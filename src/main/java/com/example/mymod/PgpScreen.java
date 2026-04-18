package com.example.mymod;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class PgpScreen extends Screen {
    private EditBox identityBox;
    private EditBox passphraseBox;
    private EditBox inputBox;
    private String statusText = "在这里输入内容，然后点击按钮。";

    protected PgpScreen() {
        super(Component.literal("OpenPGP 操作面板"));
    }

    @Override
    protected void init() {
        int widthCenter = this.width / 2;
        int top = this.height / 4;

        identityBox = new EditBox(this.font, widthCenter - 150, top, 300, 20, Component.literal("用户身份"));
        identityBox.setValue("player@example.com");
        this.addRenderableWidget(identityBox);

        passphraseBox = new EditBox(this.font, widthCenter - 150, top + 26, 300, 20, Component.literal("密钥口令"));
        passphraseBox.setValue("forge-pass");
        this.addRenderableWidget(passphraseBox);

        inputBox = new EditBox(this.font, widthCenter - 150, top + 56, 300, 20, Component.literal("要加密/签名的内容"));
        inputBox.setValue("Hello Minecraft OpenPGP!");
        this.addRenderableWidget(inputBox);

        this.addRenderableWidget(new Button(widthCenter - 150, top + 90, 140, 20, Component.literal("生成密钥"), button -> {
            try {
                PgpManager.generateKeyPair(identityBox.getValue(), passphraseBox.getValue());
                statusText = "密钥已生成，公钥可通过 /pgp export public 查看。";
            } catch (Exception e) {
                statusText = "生成失败: " + e.getMessage();
            }
        }));

        this.addRenderableWidget(new Button(widthCenter + 10, top + 90, 140, 20, Component.literal("加密文本"), button -> {
            try {
                statusText = PgpManager.encrypt(inputBox.getValue());
            } catch (Exception e) {
                statusText = "加密失败: " + e.getMessage();
            }
        }));

        this.addRenderableWidget(new Button(widthCenter - 150, top + 120, 140, 20, Component.literal("签名文本"), button -> {
            try {
                statusText = PgpManager.sign(inputBox.getValue());
            } catch (Exception e) {
                statusText = "签名失败: " + e.getMessage();
            }
        }));

        this.addRenderableWidget(new Button(widthCenter + 10, top + 120, 140, 20, Component.literal("返回游戏"), button -> Minecraft.getInstance().setScreen(null)));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(poseStack);
        drawCenteredString(poseStack, this.font, this.title.getString(), this.width / 2, 20, 0xFFFFFF);
        drawString(poseStack, this.font, Component.literal("身份"), this.width / 2 - 150, this.height / 4 - 12, 0xAAAAAA);
        drawString(poseStack, this.font, Component.literal("口令"), this.width / 2 - 150, this.height / 4 + 14, 0xAAAAAA);
        drawString(poseStack, this.font, Component.literal("输入内容"), this.width / 2 - 150, this.height / 4 + 44, 0xAAAAAA);
        super.render(poseStack, mouseX, mouseY, partialTicks);
        drawCenteredString(poseStack, this.font, statusText, this.width / 2, this.height - 40, 0xFFCC66);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
