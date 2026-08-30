package noface.browsers;

import org.sqlite.SQLiteConnection;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Consumer;

public final class Db {

    static {
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) {}
    }

    public static void query(Path dbPath, String sql, Consumer<ResultSet> row) {
        if (dbPath == null) return;

        Path snap = Copy.snap(dbPath);
        if (snap != null) {
            try {
                if (open(snap, sql, row, false) > 0) return;
            } finally {
                Copy.wipe(snap);
            }
        }

        if (open(dbPath, sql, row, false) > 0) return;
        if (open(dbPath, sql, row, true) > 0) return;

        byte[] raw = Copy.bytes(dbPath);
        if (raw != null) query(raw, sql, row);
    }

    private static int open(Path dbPath, String sql, Consumer<ResultSet> row, boolean frozen) {
        String url = "jdbc:sqlite:file:" + dbPath.toAbsolutePath().toString().replace('\\', '/')
                + (frozen ? "?mode=ro&immutable=1" : "?mode=ro");
        try (Connection conn = DriverManager.getConnection(url);
             java.sql.Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            int n = 0;
            while (rs.next()) {
                n++;
                try { row.accept(rs); } catch (Exception ignored) {}
            }
            return n;
        } catch (SQLException ignored) {
            return 0;
        }
    }

    public static void query(byte[] raw, String sql, Consumer<ResultSet> row) {
        if (raw == null || raw.length == 0) return;
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        SQLiteDataSource ds = new SQLiteDataSource(config);
        ds.setUrl("jdbc:sqlite:");
        try (Connection conn = ds.getConnection()) {
            SQLiteConnection sc = conn.unwrap(SQLiteConnection.class);
            sc.deserialize("main", raw);
            try (java.sql.Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try { row.accept(rs); } catch (Exception ignored) {}
                }
            }
        } catch (SQLException ignored) {}
    }

    private Db() {}
}
