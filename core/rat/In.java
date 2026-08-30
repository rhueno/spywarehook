package noface.rat;

import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public final class In {

    private final Robot bot;
    private final Cap cap;

    public In(Robot bot, Cap cap) {
        this.bot = bot;
        this.cap = cap;
    }

    public void move(double nx, double ny) {
        int x = (int) Math.round(nx * cap.width());
        int y = (int) Math.round(ny * cap.height());
        bot.mouseMove(clamp(x, 0, cap.width() - 1), clamp(y, 0, cap.height() - 1));
    }

    public void click(int btn, boolean down) {
        int m = switch (btn) {
            case 2 -> InputEvent.BUTTON2_DOWN_MASK;
            case 3 -> InputEvent.BUTTON3_DOWN_MASK;
            default -> InputEvent.BUTTON1_DOWN_MASK;
        };
        if (down) bot.mousePress(m);
        else bot.mouseRelease(m);
    }

    public void wheel(int amt) {
        bot.mouseWheel(amt);
    }

    public void key(int code, boolean down) {
        try {
            if (down) bot.keyPress(code);
            else bot.keyRelease(code);
        } catch (Exception ignored) {
        }
    }

    public void type(String text) {
        if (text == null) return;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            try {
                int code = KeyEvent.getExtendedKeyCodeForChar(c);
                if (code == KeyEvent.VK_UNDEFINED) continue;
                boolean shift = Character.isUpperCase(c) || "~!@#$%^&*()_+{}|:\"<>?".indexOf(c) >= 0;
                if (shift) bot.keyPress(KeyEvent.VK_SHIFT);
                bot.keyPress(code);
                bot.keyRelease(code);
                if (shift) bot.keyRelease(KeyEvent.VK_SHIFT);
            } catch (Exception ignored) {
            }
        }
    }

    private static int clamp(int v, int a, int b) {
        return Math.max(a, Math.min(b, v));
    }
}
