import java.sql.*;

public class StudentManager {

    public Student find(String rollNumber) throws SQLException {
        Connection conn = Database.getConnection();
        try (PreparedStatement find = conn.prepareStatement("SELECT * FROM students WHERE roll_number = ?")) {
            find.setString(1, rollNumber);
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    return new Student(rs.getString("roll_number"), rs.getString("name"), rs.getString("section"));
                }
                return null;
            }
        }
    }

    public Student register(String rollNumber, String name, String section, String password) throws SQLException {
        rollNumber = Validation.identifier(rollNumber, "Roll number");
        name = Validation.text(name, "Name", 100);
        section = Validation.identifier(section, "Section").toUpperCase(java.util.Locale.ROOT);
        Validation.password(password);
        Connection conn = Database.getConnection();
        try (PreparedStatement insert = conn.prepareStatement(
        "INSERT INTO students (roll_number, name, section, password_hash) VALUES (?, ?, ?, ?)")) {
            insert.setString(1, rollNumber);
            insert.setString(2, name);
            insert.setString(3, section);
            insert.setString(4, PasswordUtil.hash(password));
            insert.executeUpdate();
            return new Student(rollNumber, name, section);
        }
    }

    /**
     * Returns the student if rollNumber exists and password matches, null otherwise.
     */
    public Student login(String rollNumber, String password) throws SQLException {
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM students WHERE roll_number = ?")) {
            ps.setString(1, rollNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                if (!PasswordUtil.verify(password, rs.getString("password_hash"))) return null;
                return new Student(rs.getString("roll_number"), rs.getString("name"), rs.getString("section"));
            }
        }
    }

    public boolean updatePassword(String rollNumber, String oldPassword, String newPassword) throws SQLException {
        Validation.password(newPassword);
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM students WHERE roll_number = ?")) {
            ps.setString(1, rollNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                if (!PasswordUtil.verify(oldPassword, rs.getString("password_hash"))) return false;

                try (PreparedStatement update = conn.prepareStatement("UPDATE students SET password_hash = ? WHERE roll_number = ?")) {
                    update.setString(1, PasswordUtil.hash(newPassword));
                    update.setString(2, rollNumber);
                    update.executeUpdate();
                    return true;
                }

            }
        }
    }
}
