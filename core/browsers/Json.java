package noface.browsers;

public final class Json {

    public static String field(String json, String name) {
        if (json == null || name == null) return null;
        String needle = "\"" + name + "\"";
        int idx = json.indexOf(needle);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + needle.length());
        if (colon < 0) return null;
        int q1 = json.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = q1 + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') break;
            if (c == '\\' && i + 1 < json.length()) sb.append(json.charAt(++i));
            else sb.append(c);
        }
        return sb.toString();
    }

    private Json() {}
}
