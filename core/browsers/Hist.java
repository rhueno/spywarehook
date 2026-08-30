package noface.browsers;

import noface.config.S;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class Hist {

    public record Entry(String url, String title, int visits, long when) {}

    private static String sql() {
        return S.e("SELECT url, title, visit_count, last_visit_time FROM urls ")
                + S.e("WHERE url IS NOT NULL AND length(url) > 0 ")
                + S.e("ORDER BY last_visit_time DESC LIMIT 2000");
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
            String url = rs.getString(1);
            String title = rs.getString(2);
            int visits = rs.getInt(3);
            long t = rs.getLong(4);
            if (url == null || url.isBlank()) return;
            out.add(new Entry(url, title == null ? "" : title, visits, t));
        } catch (Exception ignored) {
        }
    }

    public static String fmt(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            sb.append(e.url()).append('\t').append(e.title()).append('\t')
                    .append(e.visits()).append('\n');
        }
        return sb.toString();
    }

    private Hist() {}
}
