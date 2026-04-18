package com.example.mymod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;

public class PgpCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean clientOnly) {
        dispatcher.register(Commands.literal("pgp")
            .then(Commands.literal("gui")
                .executes(context -> executeGui(context.getSource(), clientOnly)))
            .then(Commands.literal("genkey")
                .then(Commands.argument("identity", StringArgumentType.word())
                    .then(Commands.argument("passphrase", StringArgumentType.greedyString())
                        .executes(context -> executeGenerateKey(context.getSource(), StringArgumentType.getString(context, "identity"), StringArgumentType.getString(context, "passphrase"))))))
            .then(Commands.literal("export")
                .then(Commands.literal("public")
                    .executes(context -> executeExportPublic(context.getSource())))
                .then(Commands.literal("private")
                    .executes(context -> executeExportPrivate(context.getSource()))))
            .then(Commands.literal("encrypt")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> executeEncrypt(context.getSource(), StringArgumentType.getString(context, "message")))))
            .then(Commands.literal("decrypt")
                .then(Commands.argument("ciphertext", StringArgumentType.greedyString())
                    .executes(context -> executeDecrypt(context.getSource(), StringArgumentType.getString(context, "ciphertext")))))
            .then(Commands.literal("sign")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes(context -> executeSign(context.getSource(), StringArgumentType.getString(context, "message")))))
            .then(Commands.literal("verify")
                .then(Commands.argument("payload", StringArgumentType.greedyString())
                    .executes(context -> executeVerify(context.getSource(), StringArgumentType.getString(context, "payload")))))
        );
    }

    private static int executeGui(CommandSourceStack source, boolean clientOnly) {
        if (!clientOnly) {
            source.sendFailure(Component.literal("[PGP] GUI 只能在客户端使用。"));
            return 0;
        }
        Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new PgpScreen()));
        source.sendSuccess(() -> Component.literal("[PGP] 已打开 OpenPGP 操作窗口。"), false);
        return 1;
    }

    private static int executeGenerateKey(CommandSourceStack source, String identity, String passphrase) {
        try {
            String publicKey = PgpManager.generateKeyPair(identity, passphrase);
            source.sendSuccess(() -> Component.literal("[PGP] 密钥对已生成。使用 /pgp export public 查看公钥。"), false);
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

    private static int executeEncrypt(CommandSourceStack source, String message) {
        try {
            String encrypted = PgpManager.encrypt(message);
            source.sendSuccess(() -> Component.literal("[PGP] 加密结果:\n" + encrypted), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 加密失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDecrypt(CommandSourceStack source, String ciphertext) {
        try {
            String decrypted = PgpManager.decrypt(ciphertext);
            source.sendSuccess(() -> Component.literal("[PGP] 解密结果:\n" + decrypted), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 解密失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeSign(CommandSourceStack source, String message) {
        try {
            String signature = PgpManager.sign(message);
            source.sendSuccess(() -> Component.literal("[PGP] 签名结果:\n" + signature), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 签名失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeVerify(CommandSourceStack source, String payload) {
        try {
            String[] parts = payload.split("\\|", 2);
            if (parts.length != 2) {
                source.sendFailure(Component.literal("[PGP] 请使用格式: /pgp verify <message>|<signature>"));
                return 0;
            }
            boolean valid = PgpManager.verify(parts[0], parts[1]);
            source.sendSuccess(() -> Component.literal("[PGP] 签名验证: " + (valid ? "有效" : "无效")), false);
            return valid ? 1 : 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[PGP] 验证失败: " + e.getMessage()));
            return 0;
        }
    }
}
