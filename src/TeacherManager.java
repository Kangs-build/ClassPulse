import java.sql.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TeacherManager {

    private static final Set<String> ALLOWED_SUBJECTS = Validation.SUBJECTS;

    public Teacher find(String teacherId) throws SQLException {
        Connection conn = Database.getConnection();
        try (PreparedStatement find = conn.prepareStatement("SELECT * FROM teachers WHERE teacher_id = ?")) {
            find.setString(1, teacherId);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) return mapRow(rs);
                return null;
            }
        }
    }

    public Teacher login(String teacherId, String password) throws SQLException {
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM teachers WHERE teacher_id = ?")) {
            ps.setString(1, teacherId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                if (!PasswordUtil.verify(password, rs.getString("password_hash"))) return null;
                return mapRow(rs);
            }
        }
    }

    public static final String FACULTY_SECRET_KEY = System.getenv("CLASSPULSE_FACULTY_KEY");

    public Teacher register(String teacherId, String name, String subjectsCsv, String password, String secretKey) throws SQLException {
        if (FACULTY_SECRET_KEY == null || FACULTY_SECRET_KEY.isBlank() || secretKey == null || !FACULTY_SECRET_KEY.equals(secretKey.trim())) {
            throw new SecurityException("Invalid Faculty Secret Key. Teacher registration is restricted to authorized faculty.");
        }

        teacherId = Validation.identifier(teacherId, "Teacher ID");
        name = Validation.text(name, "Name", 100);
        Validation.password(password);
        subjectsCsv = Validation.text(subjectsCsv, "Subjects", 300);
        String[] parts = subjectsCsv.split(",", -1);
        List<String> validatedSubjects = new ArrayList<>();
        for (String raw : parts) {
            String trimmed = Validation.subject(raw);
            if (!ALLOWED_SUBJECTS.contains(trimmed)) {
                throw new IllegalArgumentException("Invalid subject '" + trimmed + "'. Allowed subjects: " + String.join(", ", ALLOWED_SUBJECTS));
            }
            if (!validatedSubjects.contains(trimmed)) {
                validatedSubjects.add(trimmed);
            }
        }
        String cleanSubjectsCsv = String.join(",", validatedSubjects);

        Connection conn = Database.getConnection();
        try (PreparedStatement insert = conn.prepareStatement(
        "INSERT INTO teachers (teacher_id, name, subjects, password_hash) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, teacherId);
            insert.setString(2, name);
            insert.setString(3, cleanSubjectsCsv);
            insert.setString(4, PasswordUtil.hash(password));
            insert.executeUpdate();
            return new Teacher(teacherId, name, validatedSubjects);
        }
    }

    public Teacher register(String teacherId, String name, String subjectsCsv, String password) throws SQLException {
        return register(teacherId, name, subjectsCsv, password, FACULTY_SECRET_KEY);
    }

    public boolean updatePassword(String teacherId, String oldPassword, String newPassword) throws SQLException {
        Validation.password(newPassword);
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM teachers WHERE teacher_id = ?")) {
            ps.setString(1, teacherId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                if (!PasswordUtil.verify(oldPassword, rs.getString("password_hash"))) return false;

                try (PreparedStatement update = conn.prepareStatement("UPDATE teachers SET password_hash = ? WHERE teacher_id = ?")) {
                    update.setString(1, PasswordUtil.hash(newPassword));
                    update.setString(2, teacherId);
                    update.executeUpdate();
                    return true;
                }

            }
        }
    }

    private Teacher mapRow(ResultSet rs) throws SQLException {
        String subjectsRaw = rs.getString("subjects");
        List<String> subjects = subjectsRaw.isEmpty()
        ? new ArrayList<>()
        : new ArrayList<>(Arrays.asList(subjectsRaw.split(",")));
        return new Teacher(rs.getString("teacher_id"), rs.getString("name"), subjects, CollegeAccounts.sectionsForTeacher(rs.getString("teacher_id")));
    }
}
