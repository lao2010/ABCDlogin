/*
 * Copyright (c) 2026 lao20
 * SPDX-License-Identifier: MIT
 */

package com.abcdlogin.commands;

import com.abcdlogin.ABCDlogin;
import com.abcdlogin.I18n;
import com.abcdlogin.data.PlayerDataManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class RegisterCommand {
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("register")
            .then(Commands.argument("password", StringArgumentType.word())
                .then(Commands.argument("confirmPassword", StringArgumentType.word())
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        if (!(source.getEntity() instanceof ServerPlayer player)) {
                            source.sendFailure(Component.literal(I18n.get("email.playerOnly", I18n.DEFAULT_LANG)));
                            return 0;
                        }

                        String password = StringArgumentType.getString(ctx, "password");
                        String confirm = StringArgumentType.getString(ctx, "confirmPassword");
                        String username = player.getGameProfile().getName();
                        PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

                        if (dm.isRegistered(username)) {
                            player.sendSystemMessage(Component.literal(I18n.t(player, "register.alreadyRegistered")));
                            return 0;
                        }

                        if (password.length() < 4) {
                            player.sendSystemMessage(Component.literal(I18n.t(player, "register.passwordTooShort")));
                            return 0;
                        }

                        if (!password.equals(confirm)) {
                            player.sendSystemMessage(Component.literal(I18n.t(player, "register.passwordMismatch")));
                            return 0;
                        }

                        dm.register(username, password);
                        dm.setLoggedIn(username, true);
                        dm.recordLogin(username, ABCDlogin.getPlayerIp(player));
                        ABCDlogin.finishLogin(player);
                        player.sendSystemMessage(Component.literal(I18n.t(player, "register.success")));
                        player.sendSystemMessage(Component.literal(I18n.t(player, "register.bindReminder")));
                        return 1;
                    })
                )
            )
        );
    }
}
