package com.example.mymod;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * 进度反馈抽象：加密、解密、签名、验签、从密钥服务器下载时向玩家显示进度条。
 * 形如：[██████░░░░░░░░░░░░░░] 30% 加密中
 */
public interface PgpProgress {

    /** 更新进度，percent 取值 0-100。 */
    void update(int percent, String stage);

    /** 完成提示（可覆盖）。 */
    default void done(String message) {
    }

    /** 空实现，供不需要进度的地方使用。 */
    PgpProgress NOOP = (percent, stage) -> {
    };

    /** 生成 20 格的字符进度条。 */
    static String bar(int percent) {
        int p = Math.max(0, Math.min(100, percent));
        int filled = Math.round(p * 20.0f / 100.0f);
        StringBuilder sb = new StringBuilder(20);
        for (int i = 0; i < 20; i++) {
            sb.append(i < filled ? '\u2588' : '\u2591');
        }
        return sb.toString();
    }

    /** 客户端动作栏进度条（不刷屏聊天框）。 */
    final class ActionBar implements PgpProgress {
        private final String label;
        private long lastUpdate = 0L;

        public ActionBar(String label) {
            this.label = label;
        }

        @Override
        public void update(int percent, String stage) {
            long now = System.currentTimeMillis();
            if (now - lastUpdate < 40L && percent < 100) {
                return;
            }
            lastUpdate = now;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return;
            }
            String text = "[" + bar(percent) + "] " + percent + "% " + (stage == null ? label : stage);
            mc.player.displayClientMessage(Component.literal(text), true);
        }

        @Override
        public void done(String message) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal(message), true);
            }
        }
    }

    /** 命令来源的进度：输出到聊天栏，做节流避免刷屏。 */
    final class Chat implements PgpProgress {
        private final CommandSourceStack source;
        private final String label;
        private long lastUpdate = 0L;

        public Chat(CommandSourceStack source, String label) {
            this.source = source;
            this.label = label;
        }

        @Override
        public void update(int percent, String stage) {
            long now = System.currentTimeMillis();
            if (now - lastUpdate < 200L && percent < 100) {
                return;
            }
            lastUpdate = now;
            String text = "[" + bar(percent) + "] " + percent + "% " + (stage == null ? label : stage);
            source.sendSuccess(() -> Component.literal(text), false);
        }
    }
}
