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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

public class EmailCommand {
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    /** 记录每个玩家的轮询 Future，用于取消旧轮询防止重复堆积 */
    private static final Map<String, Future<?>> POLL_FUTURES = new ConcurrentHashMap<>();

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("email")
            // ── /email bind <邮箱> 绑定邮箱 ──
            .then(Commands.literal("bind")
                .then(Commands.argument("email", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        if (!(source.getEntity() instanceof ServerPlayer player)) {
                            source.sendFailure(Component.literal(I18n.get("email.playerOnly", I18n.DEFAULT_LANG)));
                            return 0;
                        }

                        String email = StringArgumentType.getString(ctx, "email").trim();
                        String username = player.getGameProfile().getName();
                        PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

                        if (!dm.isRegistered(username)) {
                            player.sendSystemMessage(Component.literal(I18n.t(player, "email.bind.notRegistered")));
                            return 0;
                        }

                        if (!EMAIL_PATTERN.matcher(email).matches()) {
                            player.sendSystemMessage(Component.literal(I18n.t(player, "email.bind.badFormat")));
                            return 0;
                        }

                        if (dm.bindEmail(username, email)) {
                            player.sendSystemMessage(Component.literal(I18n.t(player, "email.bind.success")));
                            player.sendSystemMessage(Component.literal(I18n.t(player, "email.bind.verifyReminder")));
                            return 1;
                        } else {
                            player.sendSystemMessage(Component.literal(I18n.t(player, "email.bind.fail")));
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
                            source.sendFailure(Component.literal(I18n.get("email.playerOnly", I18n.DEFAULT_LANG)));
                            return 0;
                        }

                        String username = player.getGameProfile().getName();
                        PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

                        if (dm.unbindEmail(username)) {
                            player.sendSystemMessage(Component.literal(I18n.t(player, "email.unbind.success")));
                            player.sendSystemMessage(Component.literal(I18n.t(player, "email.unbind.rebindHint")));
                        } else {
                            player.sendSystemMessage(Component.literal(I18n.t(player, "email.unbind.fail")));
                        }
                        return 1;
                    })
                )
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        source.sendFailure(Component.literal(I18n.get("email.playerOnly", I18n.DEFAULT_LANG)));
                        return 0;
                    }
                    player.sendSystemMessage(Component.literal(I18n.t(player, "email.unbind.warn")));
                    player.sendSystemMessage(Component.literal(I18n.t(player, "email.unbind.confirmPrompt")));
                    return 1;
                })
            )
            // ── /email verify 生成验证码（验证邮箱 / 自动放行登录）──
            .then(Commands.literal("verify")
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                        source.sendFailure(Component.literal(I18n.get("email.playerOnly", I18n.DEFAULT_LANG)));
                        return 0;
                    }

                    String username = player.getGameProfile().getName();
                    PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

                    if (!dm.isRegistered(username)) {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "email.verify.notRegistered")));
                        return 0;
                    }

                    if (!dm.isEmailBound(username)) {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "email.verify.notBound")));
                        return 0;
                    }

                    // 取消该玩家之前的轮询，避免重复线程堆积
                    cancelPreviousPolls(username);

                    String code = dm.generateVerificationCode(username);
                    String email = dm.getEmail(username);
                    boolean loggedIn = dm.isLoggedIn(username);
                    int expiryMin = ModConfig.DATA.codeExpiryMs.get() / 60000;

                    player.sendSystemMessage(Component.literal(I18n.t(player, "email.verify.title")));
                    player.sendSystemMessage(Component.literal(I18n.t(player, "email.verify.code", code)));
                    player.sendSystemMessage(Component.literal(I18n.t(player, "email.verify.subject")));
                    player.sendSystemMessage(Component.literal(I18n.t(player, "email.verify.sendTo", ModConfig.recipientDisplay())));
                    if (loggedIn) {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "email.verify.loggedInNote")));
                    } else {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "email.verify.autoLoginNote")));
                    }
                    player.sendSystemMessage(Component.literal(I18n.t(player, "email.verify.expiry", expiryMin)));
                    player.sendSystemMessage(Component.literal("§6================================="));

                    // 启动自动检测：验证码到达后根据场景处理（登录放行 / 邮箱有效性验证）
                    startAutoVerify(player, username, email, code, loggedIn);

                    // 验证码过期后自动清除（使用共享线程池，避免无限制创建线程）
                    scheduleCodeClear(username, code, ModConfig.DATA.codeExpiryMs.get());

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
                                source.sendFailure(Component.literal(I18n.get("email.playerOnly", I18n.DEFAULT_LANG)));
                                return 0;
                            }

                            String newPassword = StringArgumentType.getString(ctx, "newPassword");
                            String confirm = StringArgumentType.getString(ctx, "confirmPassword");
                            String username = player.getGameProfile().getName();
                            PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

                            if (!dm.isRegistered(username)) {
                                player.sendSystemMessage(Component.literal(I18n.t(player, "email.forgot.notRegistered")));
                                return 0;
                            }

                            if (!dm.isEmailBound(username)) {
                                player.sendSystemMessage(Component.literal(I18n.t(player, "email.forgot.notBound")));
                                player.sendSystemMessage(Component.literal(I18n.t(player, "email.forgot.bindHint")));
                                return 0;
                            }

                            if (newPassword.length() < 4) {
                                player.sendSystemMessage(Component.literal(I18n.t(player, "email.forgot.tooShort")));
                                return 0;
                            }

                            if (!newPassword.equals(confirm)) {
                                player.sendSystemMessage(Component.literal(I18n.t(player, "email.forgot.mismatch")));
                                return 0;
                            }

                            String code = dm.generateVerificationCode(username);
                            String email = dm.getEmail(username);
                            int expiryMin = ModConfig.DATA.codeExpiryMs.get() / 60000;

                            player.sendSystemMessage(Component.literal(I18n.t(player, "email.forgot.title")));
                            player.sendSystemMessage(Component.literal(I18n.t(player, "email.forgot.code", code)));
                            player.sendSystemMessage(Component.literal(I18n.t(player, "email.forgot.subject")));
                            player.sendSystemMessage(Component.literal(I18n.t(player, "email.forgot.sendTo", ModConfig.recipientDisplay())));
                            player.sendSystemMessage(Component.literal(I18n.t(player, "email.forgot.willReset", newPassword)));
                            player.sendSystemMessage(Component.literal(I18n.t(player, "email.forgot.expiry", expiryMin)));
                            player.sendSystemMessage(Component.literal("§6================================="));

                            startForgetPassword(player, username, email, code, newPassword);

                            // 验证码过期后自动清除（使用共享线程池，避免无限制创建线程）
                            scheduleCodeClear(username, code, ModConfig.DATA.codeExpiryMs.get());

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
                        source.sendFailure(Component.literal(I18n.get("email.playerOnly", I18n.DEFAULT_LANG)));
                        return 0;
                    }

                    String username = player.getGameProfile().getName();
                    PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

                    if (!dm.isRegistered(username)) {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "email.status.notRegistered")));
                        return 0;
                    }

                    String email = dm.getEmail(username);
                    boolean bound = dm.isEmailBound(username);
                    if (bound) {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "email.status.bound", email)));
                        player.sendSystemMessage(Component.literal(I18n.t(player, "email.status.verifyHint")));
                        player.sendSystemMessage(Component.literal(I18n.t(player, "email.status.unbindHint")));
                    } else {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "email.status.notBound")));
                        player.sendSystemMessage(Component.literal(I18n.t(player, "email.status.bindHint")));
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
     * 取消玩家之前的轮询任务（避免同一玩家多次 verify 堆积轮询线程）。
     */
    private static void cancelPreviousPolls(String username) {
        String key = username.toLowerCase();
        Future<?> old = POLL_FUTURES.remove(key);
        if (old != null && !old.isDone()) {
            old.cancel(true);
            ABCDlogin.LOGGER.info("[ABCDlogin] 已取消玩家 {} 的旧验证码轮询", username);
        }
    }

    /**
     * 安排验证码过期清理（使用共享线程池，不再无限制创建 daemon 线程）。
     */
    private static void scheduleCodeClear(String username, String code, int expiryMs) {
        ABCDlogin.POOL.submit(() -> {
            try {
                Thread.sleep(expiryMs);
                PlayerDataManager dm = ABCDlogin.getPlayerDataManager();
                String currentCode = dm.getVerificationCode(username);
                if (code.equals(currentCode)) {
                    dm.clearVerificationCode(username);
                    ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 的验证码已过期清除", username);
                }
            } catch (InterruptedException ignored) {}
        });
    }

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

        String key = username.toLowerCase();
        Future<?> future = ABCDlogin.POOL.submit(() -> {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    return;
                }

                if (player.hasDisconnected()) return;

                boolean ok = EmailClient.checkVerificationCode(email, code);
                if (ok) {
                    // 验证成功，清除验证码防止重用
                    PlayerDataManager dm = ABCDlogin.getPlayerDataManager();
                    dm.clearVerificationCode(username);

                    final ServerPlayer p = player;
                    p.server.execute(() -> {
                        if (p.hasDisconnected()) return;

                        if (loggedInAtStart) {
                            // 已登录玩家：仅验证邮箱有效性
                            p.sendSystemMessage(Component.literal(I18n.t(p, "email.verify.success")));
                            ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 邮箱有效性验证通过 (邮箱: {})", username, email);
                        } else {
                            // 未登录玩家：自动放行
                            if (dm.isLoggedIn(username)) return;
                            dm.setLoggedIn(username, true);
                            dm.recordLogin(username, ABCDlogin.getPlayerIp(p));
                            ABCDlogin.finishLogin(p);
                            p.sendSystemMessage(Component.literal(I18n.t(p, "email.verify.autoLoginSuccess")));
                            ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 验证码自动放行成功 (邮箱: {})", username, email);
                        }
                    });
                    POLL_FUTURES.remove(key);
                    return;
                }
            }
            // 超时
            player.server.execute(() -> {
                if (!player.hasDisconnected()) {
                    if (loggedInAtStart) {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "email.verify.timeoutLoggedIn")));
                    } else {
                        player.sendSystemMessage(Component.literal(I18n.t(player, "email.verify.timeout")));
                    }
                }
            });
            POLL_FUTURES.remove(key);
            ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 验证码自动检测超时", username);
        });
        POLL_FUTURES.put(key, future);
        ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 已启动验证码自动检测 (邮箱: {}, 最长等待{}秒)",
            username, email, timeoutMs / 1000);
    }

    /**
     * 忘记密码：邮箱验证通过后自动重置密码。
     * 未登录玩家验证通过后同时自动放行。
     */
    private static void startForgetPassword(ServerPlayer player, String username, String email, String code, String newPassword) {
        int intervalMs = ModConfig.DATA.pollIntervalMs.get();
        int timeoutMs = ModConfig.DATA.pollTimeoutMs.get();

        ABCDlogin.POOL.submit(() -> {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    return;
                }

                if (player.hasDisconnected()) return;

                boolean ok = EmailClient.checkVerificationCode(email, code);
                if (ok) {
                    // 验证成功，清除验证码防止重用
                    ABCDlogin.getPlayerDataManager().clearVerificationCode(username);

                    final ServerPlayer p = player;
                    p.server.execute(() -> {
                        if (p.hasDisconnected()) return;
                        PlayerDataManager dm = ABCDlogin.getPlayerDataManager();

                        if (!dm.changePassword(username, newPassword)) {
                            p.sendSystemMessage(Component.literal(I18n.t(p, "email.forgot.fail")));
                            return;
                        }
                        p.sendSystemMessage(Component.literal(I18n.t(p, "email.forgot.success")));

                        if (!dm.isLoggedIn(username)) {
                            // 未登录：自动放行
                            dm.setLoggedIn(username, true);
                            dm.recordLogin(username, ABCDlogin.getPlayerIp(p));
                            ABCDlogin.finishLogin(p);
                            p.sendSystemMessage(Component.literal(I18n.t(p, "email.forgot.autoLogin")));
                        } else {
                            p.sendSystemMessage(Component.literal(I18n.t(p, "email.forgot.nextTime")));
                        }
                        ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 忘记密码流程完成，密码已重置", username);
                    });
                    return;
                }
            }
            // 超时
            player.server.execute(() -> {
                if (!player.hasDisconnected()) {
                    player.sendSystemMessage(Component.literal(I18n.t(player, "email.forgot.timeout")));
                }
            });
            ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 忘记密码流程超时", username);
        });
        ABCDlogin.LOGGER.info("[ABCDlogin] 玩家 {} 已启动忘记密码验证 (邮箱: {}, 最长等待{}秒)",
            username, email, timeoutMs / 1000);
    }
}