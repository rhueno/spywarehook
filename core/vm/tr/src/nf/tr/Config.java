package nf.tr;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class Config {

    boolean rename = true;
    String pkg = "nf/x";
    boolean str = true;
    boolean scatter = true;
    boolean cff = false;
    boolean guard = true;
    String entry = "";
    List<String> include = new ArrayList<>();
    List<String> exclude = new ArrayList<>();

    List<Pattern> includePatterns() {
        return glob(include);
    }

    List<Pattern> excludePatterns() {
        return glob(exclude);
    }

    private static List<Pattern> glob(List<String> items) {
        List<Pattern> out = new ArrayList<>();
        for (String g : items) {
            String n = g.replace('/', '.');
            StringBuilder sb = new StringBuilder("^");
            for (int i = 0; i < n.length(); i++) {
                char c = n.charAt(i);
                if (c == '*') {
                    if (i + 1 < n.length() && n.charAt(i + 1) == '*') {
                        sb.append(".*");
                        i++;
                    } else {
                        sb.append("[^.]*");
                    }
                } else if (c == '?') {
                    sb.append("[^.]");
                } else if (".[](){}+^$|\\".indexOf(c) >= 0) {
                    sb.append('\\').append(c);
                } else {
                    sb.append(c);
                }
            }
            sb.append('$');
            out.add(Pattern.compile(sb.toString()));
        }
        return out;
    }
}
