package com.example.mymod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PgpScreen extends Screen {

    private EditBox identityBox;
    private EditBox passphraseBox;
    private EditBox inputBox;
    private EditBox keyserverBox;
    private Checkbox rememberBox;
    private String statusText = "输入内容后选择操作。";

    protected PgpScreen() {
        super(Component.literal("OpenPGP 操作面板"));
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 160;
        int right = this.width / 2 + 10;
        int top = 50;

        String playerName = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getGameProfile().getName()
                : "player";

        identityBox = new EditBox(this.font, left, top, 310, 20, Component.literal("身份"));
        identityBox.setValue(playerName + "@example.com");
        this.addRenderableWidget(identityBox);

        passphraseBox = new EditBox(this.font, left, top + 26, 310, 20, Component.literal("口令"));
        passphraseBox.setFormatter((text, cursor) -> Component.literal("*".repeat(text.length())));
        this.addRenderableWidget(passphraseBox);

        rememberBox = Checkbox.builder(Component.literal("记住口令（加密保存）"), this.font)
                .pos(left, top + 50)
                .selected(PgpConfig.rememberPassphrase())
                .build();
        this.addRenderableWidget(rememberBox);

        this.addRenderableWidget(Button.builder(Component.literal("生成密钥"), button -> runWithProgress(
                "生成密钥", progress -> {
                    PgpManager.generateKeyPair(identityBox.getValue(), passphraseBox.getValue(),
                            rememberBox.selected(), progress);
                    return "密钥已生成，可用 /pgp export public 查看公钥。";
                })).bounds(left, top + 76, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("下载公钥"), button -> runWithProgress(
                "下载公钥", progress -> {
                    var result = PgpManager.fetchKeys(keyserverBox.getValue(), progress);
                    return "已获取 " + result.summaries().size() + " 个公钥。";
                })).bounds(right, top + 76, 150, 20).build());

        inputBox = new EditBox(this.font, left, top + 112, 310, 20, Component.literal("待处理内容"));
        inputBox.setValue("Hello Minecraft OpenPGP!");
        this.addRenderableWidget(inputBox);

        this.addRenderableWidget(Button.builder(Component.literal("加密文本"), button -> runWithProgress(
                "加密", progress -> PgpManager.encrypt(inputBox.getValue(), progress)))
                .bounds(left, top + 138, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("解密文本"), button -> runWithProgress(
                "解密", progress -> PgpManager.decrypt(inputBox.getValue(), progress)))
                .bounds(right, top + 138, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("签名文本"), button -> runWithProgress(
                "签名", progress -> PgpManager.sign(inputBox.getValue(), progress)))
                .bounds(left, top + 164, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("返回游戏"), button ->
                        Minecraft.getInstance().setScreen(null))
                .bounds(right, top + 164, 150, 20).build());

        keyserverBox = new EditBox(this.font, left, top + 200, 310, 20, Component.literal("密钥服务器"));
        keyserverBox.setValue(PgpManager.currentKeyserver());
        this.addRenderableWidget(keyserverBox);
    }

    private interface Operation {
        String run(PgpProgress progress) throws Exception;
    }

    private void runWithProgress(String label, Operation operation) {
        PgpProgress progress = new ScreenProgress(label);
        new Thread(() -> {
            try {
                String message = operation.run(progress);
                Minecraft.getInstance().execute(() -> statusText = message);
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> statusText = label + "失败: " + e.getMessage());
            }
        }, "mymod-pgp-" + label).start();
    }

    /** 界面内进度：直接刷新底部状态文字。 */
    private final class ScreenProgress implements PgpProgress {
        private final String label;

        private ScreenProgress(String label) {
            this.label = label;
        }

        @Override
        public void update(int percent, String stage) {
            String text = "[" + PgpProgress.bar(percent) + "] " + percent + "% "
                    + (stage == null ? label : stage);
            Minecraft.getInstance().execute(() -> statusText = text);
        }

        @Override
        public void done(String message) {
            Minecraft.getInstance().execute(() -> statusText = message);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        int left = this.width / 2 - 160;
        guiGraphics.drawString(this.font, Component.literal("身份"), left, 40, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("口令"), left, 66, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("内容"), left, 152, 0xAAAAAA);
        guiGraphics.drawString(this.font, Component.literal("密钥服务器"), left, 240, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        guiGraphics.drawCenteredString(this.font, statusText, this.width / 2, this.height - 40, 0xFFCC66);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
