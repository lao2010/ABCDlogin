/*
 * Copyright (c) 2026 lao20
 * SPDX-License-Identifier: MIT
 */

package com.abcdlogin.config;

import com.abcdlogin.ABCDlogin;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * 模组配置 (config/abcdlogin-server.toml)
 *
 * 注意：默认值不包含任何个人服务器地址，方便他人直接使用。
 * 使用者只需配置自己的邮箱验证服务地址即可。
 */
public class ModConfig {
    public static final ModConfigSpec SPEC;
    public static final Config DATA;

    public static class Config {
        // ── 常规 ──
        public final ModConfigSpec.ConfigValue<String> defaultLanguage;

        // ── 邮箱验证服务 ──
        public final ModConfigSpec.ConfigValue<String> emailRecipient;
        public final ModConfigSpec.ConfigValue<String> emailApiUrl;
        public final ModConfigSpec.ConfigValue<String> emailApiPassword;
        public final ModConfigSpec.ConfigValue<Integer> emailApiTimeout;

        // ── 登录行为 ──
        public final ModConfigSpec.ConfigValue<Integer> waitYOffset;
        public final ModConfigSpec.ConfigValue<Integer> pollIntervalMs;
        public final ModConfigSpec.ConfigValue<Integer> pollTimeoutMs;
        public final ModConfigSpec.ConfigValue<Integer> codeExpiryMs;

        Config(ModConfigSpec.Builder builder, String detectedLanguage) {
            builder.push("general");
            defaultLanguage = builder
                .comment("默认语言 (zh_cn / en_us)",
                         "新玩家与未设置语言的玩家使用此语言",
                         "首次生成配置时自动检测服务器语言: 服务器语言非中文则默认英文")
                .define("defaultLanguage", detectedLanguage);
            builder.pop();

            builder.push("email");
            emailRecipient = builder
                .comment("玩家发送验证码的目标邮箱地址（填写到邮件主题）",
                         "示例: verify@example.com")
                .define("recipient", "");
            emailApiUrl = builder
                .comment("验证码查询 API 地址",
                         "服务器通过 GET 请求该地址查询验证码列表",
                         "示例: http://example.com/records")
                .define("apiUrl", "");
            emailApiPassword = builder
                .comment("API 认证密码（请求标头 pwd）")
                .define("apiPassword", "test");
            emailApiTimeout = builder
                .comment("API 请求超时（毫秒）")
                .defineInRange("apiTimeout", 5000, 1000, 30000);
            builder.pop();

            builder.push("login");
            waitYOffset = builder
                .comment("登录等待区高度偏移（格）",
                         "未登录玩家被传送到出生点上方这个高度")
                .defineInRange("waitYOffset", 300, 100, 1000);
            pollIntervalMs = builder
                .comment("验证码自动检测间隔（毫秒）")
                .defineInRange("pollIntervalMs", 5000, 1000, 60000);
            pollTimeoutMs = builder
                .comment("验证码自动检测超时（毫秒）")
                .defineInRange("pollTimeoutMs", 300000, 10000, 600000);
            codeExpiryMs = builder
                .comment("验证码有效期（毫秒）")
                .defineInRange("codeExpiryMs", 300000, 10000, 600000);
            builder.pop();
        }
    }

    static {
        // 首次创建配置时检测服务器语言: 服务器语言非中文 -> 默认英文
        String detectedLanguage = detectServerLanguage();
        Pair<Config, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(builder -> new Config(builder, detectedLanguage));
        DATA = pair.getLeft();
        SPEC = pair.getRight();
    }

    /**
     * 检测服务器语言 (server.properties 的 language 键):
     * - 服务器语言以 zh 开头 -> zh_cn
     * - 服务器语言非中文（如 en_us / de_de 等）-> en_us
     * - 未设置 language 键 -> zh_cn
     */
    private static String detectServerLanguage() {
        try {
            java.nio.file.Path serverProps = FMLPaths.GAMEDIR.get().resolve("server.properties");
            if (java.nio.file.Files.exists(serverProps)) {
                for (String rawLine : java.nio.file.Files.readAllLines(serverProps)) {
                    String line = rawLine.trim();
                    if (line.startsWith("language=")) {
                        String lang = line.substring("language=".length()).trim().toLowerCase();
                        if (lang.startsWith("zh")) {
                            ABCDlogin.LOGGER.info("[ABCDlogin] 检测到服务器语言为中文 ({}), 默认语言: zh_cn", lang);
                            return "zh_cn";
                        }
                        if (!lang.isEmpty()) {
                            ABCDlogin.LOGGER.info("[ABCDlogin] 服务器语言为 {} (非中文), 默认语言: en_us", lang);
                            return "en_us";
                        }
                    }
                }
                ABCDlogin.LOGGER.info("[ABCDlogin] server.properties 未设置 language，默认语言: zh_cn");
            } else {
                ABCDlogin.LOGGER.info("[ABCDlogin] 未找到 server.properties，默认语言: zh_cn");
            }
        } catch (Exception e) {
            ABCDlogin.LOGGER.warn("[ABCDlogin] 读取服务器语言失败: {}", e.getMessage());
        }
        return "zh_cn";
    }

    public static void init(ModContainer container) {
        migrateLegacyConfig();
        container.registerConfig(Type.SERVER, SPEC, "abcdlogin-server.toml");
    }

    /**
     * 旧版本（LoginMod）配置文件自动迁移：
     * 若新配置 abcdlogin-server.toml 不存在，但旧配置 loginmod-server.toml 存在，
     * 自动复制旧配置内容，保留服务器原有设置。
     */
    private static void migrateLegacyConfig() {
        try {
            java.nio.file.Path configDir = FMLPaths.CONFIGDIR.get();
            java.nio.file.Path newFile = configDir.resolve("abcdlogin-server.toml");
            java.nio.file.Path legacyFile = configDir.resolve("loginmod-server.toml");
            if (!java.nio.file.Files.exists(newFile) && java.nio.file.Files.exists(legacyFile)) {
                java.nio.file.Files.copy(legacyFile, newFile);
                ABCDlogin.LOGGER.info("[ABCDlogin] 检测到旧版配置文件 loginmod-server.toml，已自动迁移为 abcdlogin-server.toml");
            }
        } catch (Exception e) {
            ABCDlogin.LOGGER.error("[ABCDlogin] 旧版配置文件迁移异常", e);
        }
    }

    /** 获取验证码接收邮箱，未配置时返回提示文本 */
    public static String recipientDisplay() {
        String r = DATA.emailRecipient.get();
        return (r == null || r.isBlank()) ? "(未配置，请联系管理员设置 email.recipient)" : r;
    }
}
