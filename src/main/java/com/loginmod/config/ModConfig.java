package com.loginmod.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class ModConfig {
    public static final ModConfigSpec SPEC;
    public static final Config DATA;

    public static class Config {
        public final ModConfigSpec.ConfigValue<String> emailApiUrl;
        public final ModConfigSpec.ConfigValue<String> emailApiPassword;
        public final ModConfigSpec.ConfigValue<Integer> emailApiTimeout;

        Config(ModConfigSpec.Builder builder) {
            builder.push("email");
            emailApiUrl = builder
                .comment("邮箱验证码查询API地址")
                .define("apiUrl", "http://v.lhjedu.dpdns.org/records");
            emailApiPassword = builder
                .comment("API认证密码标头(pwd)")
                .define("apiPassword", "test");
            emailApiTimeout = builder
                .comment("API请求超时(毫秒)")
                .defineInRange("apiTimeout", 5000, 1000, 30000);
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
}
