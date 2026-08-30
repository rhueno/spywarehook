package nf.tr;

import java.nio.file.Files;
import java.nio.file.Path;

final class CfgLoad {

    private CfgLoad() {}

    static Config read(Path path) throws Exception {
        Config c = new Config();
        if (path == null || !Files.isRegularFile(path)) {
            c.include.add("noface/**");
            c.exclude.add("noface/browsers/Main");
            c.exclude.add("noface/browsers/win/**");
            c.str = true;
            c.scatter = true;
            c.cff = false;
            c.guard = true;
            return c;
        }
        String json = Files.readString(path);
        c.rename = bool(json, "rename", true);
        c.pkg = str(json, "pkg", "nf/x").replace('.', '/');
        c.str = bool(json, "str", true);
        c.scatter = bool(json, "scatter", true);
        c.cff = bool(json, "cff", false);
        c.guard = bool(json, "guard", true);
        list(json, "include", c.include);
        list(json, "exclude", c.exclude);
        if (!c.exclude.contains("noface/browsers/Main")) c.exclude.add("noface/browsers/Main");
        if (!c.exclude.contains("noface/browsers/win/**")) c.exclude.add("noface/browsers/win/**");
        c.entry = str(json, "entry", "").replace('.', '/');
        if (c.include.isEmpty()) c.include.add("noface/**");
        return c;
    }

    private static boolean bool(String j, String k, boolean d) {
        int i = j.indexOf('"' + k + '"');
        if (i < 0) return d;
        return j.indexOf("true", i) > i && j.indexOf("true", i) < i + 40;
    }

    private static String str(String j, String k, String d) {
        int i = j.indexOf('"' + k + '"');
        if (i < 0) return d;
        int q = j.indexOf('"', j.indexOf(':', i) + 1);
        int e = j.indexOf('"', q + 1);
        if (q < 0 || e < 0) return d;
        return j.substring(q + 1, e);
    }

    private static void list(String j, String k, java.util.List<String> out) {
        int i = j.indexOf('"' + k + '"');
        if (i < 0) return;
        int a = j.indexOf('[', i);
        int b = j.indexOf(']', a);
        if (a < 0 || b < 0) return;
        String block = j.substring(a + 1, b);
        int p = 0;
        while (p < block.length()) {
            int q = block.indexOf('"', p);
            if (q < 0) break;
            int e = block.indexOf('"', q + 1);
            if (e < 0) break;
            out.add(block.substring(q + 1, e));
            p = e + 1;
        }
    }
}
