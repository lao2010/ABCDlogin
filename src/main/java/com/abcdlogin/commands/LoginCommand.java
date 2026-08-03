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
                        source.sendFailure(Component.literal("§c此命令只能由玩家使用"));
                        return 0;
                    }

                    String password = StringArgumentType.getString(ctx, "password");
                    String username = player.getGameProfile().getName();
                    PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

                    if (dm.isLoggedIn(username)) {
                        player.sendSystemMessage(Component.literal("§a你已经登录了"));
                        return 1;
                    }

                    if (!dm.isRegistered(username)) {
                        player.sendSystemMessage(Component.literal("§c你还没有注册！请使用 /register <密码> <确认密码> 注册"));
                        return 0;
                    }

                    if (dm.login(username, password)) {
                        dm.recordLogin(username, ABCDlogin.getPlayerIp(player));
                        ABCDlogin.finishLogin(player);
                        player.sendSystemMessage(Component.literal("§a登录成功！欢迎回来"));
                        if (!dm.isEmailBound(username)) {
                            player.sendSystemMessage(Component.literal("§e提示：建议绑定邮箱以增强账户安全，使用 /email bind <邮箱>"));
                        }
                        return 1;
                    } else {
                        player.sendSystemMessage(Component.literal("§c密码错误！"));
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
                            source.sendFailure(Component.literal("§c此命令只能由玩家使用"));
                            return 0;
                        }

                        String code = StringArgumentType.getString(ctx, "code");
                        String username = player.getGameProfile().getName();
                        PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

                        if (dm.isLoggedIn(username)) {
                            player.sendSystemMessage(Component.literal("§a你已经登录了"));
                            return 1;
                        }

                        if (!dm.isRegistered(username)) {
                            player.sendSystemMessage(Component.literal("§c你还没有注册！请使用 /register <密码> <确认密码> 注册"));
                            return 0;
                        }

                        if (!dm.isEmailBound(username)) {
                            player.sendSystemMessage(Component.literal("§c你没有绑定邮箱，无法使用验证码登录！请先使用 /email bind <邮箱>"));
                            return 0;
                        }

                        String email = dm.getEmail(username);
                        if (email == null || email.isEmpty()) {
                            player.sendSystemMessage(Component.literal("§c邮箱未绑定"));
                            return 0;
                        }

                        // 立即查询一次
                        player.sendSystemMessage(Component.literal("§7正在验证验证码，请稍候..."));
                        boolean verified = com.abcdlogin.network.EmailClient.checkVerificationCode(email, code);

                        if (verified) {
                            dm.setLoggedIn(username, true);
                            dm.recordLogin(username, ABCDlogin.getPlayerIp(player));
                            ABCDlogin.finishLogin(player);
                            player.sendSystemMessage(Component.literal("§a验证码验证成功！已登录"));
                            return 1;
                        }

                        // 未找到：邮件可能还在传输中，持续检测验证码列表
                        // (邮件服务器列表会自动删除过期内容，我们只需持续查询)
                        player.sendSystemMessage(Component.literal("§7验证码尚未到达，开始持续检测（最长60秒，每5秒查询一次）..."));
                        player.sendSystemMessage(Component.literal("§a提示：如果已通过 /email verify 获取验证码，发送邮件后服务器会自动放行，无需再输入此命令"));
                        startCodePolling(player, username, email, code);
                        return 1;
                    })
                )
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

                boolean ok = com.abcdlogin.network.EmailClient.checkVerificationCode(email, code);
                if (ok) {
                    final ServerPlayer p = player;
                    p.server.execute(() -> {
                        if (p.hasDisconnected()) return;
                        PlayerDataManager dm = ABCDlogin.getPlayerDataManager();
                        if (dm.isLoggedIn(username)) return;
                        dm.setLoggedIn(username, true);
                        dm.recordLogin(username, ABCDlogin.getPlayerIp(p));
                        ABCDlogin.finishLogin(p);
                        p.sendSystemMessage(Component.literal("§a验证码验证成功！已登录"));
                    });
                    return;
                }
            }
            // 超时
            player.server.execute(() -> {
                if (!player.hasDisconnected()) {
                    player.sendSystemMessage(Component.literal("§c验证码检测超时（60秒）。请确认已将验证码填写到邮件主题并发送到 " + com.abcdlogin.config.ModConfig.recipientDisplay() + " 后重试"));
                }
            });
        });
        poller.setDaemon(true);
        poller.setName("abcdlogin-code-poll-" + username);
        poller.start();
    }
}
