/*
 * Copyright (c) 2026 lao20
 * SPDX-License-Identifier: MIT
 */

package com.abcdlogin;

import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        loadDefaultLanguages();
    }

    private static void loadDefaultLanguages() {
        STRINGS.clear();
        STRINGS.put("zh_cn", zhStrings());
        STRINGS.put("en_us", enStrings());
        STRINGS.put("ja_jp", jaStrings());
        STRINGS.put("fr_fr", frStrings());
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

    /**
     * 加载语言文件（用于热重载）
     */
    public static Map<String, String> loadLanguageFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        Map<String, String> strings = new HashMap<>();
        
        // 简单的 JSON 解析（实际项目中应使用 JSON 库）
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("\"") && line.contains("\":") && line.endsWith("\",")) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].substring(1).replace("\"", "");
                    String value = parts[1].substring(1).replace("\",", "");
                    strings.put(key, value);
                }
            }
        }
        
        return strings;
    }

    /**
     * 获取回退语言包（用于热重载时兜底）
     */
    public static Map<String, String> getFallbackStrings(String lang) {
        return STRINGS.getOrDefault(lang, STRINGS.get(DEFAULT_LANG));
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
        m.put("language.reloaded", "§a语言包已重新加载");
        m.put("language.updated", "§a语言 {0} 已更新");

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
        m.put("language.reloaded", "§aLanguage packages reloaded");
        m.put("language.updated", "§aLanguage {0} updated");

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

    private static Map<String, String> jaStrings() {
        Map<String, String> m = new HashMap<>();
        // ── ログイン / 登録 ──
        m.put("login.usage", "§e使い方: §f/login <パスワード> §eまたは §f/login code <コード>");
        m.put("login.success", "§aログイン成功！お帰りなさい");
        m.put("login.wrongPassword", "§cパスワードが違います");
        m.put("login.notRegistered", "§cアカウントが登録されていません。まず /register <パスワード> <確認> を使用してください");
        m.put("login.alreadyLoggedIn", "§a既にログインしています");
        m.put("login.notBound", "§cメールアドレスが未設定のため、コードログインはできません");
        m.put("login.codeUsage", "§e使い方: §f/login code <コード>");
        m.put("login.codeChecking", "§7コードを検証中、お待ちください...");
        m.put("login.codeSuccess", "§aコード検証成功！ログインしました");
        m.put("login.codePending", "§7コードがまだ到着していません。60秒間（5秒ごとに検索）検索を開始します...");
        m.put("login.codeHint", "§aヒント: /email verify でコードを取得した場合、メールを送信するとサーバーが自動で許可します。このコマンドを再入力する必要はありません");
        m.put("login.codeTimeout", "§cコード検索がタイムアウトしました（60秒）。コードをメールの件名に入れて {0} に送信した後、再試行してください");
        m.put("login.bindReminder", "§eメールアドレスを §f/email bind <メールアドレス> §eで設定することをお勧めします。コードログインとパスワード再設定に使用できます");
        m.put("register.usage", "§e使い方: §f/register <パスワード> <確認パスワード>");
        m.put("register.success", "§a登録成功！自動でログインしました");
        m.put("register.bindReminder", "§eヒント: コードログインとパスワード再設定のため、メールアドレスの設定をお勧めします");
        m.put("register.alreadyRegistered", "§cアカウントは既に登録されています。/login <パスワード> でログインしてください");
        m.put("register.passwordMismatch", "§c入力されたパスワードが一致しません");
        m.put("register.passwordTooShort", "§cパスワードは4文字以上必要です");

        // ── サーバー参加メッセージ ──
        m.put("join.title", "§6========== ログイン認証 ==========");
        m.put("join.registerTitle", "§6========== サーバーへようこそ ==========");
        m.put("join.loginPrompt", "§e§f/login <パスワード> §eでログインしてください");
        m.put("join.emailOption1", "§eまたは §f/email verify §eでコードを取得");
        m.put("join.emailOption2", "§eコードをメールの件名に入れて {0} に送信してください");
        m.put("join.emailOption3", "§aサーバーがコードを検出すると自動で許可され、他の操作は不要です");
        m.put("join.registerPrompt", "§e§f/register <パスワード> <確認パスワード> §eで登録してください");
        m.put("join.registerReminder", "§e登録後はメールアドレスの設定をお勧めします。コードログインとパスワード再設定に使用できます");
        m.put("join.warning", "§c注意: 未ログイン状態ではログイン待機エリア（観察者視点、装備とステータスバーが見えません）に移動され、移動、ブロック破壊、発言ができません！");

        // ── 言語 ──
        m.put("language.usage", "§e使い方: §f/language [zh_cn|en_us|ja_jp]");
        m.put("language.current", "§a現在の言語: §f{0}");
        m.put("language.available", "§e利用可能な言語: §fzh_cn §e(简体中文) §7/§f en_us §e(English) §7/§f ja_jp §e(日本語)");
        m.put("language.unknown", "§c不明な言語: {0}。利用可能: zh_cn, en_us, ja_jp");
        m.put("language.switched", "§a言語が §f{0} §aに切り替わりました");
        m.put("language.reloaded", "§a言語パッケージが再読み込みされました");
        m.put("language.updated", "§a言語 {0} が更新されました");

        // ── 未ログイン操作ブロック ──
        m.put("blocked.loginFirst", "§cまずログインしてください！ /login <パスワード> を使用");
        m.put("blocked.chat", "§cログイン後にチャットしてください！ /login <パスワード> を使用");
        m.put("blocked.commands", "§c未ログイン状態では、/login, /register, /email, /language コマンドのみ使用可能");

        // ── Eメール ──
        m.put("email.playerOnly", "§cこのコマンドはプレイヤーのみ使用可能です");
        m.put("email.bind.success", "§aメールアドレスの設定に成功しました！");
        m.put("email.bind.verifyReminder", "§eメールアドレスが使用可能か確認するには /email verify を使用してください");
        m.put("email.bind.badFormat", "§cメールアドレスの形式が正しくありません");
        m.put("email.bind.notRegistered", "§cまず /register <パスワード> <確認パスワード> で登録してください");
        m.put("email.bind.fail", "§c設定に失敗しました。まず登録してください");
        m.put("email.unbind.warn", "§cメールアドレスの解除は、コードログインとパスワード再設定ができなくなります！");
        m.put("email.unbind.confirmPrompt", "§e解除を確認する場合は、 /email unbind confirm を使用してください");
        m.put("email.unbind.success", "§aメールアドレスが解除されました");
        m.put("email.unbind.rebindHint", "§e再設定する場合は /email bind <メールアドレス> を使用してください");
        m.put("email.unbind.fail", "§c解除に失敗しました：メールアドレスが設定されていません");
        m.put("email.verify.title", "§6========== メール認証 ==========");
        m.put("email.verify.code", "§eあなたの認証コード: §f§l{0}");
        m.put("email.verify.subject", "§7コードをメールの【件名】に入れて送信してください");
        m.put("email.verify.sendTo", "§e送信先: {0}");
        m.put("email.verify.autoLogin", "§aサーバーがコードを検出すると自動でログインします");
        m.put("email.verify.checking", "§7コード検索中（最大60秒、5秒ごとにチェック）...");
        m.put("email.verify.timeout", "§cコード検索がタイムアウトしました（60秒）。コードをメールの件名に入れて送信した後、再試行してください");
        m.put("email.verify.success", "§aメール認証に成功しました！");
        m.put("email.verify.alreadyBound", "§cメールアドレスは既に設定されています");
        m.put("email.verify.notBound", "§cメールアドレスが設定されていません");
        m.put("email.verify.noCode", "§7認証コードがまだありません。メールを送信してから数秒待ってください");
        m.put("email.forgot.usage", "§e使い方: §f/email forgot <新しいパスワード> <確認パスワード>");
        m.put("email.forgot.success", "§aパスワードがリセットされました！新しいパスワードでログインしてください");
        m.put("email.forgot.notBound", "§cメールアドレスが設定されていないため、パスワードリセットはできません");
        m.put("email.forgot.verifyRequired", "§cメール認証が必要です。まず /email verify を実行してください");
        m.put("email.forgot.passwordMismatch", "§c入力された新しいパスワードが一致しません");
        m.put("email.forgot.passwordTooShort", "§c新しいパスワードは4文字以上必要です");
        m.put("email.forgot.apiError", "§cAPIエラー: {0}");
        m.put("email.status.bound", "§aメールアドレス: {0}");
        m.put("email.status.notBound", "§cメールアドレスは未設定です");
        m.put("email.status.verified", "§aメール認証: 有効");
        m.put("email.status.notVerified", "§7メール認証: 未実行");
        m.put("email.status.apiError", "§cAPIエラー: {0}");

        // ── コマンド一般 ──
        m.put("command.usage", "§e使い方: {0}");
        m.put("command.noPermission", "§c権限がありません");
        m.put("command.playerOnly", "§cプレイヤーのみ使用可能です");
        m.put("command.error", "§cコマンド実行エラー: {0}");
        m.put("command.success", "§a操作成功");

        return m;
    }

    private static Map<String, String> frStrings() {
        Map<String, String> m = new HashMap<>();
        // ── Connexion / Inscription ──
        m.put("login.usage", "§eUsage: §f/login <mot_de_passe> §eou §f/login code <code>");
        m.put("login.success", "§aConnexion réussie! Bienvenue");
        m.put("login.wrongPassword", "§cMot de passe incorrect");
        m.put("login.notRegistered", "§cCompte non enregistré. Veuillez d'abord utiliser /register <mot_de_passe> <confirmation>");
        m.put("login.alreadyLoggedIn", "§aVous êtes déjà connecté");
        m.put("login.notBound", "§cAucun e-mail lié, impossible d'utiliser la connexion par code");
        m.put("login.codeUsage", "§eUsage: §f/login code <code>");
        m.put("login.codeChecking", "§7Vérification du code, veuillez patienter...");
        m.put("login.codeSuccess", "§aCode vérifié avec succès! Connecté");
        m.put("login.codePending", "§7Le code n'est pas encore arrivé. Début de la détection toutes les 5 secondes (max 60s)...");
        m.put("login.codeHint", "§aAstuce: Si vous avez obtenu un code via /email verify, l'envoi du e-mail déclenchera automatiquement l'accès, pas besoin de taper cette commande");
        m.put("login.codeTimeout", "§cDélai d'attente du code dépassé (60s). Veuillez entrer le code dans l'objet du e-mail et envoyer à {0}, puis réessayer");
        m.put("login.bindReminder", "§eNous recommandons de lier un e-mail avec §f/email bind <e-mail> §e pour la connexion par code et la récupération de mot de passe");
        m.put("register.usage", "§eUsage: §f/register <mot_de_passe> <confirmation>");
        m.put("register.success", "§aInscription réussie! Connexion automatique");
        m.put("register.bindReminder", "§eAstuce: Liez un e-mail pour utiliser la connexion par code et récupérer votre mot de passe");
        m.put("register.alreadyRegistered", "§cCompte déjà enregistré. Veuillez utiliser /login <mot_de_passe>");
        m.put("register.passwordMismatch", "§cLes mots de passe ne correspondent pas");
        m.put("register.passwordTooShort", "§cLe mot de passe doit contenir au moins 4 caractères");

        // ── Messages de bienvenue ──
        m.put("join.title", "§6========== Connexion Requise ==========");
        m.put("join.registerTitle", "§6========== Bienvenue sur le Serveur ==========");
        m.put("join.loginPrompt", "§eVeuillez utiliser §f/login <mot_de_passe> §epour vous connecter");
        m.put("join.emailOption1", "§eou utilisez §f/email verify §epour obtenir un code");
        m.put("join.emailOption2", "§eEntrez le code dans l'objet du e-mail et envoyez-le à {0}");
        m.put("join.emailOption3", "§aLe serveur vous accordera l'accès automatiquement dès qu'il détecte le code");
        m.put("join.registerPrompt", "§eVeuillez utiliser §f/register <mot_de_passe> <confirmation> §epour vous inscrire");
        m.put("join.registerReminder", "§eAprès inscription, liez un e-mail pour la connexion par code et récupération de mot de passe");
        m.put("join.warning", "§cAttention: Non connecté, vous êtes dans la zone d'attente (vue spectateur, équipement et HUD invisibles), impossible de bouger, détruire, construire, interagir, attaquer, ramasser ou chatter!");

        // ── Langue ──
        m.put("language.usage", "§eUsage: §f/language [zh_cn|en_us|fr_fr]");
        m.put("language.current", "§aLangue actuelle: §f{0}");
        m.put("language.available", "§eLangues disponibles: §fzh_cn §e(简体中文) §7/§f en_us §e(English) §7/§f fr_fr §e(Français)");
        m.put("language.unknown", "§cLangue inconnue: {0}. Disponibles: zh_cn, en_us, fr_fr");
        m.put("language.switched", "§aLangue changée en §f{0}");
        m.put("language.reloaded", "§aPacks de langue rechargés");
        m.put("language.updated", "§aLangue {0} mise à jour");

        // ── Actions bloquées sans connexion ──
        m.put("blocked.loginFirst", "§cVeuillez d'abord vous connecter! Utilisez /login <mot_de_passe>");
        m.put("blocked.chat", "§cVeuillez vous connecter avant de chatter! Utilisez /login <mot_de_passe>");
        m.put("blocked.commands", "§cNon connecté, seuls ces commandes sont autorisées: /login, /register, /email, /language");

        // ── E-mail ──
        m.put("email.playerOnly", "§cCette commande est réservée aux joueurs");
        m.put("email.bind.success", "§aE-mail lié avec succès!");
        m.put("email.bind.verifyReminder", "§eNous recommandons d'utiliser /email verify pour vérifier si l'e-mail fonctionne");
        m.put("email.bind.badFormat", "§cFormat d'e-mail invalide");
        m.put("email.bind.notRegistered", "§cVeuillez d'abord vous inscrire avec /register <mot_de_passe> <confirmation>");
        m.put("email.bind.fail", "§cÉchec de liaison, veuillez d'abord vous inscrire");
        m.put("email.unbind.warn", "§cDélier l'e-mail empêchera la connexion par code et la récupération de mot de passe!");
        m.put("email.unbind.confirmPrompt", "§ePour confirmer le déliement, utilisez /email unbind confirm");
        m.put("email.unbind.success", "§aE-mail délié");
        m.put("email.unbind.rebindHint", "§ePour le lier à nouveau, utilisez /email bind <e-mail>");
        m.put("email.unbind.fail", "§cÉchec du déliement: aucun e-mail lié");
        m.put("email.verify.title", "§6========== Vérification E-mail ==========");
        m.put("email.verify.code", "§eVotre code de vérification: §f§l{0}");
        m.put("email.verify.subject", "§7Veuillez entrer le code dans l'[objet] du e-mail");
        m.put("email.verify.sendTo", "§eEnvoyer à: {0}");
        m.put("email.verify.autoLogin", "§aLe serveur vous accordera l'accès automatiquement dès qu'il détectera le code");
        m.put("email.verify.checking", "§7Recherche du code (max 60s, vérification toutes les 5s)...");
        m.put("email.verify.timeout", "§cDélai de recherche du code dépassé (60s). Veuillez entrer le code dans l'objet et envoyer, puis réessayer");
        m.put("email.verify.success", "§aVérification e-mail réussie!");
        m.put("email.verify.alreadyBound", "§cUn e-mail est déjà lié");
        m.put("email.verify.notBound", "§cAucun e-mail lié");
        m.put("email.verify.noCode", "§7Aucun code de vérification reçu. Veuillez attendre quelques secondes après l'envoi du e-mail");
        m.put("email.forgot.usage", "§eUsage: §f/email forgot <nouveau_mot_de_passe> <confirmation>");
        m.put("email.forgot.success", "§aMot de passe réinitialisé! Veuillez vous connecter avec le nouveau mot de passe");
        m.put("email.forgot.notBound", "§cAucun e-mail lié, impossible de réinitialiser le mot de passe");
        m.put("email.forgot.verifyRequired", "§cVérification e-mail requise. Veuillez d'abord exécuter /email verify");
        m.put("email.forgot.passwordMismatch", "§cLes nouveaux mots de passe ne correspondent pas");
        m.put("email.forgot.passwordTooShort", "§cLe nouveau mot de passe doit contenir au moins 4 caractères");
        m.put("email.forgot.apiError", "§cErreur API: {0}");
        m.put("email.status.bound", "§aE-mail: {0}");
        m.put("email.status.notBound", "§cAucun e-mail lié");
        m.put("email.status.verified", "§aVérification e-mail: Valide");
        m.put("email.status.notVerified", "§7Vérification e-mail: Non effectuée");
        m.put("email.status.apiError", "§cErreur API: {0}");

        // ── Commandes générales ──
        m.put("command.usage", "§eUsage: {0}");
        m.put("command.noPermission", "§cPermissions insuffisantes");
        m.put("command.playerOnly", "§cCommande réservée aux joueurs");
        m.put("command.error", "§cErreur d'exécution: {0}");
        m.put("command.success", "§aOpération réussie");

        return m;
    }
}
