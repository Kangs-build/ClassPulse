import java.sql.*;

/**
 * Handles the SQLite database connection and schema initialization for ClassPulse.
 * A single shared connection is used with synchronized access, since SQLite handles
 * one writer at a time - this keeps the web server's concurrent requests safe
 * without needing a full connection pool at this project's scale.
 */
public class Database {
    private static final String DB_URL = "jdbc:sqlite:" + System.getProperty("classpulse.db", "data/classpulse.db");
    private static Connection connection;

    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                throw new SQLException("SQLite JDBC driver not found on classpath", e);
            }
            connection = DriverManager.getConnection(DB_URL);
            // Enforce foreign key constraints (off by default in SQLite)
            try (Statement pragma=connection.createStatement()) {pragma.execute("PRAGMA foreign_keys = ON"); pragma.execute("PRAGMA busy_timeout = 5000");}
        }
        return connection;
    }

    /**
     * Creates all tables if they don't already exist. Safe to call every startup.
     */
    public static synchronized void initSchema() throws SQLException {
        try {
            java.nio.file.Path db = java.nio.file.Path.of(System.getProperty("classpulse.db", "data/classpulse.db")).toAbsolutePath();
            java.nio.file.Files.createDirectories(db.getParent());
        } catch (java.io.IOException e) { throw new SQLException("Cannot create data directory", e); }
        Connection conn = getConnection();
        try (Statement stmt = conn.createStatement()) {

            stmt.execute("""
            CREATE TABLE IF NOT EXISTS students (
                roll_number TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                section TEXT NOT NULL,
                password_hash TEXT NOT NULL
            )
        """);

            stmt.execute("""
            CREATE TABLE IF NOT EXISTS teachers (
                teacher_id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                subjects TEXT NOT NULL,
                password_hash TEXT NOT NULL
            )
        """);

            stmt.execute("""
            CREATE TABLE IF NOT EXISTS doubts (
                doubt_id TEXT PRIMARY KEY,
                subject TEXT NOT NULL,
                section TEXT NOT NULL,
                roll_number TEXT NOT NULL,
                description TEXT NOT NULL,
                status TEXT NOT NULL,
                posted_time TEXT NOT NULL,
                priority TEXT NOT NULL,
                teacher_response TEXT DEFAULT '',
                rejection_reason TEXT DEFAULT '',
                published_to_faq INTEGER DEFAULT 0,
                response_file_name TEXT DEFAULT '',
                upvote_count INTEGER DEFAULT 1,
                FOREIGN KEY (roll_number) REFERENCES students(roll_number)
            )
        """);

            addColumnIfMissing(conn, "doubts", "upvote_count", "INTEGER DEFAULT 1");

            stmt.execute("""
            CREATE TABLE IF NOT EXISTS moderation_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                roll_number TEXT NOT NULL,
                logged_time TEXT NOT NULL,
                confidence REAL NOT NULL
            )
        """);

            stmt.execute("""
            CREATE TABLE IF NOT EXISTS live_pulse (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject TEXT NOT NULL,
                section TEXT NOT NULL,
                timestamp TEXT NOT NULL
            )
        """);

            stmt.execute("""
            CREATE TABLE IF NOT EXISTS peer_answers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                doubt_id TEXT NOT NULL,
                answer_text TEXT NOT NULL,
                posted_time TEXT NOT NULL,
                FOREIGN KEY (doubt_id) REFERENCES doubts(doubt_id)
            )
        """);

            stmt.execute("CREATE TABLE IF NOT EXISTS doubt_votes (doubt_id TEXT NOT NULL REFERENCES doubts(doubt_id), roll_number TEXT NOT NULL REFERENCES students(roll_number), PRIMARY KEY(doubt_id, roll_number))");
            // Old totals had no voter provenance. Reset them to the author's implicit vote.
            stmt.execute("UPDATE doubts SET upvote_count = 1 + (SELECT COUNT(*) FROM doubt_votes v WHERE v.doubt_id = doubts.doubt_id), priority = CASE WHEN priority IN ('High','Urgent') THEN 'Normal' ELSE priority END WHERE NOT EXISTS (SELECT 1 FROM doubt_votes v WHERE v.doubt_id = doubts.doubt_id)");
            addColumnIfMissing(conn, "live_pulse", "roll_number", "TEXT");
            stmt.execute("CREATE INDEX IF NOT EXISTS doubts_subject_status ON doubts(subject, status)");
        }
        CollegeAccounts.initSchema(conn);
        System.out.println("Database schema ready.");
    }
    private static void addColumnIfMissing(Connection conn, String table, String column, String type) throws SQLException {
        boolean found=false;
        try(Statement stmt=conn.createStatement();ResultSet rs=stmt.executeQuery("PRAGMA table_info("+table+")")) {
            while(rs.next()) if(column.equals(rs.getString("name"))) found=true;
        }
        if(!found) try(Statement stmt=conn.createStatement()) {stmt.execute("ALTER TABLE "+table+" ADD COLUMN "+column+" "+type);}
    }
}
