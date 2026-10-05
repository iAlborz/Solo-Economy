package com.reazip.economycraft.bank;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.util.ChatCompat;
import com.reazip.economycraft.util.ConfirmUi;
import com.reazip.economycraft.util.EconomySounds;
import com.reazip.economycraft.util.IdentityCompat;
import com.reazip.economycraft.util.MenuUiSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Joint accounts: the Joint button invites everyone online to share finances. Anyone who accepts has their
 * balance added to the shared one, and from then on every sale, purchase and reward changes everyone's balance.
 */
public final class JointAccounts {
    private static final long INVITE_LIFETIME_MS = 5 * 60 * 1000L;
    private static final long INVITE_COOLDOWN_MS = 10 * 1000L;

    /** invitee -> (inviter -> when the invite runs out) */
    private static final Map<UUID, Map<UUID, Long>> INVITES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SENT = new ConcurrentHashMap<>();

    private JointAccounts() {}

    /** Names of the players sharing finances with {@code id}, comma separated, or empty. */
    public static String partnerNames(EconomyManager eco, UUID id) {
        List<String> names = new ArrayList<>();
        for (UUID partner : eco.jointPartners(id)) {
            String name = eco.getBestName(partner);
            names.add(name == null || name.isBlank() ? "?" : name);
        }
        return String.join(", ", names);
    }

    /** The Joint button: leave your joint account, or invite everyone else to share finances. */
    public static void onButton(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        EconomyManager eco = EconomyCraft.getManager(server);
        if (eco.isInJointAccount(player.getUUID())) {
            confirmLeave(player, eco);
        } else {
            invite(player, server);
        }
    }

    private static void confirmLeave(ServerPlayer player, EconomyManager eco) {
        List<Component> lore = List.of(
                MenuUiSupport.hint("Shared with " + partnerNames(eco, player.getUUID()) + "."),
                MenuUiSupport.labeledValue("Shared balance", EconomyCraft.formatMoney(eco.getBalance(player.getUUID(), true)),
                        MenuUiSupport.LABEL_PRIMARY_COLOR),
                MenuUiSupport.italicHint("Leaving splits it evenly."));
        ConfirmUi.open(player, "Leave joint account?",
                MenuUiSupport.button(Items.GOLD_BLOCK, "Joint account", ChatFormatting.YELLOW), "Leave", lore,
                p -> leave(p, eco), ServerPlayer::closeContainer);
    }

