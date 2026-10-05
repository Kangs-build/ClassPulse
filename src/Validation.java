import java.util.Set;
public final class Validation {
    public static final Set<String> SUBJECTS = Set.of("ML", "DAA", "Java", "Discrete Mathematics", "Aptitude", "Competitive Programming", "Computer Architecture", "College & Career", "General Academics");
    public static String subject(String value) {
        for (String allowed : SUBJECTS) if (value != null && allowed.equalsIgnoreCase(value.trim())) return allowed;
        throw new IllegalArgumentException("Choose a supported subject");
    }
    public static String text(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(field + " is required (maximum " + max + " characters)");
        return value.trim();
    }
    public static String identifier(String value, String field) {
        value = text(value, field, 64);
        if (!value.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException(field + " may contain letters, numbers, underscores and hyphens only");
        return value;
    }
    public static void password(String value) {
        if (value == null || value.length() < 8 || value.length() > 128 || value.isBlank()) throw new IllegalArgumentException("Password must contain 8 to 128 characters");
    }
}
