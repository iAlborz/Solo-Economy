package com.reazip.economycraft.bank;

import com.reazip.economycraft.EconomyCraft;
import com.reazip.economycraft.EconomyManager;
import com.reazip.economycraft.EconomySources;
import com.reazip.economycraft.util.EconomySounds;
import com.reazip.economycraft.util.IdentityCompat;
import com.reazip.economycraft.util.MenuUiSupport;
import com.reazip.economycraft.util.NumberInputUi;
import com.reazip.economycraft.util.PlayerPickerUi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

public final class SendMoneyUi {
    private SendMoneyUi() {}

    public static void open(ServerPlayer player) {
        PlayerPickerUi.open(player, "Send money to", false, SendMoneyUi::pickAmount, ServerPlayer::closeContainer);
    }

    private static void pickAmount(ServerPlayer player, PlayerPickerUi.Target target) {
        EconomyManager eco = EconomyCraft.getManager(player.level().getServer());
        if (eco.jointPartners(player.getUUID()).contains(target.id())) {
            EconomySounds.failure(player);
            player.sendSystemMessage(MenuUiSupport.line("You already share finances with " + target.name() + ".", ChatFormatting.RED));
            player.closeContainer();
            return;
        }
        long balance = eco.getBalance(player.getUUID(), true);
        if (balance < 1) {
            EconomySounds.failure(player);
            player.sendSystemMessage(MenuUiSupport.line("You have no money to send.", ChatFormatting.RED));
            player.closeContainer();
            return;
        }
        NumberInputUi.openMoney(player, "Send to " + target.name(),
                MenuUiSupport.button(Items.GOLD_INGOT, "Send to " + target.name(), ChatFormatting.GOLD),
                "Amount", Math.min(100, balance), 1, balance,
                (p, amount) -> send(p, eco, target, amount), ServerPlayer::closeContainer);
    }

    private static void send(ServerPlayer from, EconomyManager eco, PlayerPickerUi.Target target, long amount) {
        boolean sent = eco.pay(from.getUUID(), target.id(), amount, EconomySources.PLAYER_PAYMENT).successful();
        from.closeContainer();
        if (!sent) {
            EconomySounds.failure(from);
            from.sendSystemMessage(MenuUiSupport.line("Could not send " + EconomyCraft.formatMoney(amount) + ".", ChatFormatting.RED));
            return;
        }
        EconomySounds.success(from);
        from.sendSystemMessage(Component.literal("Sent " + EconomyCraft.formatMoney(amount) + " to " + target.name())
                .withStyle(ChatFormatting.GREEN));
        ServerPlayer recipient = from.level().getServer().getPlayerList().getPlayer(target.id());
        if (recipient != null) {
            recipient.sendSystemMessage(Component.literal(IdentityCompat.of(from).name() + " sent you " + EconomyCraft.formatMoney(amount))
                    .withStyle(ChatFormatting.GREEN));
        }
    }
}
