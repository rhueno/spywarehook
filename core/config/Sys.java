package noface.config;

public final class Sys {

    public static String pc() {
        String h = System.getenv("COMPUTERNAME");
        if (h == null || h.isBlank()) h = System.getenv("HOSTNAME");
        return h == null || h.isBlank() ? "PC" : h;
    }

    public static String os() {
        String name = System.getProperty("os.name", "");
        if (!name.toLowerCase().contains("win")) return name;
        try {
            double v = Double.parseDouble(System.getProperty("os.version", "10"));
            if (v >= 10) return "Windows 10-11";
            return "Windows " + System.getProperty("os.version", "");
        } catch (Exception e) {
            return "Windows 10-11";
        }
    }

    private Sys() {}
}
