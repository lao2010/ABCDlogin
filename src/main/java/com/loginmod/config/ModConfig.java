/*
 * Copyright (c) 2026 lao20
 * SPDX-License-Identifier: MIT
 */

package com.loginmod.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * 模组配置 (config/loginmod-server.toml)
 *
 * 注意：默认值不包含任何个人服务器地址，方便他人直接使用。
 * 使用者只需配置自己的邮箱验证服务地址即可。
 */
public class ModConfig {
    public static final ModConfigSpec SPEC;
    public static final Config DATA;

    public static class Config {
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

        Config(ModConfigSpec.Builder builder) {
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
        Pair<Config, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Config::new);
        DATA = pair.getLeft();
        SPEC = pair.getRight();
    }

    public static void init(ModContainer container) {
        container.registerConfig(Type.SERVER, SPEC, "loginmod-server.toml");
    }

    /** 获取验证码接收邮箱，未配置时返回提示文本 */
    public static String recipientDisplay() {
        String r = DATA.emailRecipient.get();
        return (r == null || r.isBlank()) ? "(未配置，请联系管理员设置 email.recipient)" : r;
    }
}
