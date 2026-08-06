/*
 * Copyright (c) 2026 lao20
 * SPDX-License-Identifier: MIT
 */

package com.abcdlogin.commands;

import com.abcdlogin.ABCDlogin;
import com.abcdlogin.I18n;
import com.abcdlogin.config.ModConfig;
import com.abcdlogin.data.PlayerDataManager;
import com.abcdlogin.network.EmailClient;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class LoginCommand {
    /** 验证码持续检测的最长时间（毫秒） */
    private static final long CODE_POLL_TIMEOUT_MS = 60_000L;
    /** 验证码持续检测的间隔（毫秒） */
    private static final long CODE_POLL_INTERVAL_MS = 5_000L;

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("login")
            // /login <密码> - 密码登录
            .then(Commands.argument("password", StringArgumentType.greedyString())
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        source.sendFailure(Component.literal(I18n.get("email.playerOnly", I18n.DEFAULT_LANG)));
                        return 0;
                    }

                    String password = StringArgumentType.getString(ctx, "password");
                    String username = player.getGameProfile().getName();
                    PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

                    if (dm.isLoggedIn(username)) {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "login.alreadyLoggedIn")));
                        return 1;
                    }

                    if (!dm.isRegistered(username)) {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "login.notRegistered")));
                        return 0;
                    }

                    if (dm.login(username, password)) {
                        dm.recordLogin(username, ABCDlogin.getPlayerIp(player));
                        ABCDlogin.finishLogin(player);
                        player.sendSystemMessage(Component.literal(I18n.t(player, "login.success")));
                        if (!dm.isEmailBound(username)) {
                            player.sendSystemMessage(Component.literal(I18n.t(player, "login.bindReminder")));
                        }
                        return 1;
                    } else {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "login.wrongPassword")));
                        return 0;
                    }
                })
            )
            // /login code <验证码> - 验证码登录（持续检测验证码到达）
            .then(Commands.literal("code")
                .then(Commands.argument("code", StringArgumentType.word())
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        if (!(source.getEntity() instanceof ServerPlayer player)) {
                            source.sendFailure(Component.literal(I18n.get("email.playerOnly", I18n.DEFAULT_LANG)));
                            return 0;
                        }

                        String code = StringArgumentType.getString(ctx, "code");
                        String username = player.getGameProfile().getName();
                        PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

                        if (dm.isLoggedIn(username)) {
                            player.sendSystemMessage(Component.literal(I18n.t(player, "login.alreadyLoggedIn")));
                            return 1;
                        }

                        if (!dm.isRegistered(username)) {
                            player.sendSystemMessage(Component.literal(I18n.t(player, "login.notRegistered")));
                            return 0;
                        }

                        if (!dm.isEmailBound(username)) {
                            player.sendSystemMessage(Component.literal(I18n.t(player, "login.notBound")));
                            return 0;
                        }

                        String email = dm.getEmail(username);
                        if (email == null || email.isEmpty()) {
                            player.sendSystemMessage(Component.literal(I18n.t(player, "login.notBound")));
                            return 0;
                        }

                        // 立即查询一次
                        player.sendSystemMessage(Component.literal(I18n.t(player, "login.codeChecking")));
                        boolean verified = EmailClient.checkVerificationCode(email, code);

                        if (verified) {
                            dm.setLoggedIn(username, true);
                            dm.recordLogin(username, ABCDlogin.getPlayerIp(player));
                            ABCDlogin.finishLogin(player);
                            player.sendSystemMessage(Component.literal(I18n.t(player, "login.codeSuccess")));
                            return 1;
                        }

                        // 未找到：邮件可能还在传输中，持续检测验证码列表
                        // (邮件服务器列表会自动删除过期内容，我们只需持续查询)
                        player.sendSystemMessage(Component.literal(I18n.t(player, "login.codePending")));
                        player.sendSystemMessage(Component.literal(I18n.t(player, "login.codeHint")));
                        startCodePolling(player, username, email, code);
                        return 1;
                    })
                )
            )
        );

        // /flogin <前置验证码> - 前置验证码快速登录
        dispatcher.register(Commands.literal("flogin")
            .then(Commands.argument("preCode", StringArgumentType.greedyString())
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        source.sendFailure(Component.literal(I18n.get("email.playerOnly", I18n.DEFAULT_LANG)));
                        return 0;
                    }

                    String preCode = StringArgumentType.getString(ctx, "preCode");
                    String username = player.getGameProfile().getName();
                    PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

                    if (dm.isLoggedIn(username)) {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "login.alreadyLoggedIn")));
                        return 1;
                    }

                    if (!dm.isRegistered(username)) {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "login.notRegistered")));
                        return 0;
                    }

                    // 检查前置验证码
                    boolean verified = EmailClient.checkPreVerificationCode(username, preCode);
                    
                    if (verified) {
                        dm.setLoggedIn(username, true);
                        dm.recordLogin(username, ABCDlogin.getPlayerIp(player));
                        ABCDlogin.finishLogin(player);
                        player.sendSystemMessage(Component.literal(I18n.t(player, "flogin.success")));
                        return 1;
                    } else {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "flogin.invalidCode")));
                        return 0;
                    }
                })
            )
        );
    }

    /**
     * 异步持续查询验证码列表，直到验证码出现或超时。
     * 在独立线程中执行 HTTP 请求，不阻塞服务器主线程。
     */
    private static void startCodePolling(ServerPlayer player, String username, String email, String code) {
        Thread poller = new Thread(() -> {
            long deadline = System.currentTimeMillis() + CODE_POLL_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(CODE_POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    return;
                }

                boolean ok = EmailClient.checkVerificationCode(email, code);
                if (ok) {
                    final ServerPlayer p = player;
                    p.server.execute(() -> {
                        if (p.hasDisconnected()) return;
                        PlayerDataManager dm = ABCDlogin.getPlayerDataManager();
                        if (dm.isLoggedIn(username)) return;
                        dm.setLoggedIn(username, true);
                        dm.recordLogin(username, ABCDlogin.getPlayerIp(p));
                        ABCDlogin.finishLogin(p);
                        p.sendSystemMessage(Component.literal(I18n.t(p, "login.codeSuccess")));
                    });
                    return;
                }
            }
            // 超时
            player.server.execute(() -> {
                if (!player.hasDisconnected()) {
                    player.sendSystemMessage(Component.literal(
                        I18n.t(player, "login.codeTimeout", ModConfig.recipientDisplay())));
                }
            });
        });
        poller.setDaemon(true);
        poller.setName("abcdlogin-code-poll-" + username);
        poller.start();
    }
}
