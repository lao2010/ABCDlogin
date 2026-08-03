/*
 * Copyright (c) 2026 lao20
 * SPDX-License-Identifier: MIT
 */

package com.abcdlogin.commands;

import com.abcdlogin.ABCDlogin;
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
                            source.sendFailure(Component.literal("§c此命令只能由玩家使用"));
                            return 0;
                        }

                        String password = StringArgumentType.getString(ctx, "password");
                        String confirm = StringArgumentType.getString(ctx, "confirmPassword");
                        String username = player.getGameProfile().getName();
                        PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

                        if (dm.isRegistered(username)) {
                            player.sendSystemMessage(Component.literal("§c你已经注册过了！请使用 /login <密码> 登录"));
                            return 0;
                        }

                        if (password.length() < 4) {
                            player.sendSystemMessage(Component.literal("§c密码长度不能少于4位"));
                            return 0;
                        }

                        if (!password.equals(confirm)) {
                            player.sendSystemMessage(Component.literal("§c两次输入的密码不一致"));
                            return 0;
                        }

                        dm.register(username, password);
                        dm.setLoggedIn(username, true);
                        dm.recordLogin(username, ABCDlogin.getPlayerIp(player));
                        ABCDlogin.finishLogin(player);
                        player.sendSystemMessage(Component.literal("§a注册成功！已自动登录"));
                        player.sendSystemMessage(Component.literal("§e提示：建议使用 /email bind <邮箱> 绑定邮箱，以便使用验证码登录和找回密码"));
                        return 1;
                    })
                )
            )
        );
    }
}