    private static void leave(ServerPlayer player, EconomyManager eco) {
        player.closeContainer();
        List<UUID> partners = eco.jointPartners(player.getUUID());
        if (!eco.leaveJointAccount(player.getUUID())) return;
        EconomySounds.success(player);
        player.sendSystemMessage(Component.literal("You left the joint account. Your share: "
                + EconomyCraft.formatMoney(eco.getBalance(player.getUUID(), true))).withStyle(ChatFormatting.YELLOW));
        for (UUID partner : partners) {
            ServerPlayer online = player.level().getServer().getPlayerList().getPlayer(partner);
            if (online != null) {
                online.sendSystemMessage(Component.literal(IdentityCompat.of(player).name()
                        + " left the joint account. The shared balance was split.").withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    private static void invite(ServerPlayer inviter, MinecraftServer server) {
        long now = System.currentTimeMillis();
        Long last = LAST_SENT.get(inviter.getUUID());
        if (last != null && now - last < INVITE_COOLDOWN_MS) {
            inviter.sendSystemMessage(MenuUiSupport.line("Wait a moment before inviting again.", ChatFormatting.RED));
            return;
        }

        String name = IdentityCompat.of(inviter).name();
        int sent = 0;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other.getUUID().equals(inviter.getUUID())) continue;
            INVITES.computeIfAbsent(other.getUUID(), k -> new ConcurrentHashMap<>())
                    .put(inviter.getUUID(), now + INVITE_LIFETIME_MS);
            other.sendSystemMessage(inviteMessage(name, inviter.getUUID()));
            EconomySounds.moneyReceived(other);
            sent++;
        }
        if (sent == 0) {
            EconomySounds.failure(inviter);
            inviter.sendSystemMessage(MenuUiSupport.line("Nobody else is online to invite.", ChatFormatting.RED));
            return;
        }
        LAST_SENT.put(inviter.getUUID(), now);
        inviter.sendSystemMessage(Component.literal("Invited " + sent + " player" + (sent == 1 ? "" : "s")
                + " to share finances.").withStyle(ChatFormatting.GREEN));
    }

    private static Component inviteMessage(String inviterName, UUID inviterId) {
        Component accept = Component.literal("[Accept]").withStyle(s -> s.withBold(true).withColor(ChatFormatting.GREEN)
                .withClickEvent(ChatCompat.runCommandEvent("/eco joint accept " + inviterId)));
        Component decline = Component.literal("[Decline]").withStyle(s -> s.withBold(true).withColor(ChatFormatting.RED)
                .withClickEvent(ChatCompat.runCommandEvent("/eco joint decline " + inviterId)));
        return Component.literal(inviterName + " wants to join finances with you. Your balances will be added together "
                        + "and shared. Accept? ").withStyle(ChatFormatting.GOLD)
                .append(accept).append(Component.literal(" ")).append(decline);
    }

    /** Runs when a player clicks Accept or Decline in chat. */
    public static int respond(CommandSourceStack source, String inviterText, boolean accept) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            return 0;
        }
        UUID inviterId;
        try {
            inviterId = UUID.fromString(inviterText);
        } catch (IllegalArgumentException e) {
            return 0;
        }

        Map<UUID, Long> mine = INVITES.get(player.getUUID());
        Long expires = mine == null ? null : mine.remove(inviterId);
        if (expires == null || expires < System.currentTimeMillis()) {
            player.sendSystemMessage(MenuUiSupport.line("That invitation is no longer valid.", ChatFormatting.RED));
            return 0;
        }

        MinecraftServer server = player.level().getServer();
        ServerPlayer inviter = server.getPlayerList().getPlayer(inviterId);
        String playerName = IdentityCompat.of(player).name();
        if (!accept) {
            player.sendSystemMessage(Component.literal("Invitation declined.").withStyle(ChatFormatting.GRAY));
            if (inviter != null) {
                inviter.sendSystemMessage(Component.literal(playerName + " declined to share finances.")
                        .withStyle(ChatFormatting.GRAY));
            }
            return 1;
        }

        EconomyManager eco = EconomyCraft.getManager(server);
        switch (eco.joinJointAccount(inviterId, player.getUUID())) {
            case JOINED -> {
                String balance = EconomyCraft.formatMoney(eco.getBalance(player.getUUID(), true));
                EconomySounds.success(player);
                player.sendSystemMessage(Component.literal("You now share finances. Shared balance: " + balance)
                        .withStyle(ChatFormatting.GREEN));
                for (UUID partner : eco.jointPartners(player.getUUID())) {
                    ServerPlayer online = server.getPlayerList().getPlayer(partner);
                    if (online != null) {
                        online.sendSystemMessage(Component.literal(playerName + " joined your joint account. Shared balance: "
                                + balance).withStyle(ChatFormatting.GREEN));
                    }
                }
                return 1;
            }
            case ACCEPTER_IN_OTHER_ACCOUNT -> player.sendSystemMessage(MenuUiSupport.line(
                    "You are already in a joint account. Leave it first (click Joint).", ChatFormatting.RED));
            case TOO_MUCH_MONEY -> player.sendSystemMessage(MenuUiSupport.line(
                    "Your balances together are too large to combine.", ChatFormatting.RED));
            case ALREADY_TOGETHER -> player.sendSystemMessage(MenuUiSupport.line(
                    "You already share finances.", ChatFormatting.RED));
        }
        EconomySounds.failure(player);
        return 0;
    }
}
