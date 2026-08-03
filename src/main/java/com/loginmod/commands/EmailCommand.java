/*
 * Copyright (c) 2026 lao20
 * SPDX-License-Identifier: MIT
 */

package com.loginmod.commands;

import com.loginmod.LoginMod;
import com.loginmod.config.ModConfig;
import com.loginmod.data.PlayerDataManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.regex.Pattern;

public class EmailCommand {
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("email")
            // ── /email bind <邮箱> 绑定邮箱 ──
            .then(Commands.literal("bind")
                .then(Commands.argument("email", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        if (!(source.getEntity() instanceof ServerPlayer player)) {
                            source.sendFailure(Component.literal("§c此命令只能由玩家使用"));
                            return 0;
                        }

                        String email = StringArgumentType.getString(ctx, "email").trim();
                        String username = player.getGameProfile().getName();
                        PlayerDataManager dm = LoginMod.getPlayerDataManager();

                        if (!dm.isRegistered(username)) {
                            player.sendSystemMessage(Component.literal("§c请先使用 /register <密码> <确认密码> 注册"));
                            return 0;
                        }

                        if (!EMAIL_PATTERN.matcher(email).matches()) {
                            player.sendSystemMessage(Component.literal("§c邮箱格式不正确"));
                            return 0;
                        }

                        if (dm.bindEmail(username, email)) {
                            player.sendSystemMessage(Component.literal("§a邮箱绑定成功！"));
                            player.sendSystemMessage(Component.literal("§e建议使用 §f/email verify §e验证邮箱是否可用"));
                            return 1;
                        } else {
                            player.sendSystemMessage(Component.literal("§c绑定失败，请先注册"));
                            return 0;
                        }
                    })
                )
            )
            // ── /email unbind confirm 解绑邮箱 ──
            .then(Commands.literal("unbind")
                .then(Commands.literal("confirm")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        if (!(source.getEntity() instanceof ServerPlayer player)) {
                            source.sendFailure(Component.literal("§c此命令只能由玩家使用"));
                            return 0;
                        }

                        String username = player.getGameProfile().getName();
                        PlayerDataManager dm = LoginMod.getPlayerDataManager();

                        if (dm.unbindEmail(username)) {
                            player.sendSystemMessage(Component.literal("§a邮箱已解绑"));
                            player.sendSystemMessage(Component.literal("§e如需重新绑定，使用 /email bind <邮箱>"));
                        } else {
                            player.sendSystemMessage(Component.literal("§c解绑失败：你还没有绑定邮箱"));
                        }
                        return 1;
                    })
                )
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        source.sendFailure(Component.literal("§c此命令只能由玩家使用"));
                        return 0;
                    }
                    player.sendSystemMessage(Component.literal("§c解绑邮箱将无法使用验证码登录和找回密码！"));
                    player.sendSystemMessage(Component.literal("§e如确认解绑，请使用 §f/email unbind confirm"));
                    return 1;
                })
            )
            // ── /email verify 生成验证码（验证邮箱 / 自动放行登录）──
            .then(Commands.literal("verify")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        source.sendFailure(Component.literal("§c此命令只能由玩家使用"));
                        return 0;
                    }

                    String username = player.getGameProfile().getName();
                    PlayerDataManager dm = LoginMod.getPlayerDataManager();

                    if (!dm.isRegistered(username)) {
                        player.sendSystemMessage(Component.literal("§c请先注册"));
                        return 0;
                    }

                    if (!dm.isEmailBound(username)) {
                        player.sendSystemMessage(Component.literal("§c请先使用 /email bind <邮箱> 绑定邮箱"));
                        return 0;
                    }

                    String code = dm.generateVerificationCode(username);
                    String email = dm.getEmail(username);
                    boolean loggedIn = dm.isLoggedIn(username);

                    player.sendSystemMessage(Component.literal("§6========== 邮箱验证 =========="));
                    player.sendSystemMessage(Component.literal("§e您的验证码: §f§l" + code));
                    player.sendSystemMessage(Component.literal("§7请将验证码填写到邮件【主题】"));
                    player.sendSystemMessage(Component.literal("§7发送到: §f" + ModConfig.recipientDisplay()));
                    if (loggedIn) {
                        player.sendSystemMessage(Component.literal("§a发送后服务器将自动验证邮箱绑定是否有效"));
                    } else {
                        player.sendSystemMessage(Component.literal("§a发送后无需任何操作，服务器将自动检测验证码并放行登录！"));
                    }
                    player.sendSystemMessage(Component.literal("§c验证码有效期: " + (ModConfig.DATA.codeExpiryMs.get() / 60000) + " 分钟"));
                    player.sendSystemMessage(Component.literal("§6================================="));

                    // 启动自动检测：验证码到达后根据场景处理（登录放行 / 邮箱有效性验证）
                    startAutoVerify(player, username, email, code, loggedIn);

                    // 验证码过期后自动清除
                    int expiryMs = ModConfig.DATA.codeExpiryMs.get();
                    new Thread(() -> {
                        try {
                            Thread.sleep(expiryMs);
                            String currentCode = dm.getVerificationCode(username);
                            if (code.equals(currentCode)) {
                                dm.clearVerificationCode(username);
                                LoginMod.LOGGER.info("[LoginMod] 玩家 {} 的验证码已过期清除", username);
                            }
                        } catch (InterruptedException ignored) {}
                    }).start();

                    return 1;
                })
            )
            // ── /email forgot <新密码> <确认密码> 忘记密码（邮箱验证后重置）──
            .then(Commands.literal("forgot")
                .then(Commands.argument("newPassword", StringArgumentType.word())
                    .then(Commands.argument("confirmPassword", StringArgumentType.word())
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayer player)) {
                                source.sendFailure(Component.literal("§c此命令只能由玩家使用"));
                                return 0;
                            }

                            String newPassword = StringArgumentType.getString(ctx, "newPassword");
                            String confirm = StringArgumentType.getString(ctx, "confirmPassword");
                            String username = player.getGameProfile().getName();
                            PlayerDataManager dm = LoginMod.getPlayerDataManager();

                            if (!dm.isRegistered(username)) {
                                player.sendSystemMessage(Component.literal("§c你还没有注册"));
                                return 0;
                            }

                            if (!dm.isEmailBound(username)) {
                                player.sendSystemMessage(Component.literal("§c你没有绑定邮箱，无法使用忘记密码功能！"));
                                player.sendSystemMessage(Component.literal("§e请先使用 /email bind <邮箱> 绑定邮箱"));
                                return 0;
                            }

                            if (newPassword.length() < 4) {
                                player.sendSystemMessage(Component.literal("§c密码长度不能少于4位"));
                                return 0;
                            }

                            if (!newPassword.equals(confirm)) {
                                player.sendSystemMessage(Component.literal("§c两次输入的密码不一致"));
                                return 0;
                            }

                            String code = dm.generateVerificationCode(username);
                            String email = dm.getEmail(username);

                            player.sendSystemMessage(Component.literal("§6========== 忘记密码 =========="));
                            player.sendSystemMessage(Component.literal("§e您的验证码: §f§l" + code));
                            player.sendSystemMessage(Component.literal("§7请将验证码填写到邮件【主题】"));
                            player.sendSystemMessage(Component.literal("§7发送到: §f" + ModConfig.recipientDisplay()));
                            player.sendSystemMessage(Component.literal("§a验证通过后将自动重置密码为: §f" + newPassword));
                            player.sendSystemMessage(Component.literal("§c验证码有效期: " + (ModConfig.DATA.codeExpiryMs.get() / 60000) + " 分钟"));
                            player.sendSystemMessage(Component.literal("§6================================="));

                            startForgetPassword(player, username, email, code, newPassword);

                            // 验证码过期后自动清除
                            int expiryMs = ModConfig.DATA.codeExpiryMs.get();
                            new Thread(() -> {
                                try {
                                    Thread.sleep(expiryMs);
                                    String currentCode = dm.getVerificationCode(username);
                                    if (code.equals(currentCode)) {
                                        dm.clearVerificationCode(username);
                                        LoginMod.LOGGER.info("[LoginMod] 玩家 {} 的验证码已过期清除", username);
                                    }
                                } catch (InterruptedException ignored) {}
                            }).start();

                            return 1;
                        })
                    )
                )
            )
            // ── /email status 查看邮箱状态 ──
            .then(Commands.literal("status")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        source.sendFailure(Component.literal("§c此命令只能由玩家使用"));
                        return 0;
                    }

                    String username = player.getGameProfile().getName();
                    PlayerDataManager dm = LoginMod.getPlayerDataManager();

                    if (!dm.isRegistered(username)) {
                        player.sendSystemMessage(Component.literal("§c你还没有注册"));
                        return 0;
                    }

                    String email = dm.getEmail(username);
                    boolean bound = dm.isEmailBound(username);
                    if (bound) {
                        player.sendSystemMessage(Component.literal("§a邮箱: " + email + " (已绑定)"));
                        player.sendSystemMessage(Component.literal("§e使用 /email verify 可验证邮箱是否有效"));
                        player.sendSystemMessage(Component.literal("§e使用 /email unbind confirm 可解绑"));
                    } else {
                        player.sendSystemMessage(Component.literal("§c尚未绑定邮箱"));
                        player.sendSystemMessage(Component.literal("§e使用 /email bind <邮箱> 绑定"));
                    }
                    return 1;
                })
            )
        );
    }

    // ═══════════════════════════════════════════════════════
    //  自动检测线程
    // ═══════════════════════════════════════════════════════

    /**
     * 邮箱验证自动放行 / 邮箱有效性验证：
     * 玩家发送验证码后，服务器持续查询验证码列表，
     * 查到匹配记录（邮箱 + 验证码）后：
     *   - 未登录玩家：自动完成登录（放行）
     *   - 已登录玩家：提示邮箱验证成功（验证绑定是否有效）
     */
    private static void startAutoVerify(ServerPlayer player, String username, String email, String code, boolean loggedInAtStart) {
        int intervalMs = ModConfig.DATA.pollIntervalMs.get();
        int timeoutMs = ModConfig.DATA.pollTimeoutMs.get();

        Thread poller = new Thread(() -> {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    return;
                }

                if (player.hasDisconnected()) return;

                boolean ok = com.loginmod.network.EmailClient.checkVerificationCode(email, code);
                if (ok) {
                    final ServerPlayer p = player;
                    p.server.execute(() -> {
                        if (p.hasDisconnected()) return;
                        PlayerDataManager dm = LoginMod.getPlayerDataManager();

                        if (loggedInAtStart) {
                            // 已登录玩家：仅验证邮箱有效性
                            p.sendSystemMessage(Component.literal("§a邮箱验证成功！邮箱绑定有效"));
                            LoginMod.LOGGER.info("[LoginMod] 玩家 {} 邮箱有效性验证通过 (邮箱: {})", username, email);
                        } else {
                            // 未登录玩家：自动放行
                            if (dm.isLoggedIn(username)) return;
                            dm.setLoggedIn(username, true);
                            dm.recordLogin(username, LoginMod.getPlayerIp(p));
                            LoginMod.finishLogin(p);
                            p.sendSystemMessage(Component.literal("§a验证码验证成功！已自动放行，欢迎回来"));
                            LoginMod.LOGGER.info("[LoginMod] 玩家 {} 验证码自动放行成功 (邮箱: {})", username, email);
                        }
                    });
                    return;
                }
            }
            // 超时
            player.server.execute(() -> {
                if (!player.hasDisconnected()) {
                    if (loggedInAtStart) {
                        player.sendSystemMessage(Component.literal("§c邮箱验证超时，未检测到验证码。请确认邮件已发送后重试"));
                    } else {
                        player.sendSystemMessage(Component.literal("§c验证码检测超时，验证码已过期。请重新使用 /email verify 获取新验证码"));
                    }
                }
            });
            LoginMod.LOGGER.info("[LoginMod] 玩家 {} 验证码自动检测超时", username);
        });
        poller.setDaemon(true);
        poller.setName("loginmod-auto-verify-" + username);
        poller.start();
        LoginMod.LOGGER.info("[LoginMod] 玩家 {} 已启动验证码自动检测 (邮箱: {}, 最长等待{}秒)",
            username, email, timeoutMs / 1000);
    }

    /**
     * 忘记密码：邮箱验证通过后自动重置密码。
     * 未登录玩家验证通过后同时自动放行。
     */
    private static void startForgetPassword(ServerPlayer player, String username, String email, String code, String newPassword) {
        int intervalMs = ModConfig.DATA.pollIntervalMs.get();
        int timeoutMs = ModConfig.DATA.pollTimeoutMs.get();

        Thread poller = new Thread(() -> {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    return;
                }

                if (player.hasDisconnected()) return;

                boolean ok = com.loginmod.network.EmailClient.checkVerificationCode(email, code);
                if (ok) {
                    final ServerPlayer p = player;
                    p.server.execute(() -> {
                        if (p.hasDisconnected()) return;
                        PlayerDataManager dm = LoginMod.getPlayerDataManager();

                        if (!dm.changePassword(username, newPassword)) {
                            p.sendSystemMessage(Component.literal("§c密码重置失败，请重试"));
                            return;
                        }
                        p.sendSystemMessage(Component.literal("§a邮箱验证通过！密码已重置"));

                        if (!dm.isLoggedIn(username)) {
                            // 未登录：自动放行
                            dm.setLoggedIn(username, true);
                            dm.recordLogin(username, LoginMod.getPlayerIp(p));
                            LoginMod.finishLogin(p);
                            p.sendSystemMessage(Component.literal("§a已自动登录，欢迎回来"));
                        } else {
                            p.sendSystemMessage(Component.literal("§e下次登录请使用新密码"));
                        }
                        LoginMod.LOGGER.info("[LoginMod] 玩家 {} 忘记密码流程完成，密码已重置", username);
                    });
                    return;
                }
            }
            // 超时
            player.server.execute(() -> {
                if (!player.hasDisconnected()) {
                    player.sendSystemMessage(Component.literal("§c验证码检测超时，密码未修改。请重新使用 /email forgot <新密码> <确认密码>"));
                }
            });
            LoginMod.LOGGER.info("[LoginMod] 玩家 {} 忘记密码流程超时", username);
        });
        poller.setDaemon(true);
        poller.setName("loginmod-forgot-" + username);
        poller.start();
        LoginMod.LOGGER.info("[LoginMod] 玩家 {} 已启动忘记密码验证 (邮箱: {}, 最长等待{}秒)",
            username, email, timeoutMs / 1000);
    }
}
