/**
 * Minimal JSON string-building helper. No external JSON library is used, consistent
 * with the project's "no external frameworks" approach - this only escapes strings
 * safely for output, it does not parse incoming JSON (see SimpleJsonParser for that).
 */
public class JsonUtil {
    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public static String field(String key, String value) {
        return "\"" + escape(key) + "\":\"" + escape(value) + "\"";
    }

    public static String field(String key, boolean value) {
        return "\"" + escape(key) + "\":" + value;
    }

    public static String field(String key, double value) {
        return "\"" + escape(key) + "\":" + value;
    }

    public static String field(String key, int value) {
        return "\"" + escape(key) + "\":" + value;
    }

    public static String error(String message) {
        return "{" + field("error", message) + "}";
    }
}
