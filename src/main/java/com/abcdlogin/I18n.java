/*
 * Copyright (c) 2026 lao20
 * SPDX-License-Identifier: MIT
 */

package com.abcdlogin;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 多语言支持（简体中文 / English）。
 * 玩家可用 /language <zh_cn|en_us> 切换，语言自动记录并持久化。
 */
public class I18n {
    public static final String ZH_CN = "zh_cn";
    public static final String EN_US = "en_us";
    public static final String DEFAULT_LANG = ZH_CN;

    private static final Map<String, Map<String, String>> STRINGS = new HashMap<>();

    static {
        STRINGS.put(ZH_CN, zhStrings());
        STRINGS.put(EN_US, enStrings());
    }

    /** 获取指定语言的文本，支持 {0} {1} 占位符 */
    public static String get(String key, String lang, Object... args) {
        Map<String, String> table = STRINGS.getOrDefault(lang, STRINGS.get(DEFAULT_LANG));
        String template = table.getOrDefault(key, STRINGS.get(DEFAULT_LANG).getOrDefault(key, key));
        for (int i = 0; i < args.length; i++) {
            template = template.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return template;
    }

    /** 获取玩家语言的文本 */
    public static String t(ServerPlayer player, String key, Object... args) {
        String lang = ABCDlogin.getPlayerDataManager().getLanguage(player.getGameProfile().getName());
        return get(key, lang, args);
    }

    /** 校验语言代码是否有效 */
    public static boolean isValid(String lang) {
        return STRINGS.containsKey(lang == null ? "" : lang.toLowerCase(Locale.ROOT));
    }

    public static String normalize(String lang) {
        return isValid(lang) ? lang.toLowerCase(Locale.ROOT) : DEFAULT_LANG;
    }

    /** 获取配置的默认语言（未设置时回退 zh_cn） */
    public static String getDefaultLang() {
        try {
            String lang = com.abcdlogin.config.ModConfig.DATA.defaultLanguage.get();
            return isValid(lang) ? lang : DEFAULT_LANG;
        } catch (Exception e) {
            return DEFAULT_LANG;
        }
    }

    private static Map<String, String> zhStrings() {
        Map<String, String> m = new HashMap<>();
        // ── 登录 / 注册 ──
        m.put("login.usage", "§e用法: §f/login <密码> §e或 §f/login code <验证码>");
        m.put("login.success", "§a登录成功！欢迎回来");
        m.put("login.wrongPassword", "§c密码错误");
        m.put("login.notRegistered", "§c账号未注册，请先使用 /register <密码> <确认密码> 注册");
        m.put("login.alreadyLoggedIn", "§a你已登录");
        m.put("login.notBound", "§c你还没有绑定邮箱，无法使用验证码登录");
        m.put("login.codeUsage", "§e用法: §f/login code <验证码>");
        m.put("login.codeChecking", "§7正在验证验证码，请稍候...");
        m.put("login.codeSuccess", "§a验证码验证成功！已登录");
        m.put("login.codePending", "§7验证码尚未到达，开始持续检测（最长60秒，每5秒查询一次）...");
        m.put("login.codeHint", "§a提示：如果已通过 /email verify 获取验证码，发送邮件后服务器会自动放行，无需再输入此命令");
        m.put("login.codeTimeout", "§c验证码检测超时（60秒）。请确认已将验证码填写到邮件主题并发送到 {0} 后重试");
        m.put("login.bindReminder", "§e建议使用 §f/email bind <邮箱> §e绑定邮箱，以便使用验证码登录和找回密码");
        m.put("register.usage", "§e用法: §f/register <密码> <确认密码>");
        m.put("register.success", "§a注册成功！已自动登录");
        m.put("register.bindReminder", "§e提示：建议使用 §f/email bind <邮箱> §e绑定邮箱，以便使用验证码登录和找回密码");
        m.put("register.alreadyRegistered", "§c账号已注册，请使用 /login <密码> 登录");
        m.put("register.passwordMismatch", "§c两次输入的密码不一致");
        m.put("register.passwordTooShort", "§c密码长度不能少于4位");

        // ── 加入服务器提示 ──
        m.put("join.title", "§6========== 登录验证 ==========");
        m.put("join.registerTitle", "§6========== 欢迎来到服务器 ==========");
        m.put("join.loginPrompt", "§e请使用 §f/login <密码> §e登录");
        m.put("join.emailOption1", "§e或使用 §f/email verify §e获取验证码");
        m.put("join.emailOption2", "§e将验证码填写到邮件主题，发送到 {0}");
        m.put("join.emailOption3", "§a服务器检测到验证码后将自动放行，无需其他操作");
        m.put("join.registerPrompt", "§e请使用 §f/register <密码> <确认密码> §e注册");
        m.put("join.registerReminder", "§e注册后建议绑定邮箱，可使用验证码登录与找回密码");
        m.put("join.warning", "§c注意：未登录状态下你位于登录等待区（旁观者视角，看不到装备与状态），无法移动、破坏方块和发言！");

        // ── 语言 ──
        m.put("language.usage", "§e用法: §f/language [zh_cn|en_us]");
        m.put("language.current", "§a当前语言: §f{0}");
        m.put("language.available", "§e可用语言: §fzh_cn §e(简体中文) §7/§f en_us §e(English)");
        m.put("language.unknown", "§c未知语言: {0}。可用: zh_cn, en_us");
        m.put("language.switched", "§a语言已切换为 §f{0}");

        // ── 未登录操作拦截 ──
        m.put("blocked.loginFirst", "§c请先登录！使用 /login <密码>");
        m.put("blocked.chat", "§c请先登录后再发言！使用 /login <密码>");
        m.put("blocked.commands", "§c未登录状态下只能使用: /login, /register, /email, /language 命令");

        // ── 邮箱 ──
        m.put("email.playerOnly", "§c此命令只能由玩家使用");
        m.put("email.bind.success", "§a邮箱绑定成功！");
        m.put("email.bind.verifyReminder", "§e建议使用 §f/email verify §e验证邮箱是否可用");
        m.put("email.bind.badFormat", "§c邮箱格式不正确");
        m.put("email.bind.notRegistered", "§c请先使用 /register <密码> <确认密码> 注册");
        m.put("email.bind.fail", "§c绑定失败，请先注册");
        m.put("email.unbind.warn", "§c解绑邮箱将无法使用验证码登录和找回密码！");
        m.put("email.unbind.confirmPrompt", "§e如确认解绑，请使用 §f/email unbind confirm");
        m.put("email.unbind.success", "§a邮箱已解绑");
        m.put("email.unbind.rebindHint", "§e如需重新绑定，使用 /email bind <邮箱>");
        m.put("email.unbind.fail", "§c解绑失败：你还没有绑定邮箱");
        m.put("email.verify.title", "§6========== 邮箱验证 ==========");
        m.put("email.verify.code", "§e您的验证码: §f§l{0}");
        m.put("email.verify.subject", "§7请将验证码填写到邮件【主题】");
        m.put("email.verify.sendTo", "§7发送到: §f{0}");
        m.put("email.verify.loggedInNote", "§a发送后服务器将自动验证邮箱绑定是否有效");
        m.put("email.verify.autoLoginNote", "§a发送后无需任何操作，服务器将自动检测验证码并放行登录！");
        m.put("email.verify.expiry", "§c验证码有效期: {0} 分钟");
        m.put("email.verify.success", "§a邮箱验证成功！邮箱绑定有效");
        m.put("email.verify.autoLoginSuccess", "§a验证码验证成功！已自动放行，欢迎回来");
        m.put("email.verify.timeoutLoggedIn", "§c邮箱验证超时，未检测到验证码。请确认邮件已发送后重试");
        m.put("email.verify.timeout", "§c验证码检测超时，验证码已过期。请重新使用 /email verify 获取新验证码");
        m.put("email.verify.notRegistered", "§c请先注册");
        m.put("email.verify.notBound", "§c请先使用 /email bind <邮箱> 绑定邮箱");
        m.put("email.forgot.title", "§6========== 忘记密码 ==========");
        m.put("email.forgot.code", "§e您的验证码: §f§l{0}");
        m.put("email.forgot.subject", "§7请将验证码填写到邮件【主题】");
        m.put("email.forgot.sendTo", "§7发送到: §f{0}");
        m.put("email.forgot.willReset", "§a验证通过后将自动重置密码为: §f{0}");
        m.put("email.forgot.expiry", "§c验证码有效期: {0} 分钟");
        m.put("email.forgot.notRegistered", "§c你还没有注册");
        m.put("email.forgot.notBound", "§c你没有绑定邮箱，无法使用忘记密码功能！");
        m.put("email.forgot.bindHint", "§e请先使用 /email bind <邮箱> 绑定邮箱");
        m.put("email.forgot.tooShort", "§c密码长度不能少于4位");
        m.put("email.forgot.mismatch", "§c两次输入的密码不一致");
        m.put("email.forgot.success", "§a邮箱验证通过！密码已重置");
        m.put("email.forgot.autoLogin", "§a已自动登录，欢迎回来");
        m.put("email.forgot.nextTime", "§e下次登录请使用新密码");
        m.put("email.forgot.fail", "§c密码重置失败，请重试");
        m.put("email.forgot.timeout", "§c验证码检测超时，密码未修改。请重新使用 /email forgot <新密码> <确认密码>");
        m.put("email.status.notRegistered", "§c你还没有注册");
        m.put("email.status.bound", "§a邮箱: {0} §a(已绑定)");
        m.put("email.status.verifyHint", "§e使用 /email verify 可验证邮箱是否有效");
        m.put("email.status.unbindHint", "§e使用 /email unbind confirm 可解绑");
        m.put("email.status.notBound", "§c尚未绑定邮箱");
        m.put("email.status.bindHint", "§e使用 /email bind <邮箱> 绑定");
        return m;
    }

    private static Map<String, String> enStrings() {
        Map<String, String> m = new HashMap<>();
        // ── Login / Register ──
        m.put("login.usage", "§eUsage: §f/login <password> §eor §f/login code <code>");
        m.put("login.success", "§aLogin successful! Welcome back");
        m.put("login.wrongPassword", "§cWrong password");
        m.put("login.notRegistered", "§cAccount not registered. Please use /register <password> <confirm> first");
        m.put("login.alreadyLoggedIn", "§aYou are already logged in");
        m.put("login.notBound", "§cYou have not bound an email, cannot use code login");
        m.put("login.codeUsage", "§eUsage: §f/login code <code>");
        m.put("login.codeChecking", "§7Verifying verification code, please wait...");
        m.put("login.codeSuccess", "§aVerification code accepted! Logged in");
        m.put("login.codePending", "§7Code not arrived yet. Polling continuously (max 60s, every 5s)...");
        m.put("login.codeHint", "§aTip: if you used /email verify, the server will auto-login you once the email arrives - no command needed");
        m.put("login.codeTimeout", "§cCode verification timed out (60s). Make sure the code is in the email subject sent to {0}, then retry");
        m.put("login.bindReminder", "§eConsider §f/email bind <email> §eto enable code login and password recovery");
        m.put("register.usage", "§eUsage: §f/register <password> <confirm>");
        m.put("register.success", "§aRegistered successfully! Auto-logged in");
        m.put("register.bindReminder", "§eTip: consider §f/email bind <email> §eto enable code login and password recovery");
        m.put("register.alreadyRegistered", "§cAccount already registered. Use /login <password> to log in");
        m.put("register.passwordMismatch", "§cPasswords do not match");
        m.put("register.passwordTooShort", "§cPassword must be at least 4 characters");

        // ── Join messages ──
        m.put("join.title", "§6========== Login Required ==========");
        m.put("join.registerTitle", "§6========== Welcome ==========");
        m.put("join.loginPrompt", "§ePlease §f/login <password> §eto log in");
        m.put("join.emailOption1", "§eOr use §f/email verify §eto get a verification code");
        m.put("join.emailOption2", "§ePut the code in the email subject, send to {0}");
        m.put("join.emailOption3", "§aThe server will auto-login you once the code is detected - nothing else needed");
        m.put("join.registerPrompt", "§ePlease §f/register <password> <confirm> §eto register");
        m.put("join.registerReminder", "§eAfter registering, consider binding an email for code login and password recovery");
        m.put("join.warning", "§cNote: while not logged in you are in the login waiting area (spectator view, no inventory or HUD). You cannot move, break blocks or chat!");

        // ── Language ──
        m.put("language.usage", "§eUsage: §f/language [zh_cn|en_us]");
        m.put("language.current", "§aCurrent language: §f{0}");
        m.put("language.available", "§eAvailable: §fzh_cn §e(简体中文) §7/§f en_us §e(English)");
        m.put("language.unknown", "§cUnknown language: {0}. Available: zh_cn, en_us");
        m.put("language.switched", "§aLanguage switched to §f{0}");

        // ── Blocked actions while not logged in ──
        m.put("blocked.loginFirst", "§cPlease log in first! Use /login <password>");
        m.put("blocked.chat", "§cPlease log in before chatting! Use /login <password>");
        m.put("blocked.commands", "§cWhile not logged in, only these commands are allowed: /login, /register, /email, /language");

        // ── Email ──
        m.put("email.playerOnly", "§cThis command can only be used by players");
        m.put("email.bind.success", "§aEmail bound successfully!");
        m.put("email.bind.verifyReminder", "§eConsider §f/email verify §eto confirm the email works");
        m.put("email.bind.badFormat", "§cInvalid email format");
        m.put("email.bind.notRegistered", "§cPlease register first: /register <password> <confirm>");
        m.put("email.bind.fail", "§cBind failed. Please register first");
        m.put("email.unbind.warn", "§cUnbinding your email will disable code login and password recovery!");
        m.put("email.unbind.confirmPrompt", "§eTo confirm, use §f/email unbind confirm");
        m.put("email.unbind.success", "§aEmail unbound");
        m.put("email.unbind.rebindHint", "§eTo rebind, use /email bind <email>");
        m.put("email.unbind.fail", "§cUnbind failed: you have no email bound");
        m.put("email.verify.title", "§6========== Email Verification ==========");
        m.put("email.verify.code", "§eYour code: §f§l{0}");
        m.put("email.verify.subject", "§7Put the code in the email §esubject");
        m.put("email.verify.sendTo", "§7Send to: §f{0}");
        m.put("email.verify.loggedInNote", "§aThe server will verify your email binding once the code arrives");
        m.put("email.verify.autoLoginNote", "§aNo further action needed - the server will auto-login you once the code is detected!");
        m.put("email.verify.expiry", "§cCode expires in {0} minute(s)");
        m.put("email.verify.success", "§aEmail verified successfully! Your binding is valid");
        m.put("email.verify.autoLoginSuccess", "§aVerification code accepted! Auto-logged in, welcome back");
        m.put("email.verify.timeoutLoggedIn", "§cEmail verification timed out. Make sure the email was sent, then retry");
        m.put("email.verify.timeout", "§cCode verification timed out and the code has expired. Use /email verify to get a new one");
        m.put("email.verify.notRegistered", "§cPlease register first");
        m.put("email.verify.notBound", "§cPlease bind an email first: /email bind <email>");
        m.put("email.forgot.title", "§6========== Forgot Password ==========");
        m.put("email.forgot.code", "§eYour code: §f§l{0}");
        m.put("email.forgot.subject", "§7Put the code in the email §esubject");
        m.put("email.forgot.sendTo", "§7Send to: §f{0}");
        m.put("email.forgot.willReset", "§aYour password will be reset to: §f{0}");
        m.put("email.forgot.expiry", "§cCode expires in {0} minute(s)");
        m.put("email.forgot.notRegistered", "§cYou are not registered");
        m.put("email.forgot.notBound", "§cYou have no email bound, cannot reset password!");
        m.put("email.forgot.bindHint", "§ePlease bind an email first: /email bind <email>");
        m.put("email.forgot.tooShort", "§cPassword must be at least 4 characters");
        m.put("email.forgot.mismatch", "§cPasswords do not match");
        m.put("email.forgot.success", "§aEmail verified! Password has been reset");
        m.put("email.forgot.autoLogin", "§aAuto-logged in, welcome back");
        m.put("email.forgot.nextTime", "§eUse your new password next time");
        m.put("email.forgot.fail", "§cPassword reset failed. Please retry");
        m.put("email.forgot.timeout", "§cCode verification timed out, password not changed. Use /email forgot <newPassword> <confirm> again");
        m.put("email.status.notRegistered", "§cYou are not registered");
        m.put("email.status.bound", "§aEmail: {0} §a(bound)");
        m.put("email.status.verifyHint", "§eUse /email verify to check the binding");
        m.put("email.status.unbindHint", "§eUse /email unbind confirm to unbind");
        m.put("email.status.notBound", "§cNo email bound");
        m.put("email.status.bindHint", "§eUse /email bind <email> to bind");
        return m;
    }
}
