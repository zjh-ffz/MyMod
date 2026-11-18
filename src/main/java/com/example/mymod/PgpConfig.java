package com.example.mymod;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 模组配置（COMMON）。可在 config/mymod-common.toml 里改。
 */
public final class PgpConfig {

    private static final String DEFAULT_KEYSERVER = "https://keys.openpgp.org";
    private static final String DEFAULT_DATA_DIR = "mods/mymoddata";

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.ConfigValue<String> KEYSERVER;
    private static final ForgeConfigSpec.BooleanValue ALLOW_INSECURE_HTTP;
    private static final ForgeConfigSpec.ConfigValue<String> DATA_DIRECTORY;
    private static final ForgeConfigSpec.BooleanValue DELETE_SOURCE_AFTER_DECRYPT;
    private static final ForgeConfigSpec.BooleanValue REMEMBER_PASSPHRASE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("OpenPGP 设置").push("pgp");

        KEYSERVER = builder
                .comment("默认 OpenPGP 公钥服务器（HKP over HTTPS）",
                        "可用 /pgp keyserver set <url> 在游戏内临时覆盖")
                .define("keyserver", DEFAULT_KEYSERVER);

        ALLOW_INSECURE_HTTP = builder
                .comment("允许使用明文 http:// 连接密钥服务器（不推荐）")
                .define("allowInsecureHttp", false);

        DATA_DIRECTORY = builder
                .comment("数据目录：存放 keys/、passwords.json、.secret")
                .define("dataDirectory", DEFAULT_DATA_DIR);

        DELETE_SOURCE_AFTER_DECRYPT = builder
                .comment("文件解密成功后删除 .gpg 源文件（原型要求的行为）")
                .define("deleteSourceAfterDecrypt", true);

        REMEMBER_PASSPHRASE = builder
                .comment("记住密钥口令（AES-256-GCM 加密后存入 passwords.json）")
                .define("rememberPassphrase", true);

        builder.pop();
        SPEC = builder.build();
    }

    private PgpConfig() {
    }

    public static String keyserver() {
        return read(KEYSERVER, DEFAULT_KEYSERVER);
    }

    public static boolean allowInsecureHttp() {
        try {
            return ALLOW_INSECURE_HTTP.get();
        } catch (Exception e) {
            return false;
        }
    }

    public static String dataDirectory() {
        return read(DATA_DIRECTORY, DEFAULT_DATA_DIR);
    }

    public static boolean deleteSourceAfterDecrypt() {
        try {
            return DELETE_SOURCE_AFTER_DECRYPT.get();
        } catch (Exception e) {
            return true;
        }
    }

    public static boolean rememberPassphrase() {
        try {
            return REMEMBER_PASSPHRASE.get();
        } catch (Exception e) {
            return true;
        }
    }

    private static String read(ForgeConfigSpec.ConfigValue<String> value, String fallback) {
        try {
            String v = value.get();
            return (v == null || v.isBlank()) ? fallback : v;
        } catch (Exception e) {
            return fallback;
        }
    }
}
