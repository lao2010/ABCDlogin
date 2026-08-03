package com.loginmod.commands;

import com.loginmod.LoginMod;
import com.loginmod.data.PlayerDataManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class EmailCommand {
    private static final java.util.regex.Pattern EMAIL_PATTERN =
        java.util.regex.Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("email")
            // /email bind <邮箱>
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
                            player.sendSystemMessage(Component.literal("§a使用 /email verify 获取验证码，将验证码填写到邮件主题，发送到 v@lhjedu.dpdns.org"));
                            player.sendSystemMessage(Component.literal("§a之后使用 /login code <验证码> 进行验证码登录"));
                            return 1;
                        } else {
                            player.sendSystemMessage(Component.literal("§c绑定失败，请先注册"));
                            return 0;
                        }
                    })
                )
            )
            // /email verify - 生成验证码
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
                    player.sendSystemMessage(Component.literal("§6========== 邮箱验证码 =========="));
                    player.sendSystemMessage(Component.literal("§e您的验证码: §f§l" + code));
                    player.sendSystemMessage(Component.literal("§7请将验证码填写到邮件【主题】，发送到: §fv@lhjedu.dpdns.org"));
                    player.sendSystemMessage(Component.literal("§a发送后无需任何操作，服务器将自动检测验证码并放行登录！"));
                    player.sendSystemMessage(Component.literal("§c验证码有效期: 5分钟"));
                    player.sendSystemMessage(Component.literal("§6================================="));

                    // 启动自动检测：服务器持续查询验证码列表，查到即自动放行
                    startAutoVerify(player, username, email, code);

                    // 5分钟后自动清除验证码
                    new Thread(() -> {
                        try {
                            Thread.sleep(300000);
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
            // /email status - 查看邮箱绑定状态
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
                    } else {
                        player.sendSystemMessage(Component.literal("§c尚未绑定邮箱"));
                        player.sendSystemMessage(Component.literal("§e使用 /email bind <邮箱> 绑定"));
                    }
                    return 1;
                })
            )
        );
    }

    /** 自动检测间隔（毫秒） */
    private static final long AUTO_VERIFY_INTERVAL_MS = 5_000L;
    /** 自动检测最长等待时间（毫秒）= 验证码有效期 5 分钟 */
    private static final long AUTO_VERIFY_TIMEOUT_MS = 300_000L;

    /**
     * 邮箱验证码自动放行：
     * 玩家发送验证码到 v@lhjedu.dpdns.org 后，服务器持续查询验证码列表，
     * 当查到匹配记录（邮箱 + 验证码）时，自动完成登录，无需玩家再输入任何命令。
     * 在独立线程中执行 HTTP 请求，不阻塞服务器主线程。
     */
    private static void startAutoVerify(ServerPlayer player, String username, String email, String code) {
        Thread poller = new Thread(() -> {
            long deadline = System.currentTimeMillis() + AUTO_VERIFY_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(AUTO_VERIFY_INTERVAL_MS);
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
                        if (dm.isLoggedIn(username)) return;
                        dm.setLoggedIn(username, true);
                        dm.recordLogin(username, LoginMod.getPlayerIp(p));
                        LoginMod.finishLogin(p);
                        p.sendSystemMessage(Component.literal("§a验证码验证成功！已自动放行，欢迎回来"));
                        LoginMod.LOGGER.info("[LoginMod] 玩家 {} 验证码自动放行成功 (邮箱: {})", username, email);
                    });
                    return;
                }
            }
            // 超时（验证码过期）
            player.server.execute(() -> {
                if (!player.hasDisconnected()) {
                    player.sendSystemMessage(Component.literal("§c验证码检测超时（5分钟），验证码已过期。请重新使用 /email verify 获取新验证码"));
                }
            });
            LoginMod.LOGGER.info("[LoginMod] 玩家 {} 验证码自动检测超时", username);
        });
        poller.setDaemon(true);
        poller.setName("loginmod-auto-verify-" + username);
        poller.start();
        LoginMod.LOGGER.info("[LoginMod] 玩家 {} 已启动验证码自动检测 (邮箱: {}, 最长等待5分钟)", username, email);
    }
}
