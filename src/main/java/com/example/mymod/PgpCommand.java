package com.example.mymod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PgpCommand {

    /** 会话内覆盖配置里的密钥服务器（/pgp keyserver set ...）。 */
    private static String keyserverOverride = null;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean clientOnly) {
        dispatcher.register(Commands.literal("pgp")
                .then(Commands.literal("gui")
                        .executes(context -> executeGui(context.getSource(), clientOnly)))
                .then(Commands.literal("genkey")
                        .then(Commands.argument("identity", StringArgumentType.word())
                                .then(Commands.argument("passphrase", StringArgumentType.greedyString())
                                        .executes(context -> executeGenerateKey(context.getSource(),
                                                StringArgumentType.getString(context, "identity"),
                                                StringArgumentType.getString(context, "passphrase"))))))
                .then(Commands.literal("export")
                        .then(Commands.literal("public")
                                .executes(context -> executeExportPublic(context.getSource())))
                        .then(Commands.literal("private")
                                .executes(context -> executeExportPrivate(context.getSource()))))
                .then(Commands.literal("keys")
                        .executes(context -> executeListKeys(context.getSource())))
                .then(Commands.literal("encrypt")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> executeEncrypt(context.getSource(),
                                        StringArgumentType.getString(context, "message")))))
                .then(Commands.literal("decrypt")
                        .then(Commands.argument("ciphertext", StringArgumentType.greedyString())
                                .executes(context -> executeDecrypt(context.getSource(),
                                        StringArgumentType.getString(context, "ciphertext")))))
                .then(Commands.literal("sign")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(context -> executeSign(context.getSource(),
                                        StringArgumentType.getString(context, "message")))))
                .then(Commands.literal("verify")
                        .then(Commands.argument("payload", StringArgumentType.greedyString())
                                .executes(context -> executeVerify(context.getSource(),
                                        StringArgumentType.getString(context, "payload")))))
                .then(Commands.literal("encryptfile")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(context -> executeEncryptFile(context.getSource(),
                                        StringArgumentType.getString(context, "path")))))
                .then(Commands.literal("decryptfile")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(context -> executeDecryptFile(context.getSource(),
                                        StringArgumentType.getString(context, "path")))))
                .then(Commands.literal("fetch")
                        .then(Commands.argument("query", StringArgumentType.greedyString())
                                .executes(context -> executeFetch(context.getSource(),
                                        StringArgumentType.getString(context, "query")))))
                .then(Commands.literal("keyserver")
                        .executes(context -> executeKeyserverShow(context.getSource()))
                        .then(Commands.literal("set")
                                .then(Commands.argument("url", StringArgumentType.greedyString())
                                        .executes(context -> executeKeyserverSet(context.getSource(),
                                                StringArgumentType.getString(context, "url")))))
                        .then(Commands.literal("reset")
                                .executes(context -> {
                                    keyserverOverride = null;
                                    context.getSource().sendSuccess(() ->
                                            Component.literal("[PGP] 已恢复配置中的密钥服务器: " + PgpConfig.keyserver()), false);
                                    return 1;
                                })))
                .then(Commands.literal("forget")
                        .then(Commands.argument("fingerprint", StringArgumentType.word())
                                .executes(context -> {
                                    String fpr = StringArgumentType.getString(context, "fingerprint");
                                    boolean removed = PgpManager.forgetPassphrase(fpr);
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            removed ? "[PGP] 已删除该指纹记住的口令。" : "[PGP] 该指纹没有记住的口令。"), false);
                                    return removed ? 1 : 0;
                                })))
        );
    }

    // ---------------------------------------------------------------- 基础

    private static int executeGui(CommandSourceStack source, boolean clientOnly) {
        if (!clientOnly) {
            source.sendFailure(Component.literal("[PGP] GUI 只能在客户端使用。"));
            return 0;
        }
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new PgpScreen()));
        source.sendSuccess(() -> Component.literal("[PGP] 已打开 OpenPGP 操作面板。"), false);
        return 1;
    }

    private static int executeGenerateKey(CommandSourceStack source, String identity, String passphrase) {
        PgpProgress progress = new PgpProgress.Chat(source, "生成密钥");
        try {
            PgpManager.generateKeyPair(identity, passphrase, true, progress);
            source.sendSuccess(() -> Component.literal(
                    "[PGP] 密钥对已生成，口令已加密保存。用 /pgp export public 查看公钥。"), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 生成失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeExportPublic(CommandSourceStack source) {
        try {
            String publicKey = PgpManager.exportPublicKey();
            source.sendSuccess(() -> Component.literal("[PGP] 公钥:\n" + publicKey), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 导出公钥失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeExportPrivate(CommandSourceStack source) {
        try {
            String privateKey = PgpManager.exportPrivateKey();
            source.sendSuccess(() -> Component.literal("[PGP] 私钥:\n" + privateKey), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 导出私钥失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeListKeys(CommandSourceStack source) {
        try {
            PgpManager.initialize();
            var keys = PgpManager.publicKeySummaries();
            if (keys.isEmpty()) {
                source.sendSuccess(() -> Component.literal("[PGP] 当前没有可用公钥。"), false);
                return 0;
            }
            StringBuilder sb = new StringBuilder("[PGP] 公钥列表:\n");
            for (String key : keys) {
                sb.append("  ").append(key).append('\n');
            }
            source.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 查询失败: " + e.getMessage()));
            return 0;
        }
    }

    // ---------------------------------------------------------------- 文本操作

    private static int executeEncrypt(CommandSourceStack source, String message) {
        PgpProgress progress = new PgpProgress.Chat(source, "加密");
        try {
            String encrypted = PgpManager.encrypt(message, progress);
            source.sendSuccess(() -> Component.literal("[PGP] 加密结果:\n" + encrypted), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 加密失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDecrypt(CommandSourceStack source, String ciphertext) {
        PgpProgress progress = new PgpProgress.Chat(source, "解密");
        try {
            String decrypted = PgpManager.decrypt(ciphertext, progress);
            source.sendSuccess(() -> Component.literal("[PGP] 解密结果:\n" + decrypted), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 解密失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeSign(CommandSourceStack source, String message) {
        PgpProgress progress = new PgpProgress.Chat(source, "签名");
        try {
            String signature = PgpManager.sign(message, progress);
            source.sendSuccess(() -> Component.literal("[PGP] 签名结果:\n" + signature), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 签名失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeVerify(CommandSourceStack source, String payload) {
        String[] parts = payload.split("\\|", 2);
        if (parts.length != 2) {
            source.sendFailure(Component.literal("[PGP] 请使用格式: /pgp verify <message>|<signature>"));
            return 0;
        }
        PgpProgress progress = new PgpProgress.Chat(source, "验签");
        try {
            boolean valid = PgpManager.verify(parts[0], parts[1], progress);
            source.sendSuccess(() -> Component.literal("[PGP] 签名验证: " + (valid ? "有效" : "无效")), false);
            return valid ? 1 : 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 验证失败: " + e.getMessage()));
            return 0;
        }
    }

    // ---------------------------------------------------------------- 文件操作

    private static int executeEncryptFile(CommandSourceStack source, String rawPath) {
        Path path = resolve(rawPath);
        PgpProgress progress = new PgpProgress.Chat(source, "文件加密");
        try {
            Path out = PgpManager.encryptFile(path, progress);
            source.sendSuccess(() -> Component.literal("[PGP] 已生成: " + out.toAbsolutePath()), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 文件加密失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDecryptFile(CommandSourceStack source, String rawPath) {
        Path path = resolve(rawPath);
        PgpProgress progress = new PgpProgress.Chat(source, "文件解密");
        try {
            Path out = PgpManager.decryptFile(path, progress);
            String extra = PgpConfig.deleteSourceAfterDecrypt() ? "（源文件已删除）" : "";
            source.sendSuccess(() -> Component.literal("[PGP] 已解密到: " + out.toAbsolutePath() + extra), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 文件解密失败: " + e.getMessage()));
            return 0;
        }
    }

    // ---------------------------------------------------------------- 密钥服务器

    private static int executeFetch(CommandSourceStack source, String query) {
        PgpProgress progress = new PgpProgress.Chat(source, "下载公钥");
        try {
            var result = PgpManager.fetchKeys(query, progress);
            StringBuilder sb = new StringBuilder("[PGP] 从 "
                    + PgpManager.currentKeyserver() + " 获取到 " + result.summaries().size() + " 个公钥:\n");
            for (String line : result.summaries()) {
                sb.append("  ").append(line).append('\n');
            }
            source.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 下载公钥失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeKeyserverShow(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("[PGP] 当前密钥服务器: " + PgpManager.currentKeyserver()
                + "\n用法: /pgp keyserver set <url> | /pgp keyserver reset"), false);
        return 1;
    }

    private static int executeKeyserverSet(CommandSourceStack source, String url) {
        keyserverOverride = url.trim();
        source.sendSuccess(() -> Component.literal("[PGP] 本次会话使用密钥服务器: " + keyserverOverride), false);
        return 1;
    }

    public static String keyserverOverride() {
        return keyserverOverride;
    }

    private static Path resolve(String rawPath) {
        Path path = Paths.get(rawPath.trim());
        if (!path.isAbsolute()) {
            path = Paths.get("").toAbsolutePath().resolve(path).normalize();
        }
        return path;
    }
}
