package com.reazip.economycraft;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import com.reazip.economycraft.admin.AdminUi;
import com.reazip.economycraft.shop.ShopUi;
import com.reazip.economycraft.util.AsyncFileWriter;
import com.reazip.economycraft.util.EconomyPaths;
import com.reazip.economycraft.util.IdentityCompat;
import com.reazip.economycraft.util.LiveSearchable;
import com.reazip.economycraft.util.PermissionCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class EconomyCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Commands.CommandSelection selection) {
        dispatcher.register(buildRoot(
                selection,
                buildAddMoney(),
                buildSetMoney(),
                buildRemoveMoney(),
                buildRemovePlayer()
        ));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRoot(
            Commands.CommandSelection selection,
            LiteralArgumentBuilder<CommandSourceStack> addMoney,
            LiteralArgumentBuilder<CommandSourceStack> setMoney,
            LiteralArgumentBuilder<CommandSourceStack> removeMoney,
            LiteralArgumentBuilder<CommandSourceStack> removePlayer
    ) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("eco");

        root.then(literal("admin").requires(PermissionCompat.gamemaster())
                .executes(ctx -> openAdmin(ctx.getSource())));
        root.then(SellCommand.registerInstaSell().requires(s -> EconomyConfig.get().sellEnabled));
        root.then(literal("search")
                .executes(ctx -> applyLiveSearch(ctx.getSource(), ""))
                .then(argument("query", StringArgumentType.greedyString())
                        .executes(ctx -> applyLiveSearch(ctx.getSource(), StringArgumentType.getString(ctx, "query")))));
        root.then(literal("buy")
                .requires(s -> EconomyConfig.get().shopEnabled)
                .then(argument("item", StringArgumentType.greedyString())
                        .executes(ctx -> buyItem(ctx.getSource(), StringArgumentType.getString(ctx, "item")))));

        root.then(addMoney);
        root.then(setMoney);
        root.then(removeMoney);
        root.then(removePlayer);

        if (selection != Commands.CommandSelection.DEDICATED) {
            root.then(literal("import")
                    .requires(s -> EconomyCraft.canImportSharedFolder())
                    .executes(ctx -> importSharedFolder(ctx.getSource())));
        }

        return root;
    }

    private static int importSharedFolder(CommandSourceStack source) {
        MinecraftServer server = source.getServer();

        if (!EconomyPaths.hasSharedFolder(server)) {
            source.sendFailure(Component.literal("There is nothing left to import.").withStyle(ChatFormatting.RED));
            return 0;
        }

        AsyncFileWriter.flush();

        if (!EconomyPaths.importSharedFolder(server)) {
            source.sendFailure(Component.literal("Import failed. config/economycraft was left in place, so you can try again. Check the log for the reason.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyCraft.reloadFromDisk(server);
        resyncCommands(server);

        source.sendSuccess(() -> Component.literal("Imported the old settings, prices and economy into this world. The old folder is now config/economycraft_imported.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    public static void resyncCommands(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(player);
        }
    }

    private static int applyLiveSearch(CommandSourceStack source, String query) {
        ServerPlayer player = tryGetPlayer(source);
        if (player == null) return 0;
        return LiveSearchable.apply(player, query) ? 1 : 0;
    }

    private static int openAdmin(CommandSourceStack source) {
        ServerPlayer player = tryGetPlayer(source);
        if (player == null) {
            source.sendFailure(Component.literal("Only players can open the admin menu.").withStyle(ChatFormatting.RED));
            return 0;
        }
        try {
            AdminUi.open(player, EconomyCraft.getManager(source.getServer()));
            return 1;
        } catch (Exception e) {
            LOGGER.error("[EconomyCraft] Failed to open the admin menu for {}", player.getDisplayName().getString(), e);
            source.sendFailure(Component.literal("Failed to open the admin menu. Check server logs."));
            return 0;
        }
    }

    private static @Nullable Long parseAmount(CommandSourceStack source, String raw, long min, long max) {
        Long amount = EconomyCraft.parseMoneyShort(raw);
        if (amount == null) {
            source.sendFailure(Component.literal("Invalid amount: " + raw + " (try 1000, 1.5k, 20k, 234M, ...)")
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        if (amount < min || amount > max) {
            source.sendFailure(Component.literal("Amount must be between " + min + " and " + max)
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        return amount;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAddMoney() {
        return literal("addmoney").requires(PermissionCompat.gamemaster())
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .then(argument("amount", StringArgumentType.word())
                                .executes(ctx -> {
                                    Long amount = parseAmount(ctx.getSource(), StringArgumentType.getString(ctx, "amount"), 1, EconomyManager.MAX);
                                    if (amount == null) return 0;
                                    return addMoney(
                                            IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                            amount,
                                            ctx.getSource());
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildSetMoney() {
        return literal("setmoney").requires(PermissionCompat.gamemaster())
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .then(argument("amount", StringArgumentType.word())
                                .executes(ctx -> {
                                    Long amount = parseAmount(ctx.getSource(), StringArgumentType.getString(ctx, "amount"), 0, EconomyManager.MAX);
                                    if (amount == null) return 0;
                                    return setMoney(
                                            IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                            amount,
                                            ctx.getSource());
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRemoveMoney() {
        return literal("removemoney").requires(PermissionCompat.gamemaster())
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .executes(ctx -> removeMoney(
                                IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                null,
                                ctx.getSource()))
                        .then(argument("amount", StringArgumentType.word())
                                .executes(ctx -> {
                                    Long amount = parseAmount(ctx.getSource(), StringArgumentType.getString(ctx, "amount"), 1, EconomyManager.MAX);
                                    if (amount == null) return 0;
                                    return removeMoney(
                                            IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                            amount,
                                            ctx.getSource());
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRemovePlayer() {
        return literal("removeplayer").requires(PermissionCompat.gamemaster())
                .then(argument("targets", GameProfileArgument.gameProfile())
                        .executes(ctx -> removePlayers(
                                IdentityCompat.getArgAsPlayerRefs(ctx, "targets"),
                                ctx.getSource())));
    }

    private static int addMoney(Collection<IdentityCompat.PlayerRef> profiles, long amount, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        ServerPlayer executor = tryGetPlayer(source);

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            var result = manager.addMoney(p.id(), amount, EconomySources.ADMIN_ADD);
            if (!result.successful()) {
                source.sendFailure(Component.literal("Could not add money: maximum balance exceeded")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            Component msg = Component.literal(
                            "Added " + EconomyCraft.formatMoney(amount) + " to " + p.name() + "'s balance.")
                    .withStyle(ChatFormatting.GREEN);

            reply(source, executor, msg, true);

            return 1;
        }

        int count = 0;
        for (var p : profiles) {
            if (manager.addMoney(p.id(), amount, EconomySources.ADMIN_ADD).successful()) count++;
        }

        if (count == 0) {
            source.sendFailure(Component.literal("Could not add money: all target balances would exceed the maximum")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        Component msg = Component.literal(
                        "Added " + EconomyCraft.formatMoney(amount) + " to " + count + " player" + (count > 1 ? "s" : ""))
                .withStyle(ChatFormatting.GREEN);

        reply(source, executor, msg, true);

        return count;
    }

    private static int setMoney(Collection<IdentityCompat.PlayerRef> profiles, long amount, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        ServerPlayer executor = tryGetPlayer(source);

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            manager.setMoney(p.id(), amount, EconomySources.ADMIN_SET);

            Component msg = Component.literal(
                            "Set balance of " + p.name() + " to " + EconomyCraft.formatMoney(amount))
                    .withStyle(ChatFormatting.GREEN);

            reply(source, executor, msg, true);

            return 1;
        }

        for (var p : profiles) {
            manager.setMoney(p.id(), amount, EconomySources.ADMIN_SET);
        }

        int count = profiles.size();

        Component msg = Component.literal(
                        "Set balance to " + EconomyCraft.formatMoney(amount) + " for " + count + " player" + (count > 1 ? "s" : ""))
                .withStyle(ChatFormatting.GREEN);

        reply(source, executor, msg, true);

        return count;
    }

    private static int removeMoney(Collection<IdentityCompat.PlayerRef> profiles, Long amount, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        ServerPlayer executor = tryGetPlayer(source);

        int success = 0;

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            UUID id = p.id();

            if (amount == null) {
                if (!manager.getBalances().containsKey(id)) {
                    source.sendFailure(Component.literal(
                                    "Failed to remove all money from " + p.name() + "'s balance. Unknown player.")
                            .withStyle(ChatFormatting.RED));
                    return 1;
                }
                manager.setMoney(id, 0L, EconomySources.ADMIN_REMOVE);
                Component msg = Component.literal(
                                "Removed all money from " + p.name() + "'s balance.")
                        .withStyle(ChatFormatting.GREEN);
                reply(source, executor, msg, true);
                return 1;
            }

            if (!manager.removeMoney(id, amount, EconomySources.ADMIN_REMOVE).successful()) {
                source.sendFailure(Component.literal(
                                "Failed to remove " + EconomyCraft.formatMoney(amount) + " from " + p.name() + "'s balance due to insufficient funds.")
                        .withStyle(ChatFormatting.RED));
                return 1;
            }

            Component msg = Component.literal(
                            "Successfully removed " + EconomyCraft.formatMoney(amount) + " from " + p.name() + "'s balance.")
                    .withStyle(ChatFormatting.GREEN);
            reply(source, executor, msg, true);
            return 1;
        }

        for (var p : profiles) {
            UUID id = p.id();
            if (amount == null) {
                if (!manager.getBalances().containsKey(id)) {
                    source.sendFailure(Component.literal(
                                    "Failed to remove all money from " + p.name() + "'s balance. Unknown player.")
                            .withStyle(ChatFormatting.RED));
                    continue;
                }
                manager.setMoney(id, 0L, EconomySources.ADMIN_REMOVE);
                success++;
            } else {
                if (manager.removeMoney(id, amount, EconomySources.ADMIN_REMOVE).successful()) {
                    success++;
                } else {
                    source.sendFailure(Component.literal(
                                    "Failed to remove " + EconomyCraft.formatMoney(amount) + " from " + p.name() + "'s balance due to insufficient funds.")
                            .withStyle(ChatFormatting.RED));
                }
            }
        }

        if (success > 0) {
            Component msg;
            if (amount == null) {
                msg = Component.literal(
                                "Removed all money from " + success + " player" + (success > 1 ? "s" : "") + ".")
                        .withStyle(ChatFormatting.GREEN);
            } else {
                msg = Component.literal(
                                "Successfully removed " + EconomyCraft.formatMoney(amount) + " from " + success + " player" + (success > 1 ? "s" : "") + ".")
                        .withStyle(ChatFormatting.GREEN);
            }
            reply(source, executor, msg, true);
        }

        return profiles.size();
    }

    private static int removePlayers(Collection<IdentityCompat.PlayerRef> profiles, CommandSourceStack source) {
        if (profiles.isEmpty()) {
            source.sendFailure(Component.literal("No targets matched").withStyle(ChatFormatting.RED));
            return 0;
        }

        EconomyManager manager = EconomyCraft.getManager(source.getServer());
        ServerPlayer executor = tryGetPlayer(source);

        if (profiles.size() == 1) {
            var p = profiles.iterator().next();
            manager.removePlayer(p.id());

            Component msg = Component.literal("Removed " + p.name() + " from economy")
                    .withStyle(ChatFormatting.GREEN);

            reply(source, executor, msg, true);

            return 1;
        }

        for (var p : profiles) {
            manager.removePlayer(p.id());
        }

        int count = profiles.size();

        Component msg = Component.literal(
                        "Removed " + count + " player" + (count > 1 ? "s" : "") + " from economy")
                .withStyle(ChatFormatting.GREEN);

        reply(source, executor, msg, true);

        return count;
    }

    private static int buyItem(CommandSourceStack source, String raw) {
        ServerPlayer player = tryGetPlayer(source);
        if (player == null) {
            source.sendFailure(Component.literal("Only players can buy.").withStyle(ChatFormatting.RED));
            return 0;
        }
        String id = raw == null ? "" : raw.trim();
        int count = 1;
        int space = id.lastIndexOf(' ');
        if (space > 0) {
            try {
                count = Integer.parseInt(id.substring(space + 1).trim());
                id = id.substring(0, space).trim();
            } catch (NumberFormatException ignored) {
            }
        }
        if (id.isEmpty() || count < 1) {
            source.sendFailure(Component.literal("Usage: /eco buy <item> [count]").withStyle(ChatFormatting.RED));
            return 0;
        }
        return ShopUi.buy(player, id, count);
    }

    @Nullable
    private static ServerPlayer tryGetPlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception e) {
            return null;
        }
    }

    private static void reply(CommandSourceStack source, @Nullable ServerPlayer executor, Component msg, boolean broadcastToOps) {
        if (executor != null) {
            executor.sendSystemMessage(msg);
        } else {
            source.sendSuccess(() -> msg, broadcastToOps);
        }
    }
}
