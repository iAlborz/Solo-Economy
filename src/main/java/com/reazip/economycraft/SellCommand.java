package com.reazip.economycraft;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.reazip.economycraft.sell.InstaSell;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

public final class SellCommand {
    private SellCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> registerInstaSell() {
        return literal("instasell").executes(SellCommand::instaSell);
    }

    private static int instaSell(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Only players can use this command.").withStyle(ChatFormatting.RED));
            return 0;
        }

        InstaSell.sellCarried(player);
        return 1;
    }
}
