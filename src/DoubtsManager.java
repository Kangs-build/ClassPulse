import java.sql.*;
import java.util.*;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;

public class DoubtsManager {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
    "a", "an", "the", "is", "are", "am", "was", "were", "be", "been",
    "i", "you", "he", "she", "it", "we", "they", "my", "your", "in",
    "on", "at", "to", "of", "for", "and", "or", "but", "not", "no",
    "do", "does", "did", "don't", "doesn't", "didn't", "can", "cant",
    "can't", "about", "how", "what", "why", "when", "understand",
    "confused", "doubt", "please", "help", "with", "this", "that"
    ));

    public String generateDoubtId(String subject, String section) throws SQLException {
        Connection conn = Database.getConnection();
        String prefix = "CS-" + subject.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]+", "-") + "-" + section.toUpperCase(java.util.Locale.ROOT) + "-";
        try (PreparedStatement ps = conn.prepareStatement(
        "SELECT doubt_id FROM doubts WHERE doubt_id LIKE ?")) {
            ps.setString(1, prefix + "%");
            try (ResultSet rs = ps.executeQuery()) {

                int maxNumber = 0;
                while (rs.next()) {
                    String id = rs.getString("doubt_id");
                    try {
                        int num = Integer.parseInt(id.substring(prefix.length()));
                        if (num > maxNumber) maxNumber = num;
                    } catch (NumberFormatException e) {
                        // Ignore malformed IDs
                    }
                }
                return prefix + String.format("%03d", maxNumber + 1);
            }
        }
    }

    public Doubt addDoubt(String subject, String section, String rollNumber, String description) throws SQLException {
        String doubtId = generateDoubtId(subject, section);
        String postedTime = LocalDateTime.now().format(TIME_FORMAT);

        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(
        "INSERT INTO doubts (doubt_id, subject, section, roll_number, description, status, posted_time, priority, teacher_response, rejection_reason) " +
        "VALUES (?, ?, ?, ?, ?, 'Pending', ?, 'Normal', '', '')")) {
            ps.setString(1, doubtId);
            ps.setString(2, subject);
            ps.setString(3, section);
            ps.setString(4, rollNumber);
            ps.setString(5, description);
            ps.setString(6, postedTime);
            ps.executeUpdate();

            return new Doubt(doubtId, subject, section, rollNumber, description, "Pending", postedTime, "Normal", "", "", "");
        }
    }

    /**
     * Flips Pending/Normal doubts to Escalated in the DB if more than 2 hours have passed.
     */
    public void applyEscalation() throws SQLException {
        Connection conn = Database.getConnection();
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(
            "SELECT doubt_id, posted_time FROM doubts WHERE status = 'Pending' AND priority IN ('Normal', 'High')")) {

                LocalDateTime now = LocalDateTime.now();
                List<String> toEscalate = new ArrayList<>();
                while (rs.next()) {
                    try {
                        LocalDateTime posted = LocalDateTime.parse(rs.getString("posted_time"), TIME_FORMAT);
                        if (Duration.between(posted, now).compareTo(Duration.ofHours(2)) >= 0) {
                            toEscalate.add(rs.getString("doubt_id"));
                        }
                    } catch (Exception e) {
                        // Skip unparseable timestamps
                    }
                }

                if (!toEscalate.isEmpty()) {
                    try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE doubts SET priority = 'Escalated' WHERE doubt_id = ?")) {
                        for (String id : toEscalate) {
                            update.setString(1, id);
                            update.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    /**
     * Pending doubts for a subject, escalation applied first, sorted Escalated-first
     * then oldest-first.
     */
    public List<Doubt> getDoubtsForSubject(String subject) throws SQLException {
        applyEscalation();
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(
        "SELECT * FROM doubts WHERE subject = ? COLLATE NOCASE AND status = 'Pending' " +
        "ORDER BY CASE priority WHEN 'Urgent' THEN 0 WHEN 'Escalated' THEN 1 WHEN 'High' THEN 2 ELSE 3 END, upvote_count DESC, posted_time ASC")) {
            ps.setString(1, subject);
            try (ResultSet rs = ps.executeQuery()) {

                List<Doubt> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        }
    }

    public List<Doubt> getPendingForSection(String section) throws SQLException {
        applyEscalation();
        try(PreparedStatement ps=Database.getConnection().prepareStatement("SELECT * FROM doubts WHERE section=? COLLATE NOCASE AND status='Pending' ORDER BY upvote_count DESC, posted_time ASC")) {
            ps.setString(1,section);
            try(ResultSet rs=ps.executeQuery()) {List<Doubt> list=new ArrayList<>();while(rs.next()) list.add(mapRow(rs));return list;}
        }
    }

    public List<Doubt> getAllDoubtsForSubject(String subject) throws SQLException {
        applyEscalation();
        try (PreparedStatement ps = Database.getConnection().prepareStatement("SELECT * FROM doubts WHERE subject = ? COLLATE NOCASE")) {
            ps.setString(1, subject);
            try (ResultSet rs = ps.executeQuery()) {
                List<Doubt> list = new ArrayList<>(); while (rs.next()) list.add(mapRow(rs)); return list;
            }
        }
    }

    public List<Doubt> getDoubtsByRollNumber(String rollNumber) throws SQLException {
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(
        "SELECT * FROM doubts WHERE roll_number = ? ORDER BY posted_time DESC")) {
            ps.setString(1, rollNumber);
            try (ResultSet rs = ps.executeQuery()) {

                List<Doubt> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        }
    }

    public Doubt findById(String doubtId) throws SQLException {
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM doubts WHERE doubt_id = ?")) {
            ps.setString(1, doubtId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
                return null;
            }
        }
    }

    public boolean updateDoubtStatus(String doubtId, String newStatus, String responseOrReason, String fileName) throws SQLException {
        Connection conn = Database.getConnection();
        String column = newStatus.equals("Resolved") ? "teacher_response" : "rejection_reason";
        try (PreparedStatement ps = conn.prepareStatement(
        "UPDATE doubts SET status = ?, " + column + " = ?, response_file_name = ? WHERE doubt_id = ? AND status = 'Pending'")) {
            ps.setString(1, newStatus);
            ps.setString(2, responseOrReason);
            ps.setString(3, fileName == null ? "" : fileName);
            ps.setString(4, doubtId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<Doubt> findSimilarDoubts(String subject, String section, String description) throws SQLException {
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(
        "SELECT * FROM doubts WHERE subject = ? COLLATE NOCASE AND section = ? COLLATE NOCASE AND status = 'Pending'")) {
            ps.setString(1, subject);
            ps.setString(2, section);
            try (ResultSet rs = ps.executeQuery()) {

                Set<String> newWords = extractKeywords(description);
                List<Doubt> similar = new ArrayList<>();
                while (rs.next()) {
                    Doubt d = mapRow(rs);
                    Set<String> existingWords = extractKeywords(d.getDescription());
                    int overlap = 0;
                    for (String w : newWords) if (existingWords.contains(w)) overlap++;
                    if (overlap >= 2) similar.add(d);
                }
                return similar;
            }
        }
    }

    /**
     * Archive/FAQ now only shows doubts the teacher explicitly published - resolving
     * a doubt no longer auto-adds it. See publishToFaq().
     */
    public List<Doubt> getArchive() throws SQLException {
        Connection conn = Database.getConnection();
        try (Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery(
            "SELECT * FROM doubts WHERE status = 'Resolved' AND published_to_faq = 1 ORDER BY posted_time DESC")) {
                List<Doubt> results = new ArrayList<>();
                while (rs.next()) results.add(mapRow(rs));
                return results;
            }
        }
    }

    /**
     * Teacher explicitly publishes a resolved doubt to the class FAQ.
     */
    public boolean publishToFaq(String doubtId) throws SQLException {
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(
        "UPDATE doubts SET published_to_faq = 1 WHERE doubt_id = ? AND status = 'Resolved'")) {
            ps.setString(1, doubtId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Resolved doubts belonging to this teacher's subjects that are NOT yet published,
     * so the teacher has a list to choose from.
     */
    public List<Doubt> getUnpublishedResolved(List<String> subjects) throws SQLException {
        Connection conn = Database.getConnection();
        List<Doubt> results = new ArrayList<>();
        for (String subject : subjects) {
            try (PreparedStatement ps = conn.prepareStatement(
            "SELECT * FROM doubts WHERE subject = ? COLLATE NOCASE AND status = 'Resolved' AND published_to_faq = 0")) {
                ps.setString(1, subject);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) results.add(mapRow(rs));
                }
            }
        }
        return results;
    }

    /**
     * Logs a flagged (inappropriate) submission attempt. Stores only roll number,
     * timestamp, and confidence - NEVER the flagged text itself, to avoid creating
     * a record of exactly the content the student was told would never be stored.
     */
    public void logModerationEvent(String rollNumber, double confidence) throws SQLException {
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(
        "INSERT INTO moderation_log (roll_number, logged_time, confidence) VALUES (?, ?, ?)")) {
            ps.setString(1, rollNumber);
            ps.setString(2, LocalDateTime.now().format(TIME_FORMAT));
            ps.setDouble(3, confidence);
            ps.executeUpdate();
        }
    }

    private Set<String> extractKeywords(String text) {
        Set<String> words = new HashSet<>();
        String cleaned = text.toLowerCase().replaceAll("[^a-z0-9\\s]", "");
        for (String word : cleaned.split("\\s+")) {
            if (!word.isEmpty() && !STOP_WORDS.contains(word)) words.add(word);
        }
        return words;
    }

    public boolean upvoteDoubt(String doubtId, String rollNumber) throws SQLException {
        Doubt d = findById(doubtId);
        if (d == null || !"Pending".equals(d.getStatus()) || d.getRollNumber().equals(rollNumber)) return false;
        Connection conn = Database.getConnection();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO doubt_votes(doubt_id,roll_number) VALUES (?,?)")) {
                ps.setString(1,doubtId); ps.setString(2,rollNumber);
                if(ps.executeUpdate()==0) {conn.rollback(); return false;}
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE doubts SET upvote_count=1+(SELECT COUNT(*) FROM doubt_votes WHERE doubt_id=?), priority=CASE WHEN 1+(SELECT COUNT(*) FROM doubt_votes WHERE doubt_id=?)>=5 THEN 'Urgent' WHEN priority='Escalated' THEN priority WHEN 1+(SELECT COUNT(*) FROM doubt_votes WHERE doubt_id=?)>=3 THEN 'High' ELSE priority END WHERE doubt_id=?")) {
                for(int i=1;i<=4;i++) ps.setString(i,doubtId); ps.executeUpdate();
            }
            conn.commit(); return true;
        } catch(SQLException e) {conn.rollback(); throw e;}
        finally {conn.setAutoCommit(true);}
    }

    public boolean logLivePulse(String subject, String section, String rollNumber) throws SQLException {
        String cutoff=LocalDateTime.now().minusMinutes(30).format(TIME_FORMAT);
        try(PreparedStatement ps=Database.getConnection().prepareStatement("INSERT INTO live_pulse(subject,section,timestamp,roll_number) SELECT ?,?,?,? WHERE NOT EXISTS(SELECT 1 FROM live_pulse WHERE subject=? COLLATE NOCASE AND roll_number=? AND timestamp>=?)")) {
            ps.setString(1,subject); ps.setString(2,section); ps.setString(3,LocalDateTime.now().format(TIME_FORMAT)); ps.setString(4,rollNumber); ps.setString(5,subject); ps.setString(6,rollNumber); ps.setString(7,cutoff);
            return ps.executeUpdate()>0;
        }
    }

    public Map<String, Integer> getLivePulseStats(List<String> subjects) throws SQLException {
        Connection conn = Database.getConnection();
        Map<String, Integer> stats = new HashMap<>();
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        String cutoffStr = cutoff.format(TIME_FORMAT);

        for (String subject : subjects) {
            try (PreparedStatement ps = conn.prepareStatement(
            "SELECT COUNT(*) FROM live_pulse WHERE subject = ? COLLATE NOCASE AND timestamp >= ?")) {
                ps.setString(1, subject);
                ps.setString(2, cutoffStr);
                try (ResultSet rs = ps.executeQuery()) {
                    int count = rs.next() ? rs.getInt(1) : 0;
                    stats.put(subject, count);
                }
            }
        }
        return stats;
    }

    public Map<String,Integer> getLivePulseStats(Teacher teacher) throws SQLException {
        Map<String,Integer> stats=new HashMap<>();
        for(String subject:teacher.getSubjectsHandled()) {
            int total=0;
            for(String section:teacher.getAssignedSections()) {
                try(PreparedStatement ps=Database.getConnection().prepareStatement("SELECT COUNT(*) FROM live_pulse WHERE subject=? COLLATE NOCASE AND section=? COLLATE NOCASE AND timestamp>=?")) {
                    ps.setString(1,subject);ps.setString(2,section);ps.setString(3,LocalDateTime.now().minusMinutes(30).format(TIME_FORMAT));
                    try(ResultSet rs=ps.executeQuery()) {if(rs.next()) total+=rs.getInt(1);}
                }
            }
            stats.put(subject,total);
        }
        return stats;
    }
    public boolean addPeerAnswer(String doubtId, String answerText) throws SQLException {
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(
        "INSERT INTO peer_answers (doubt_id, answer_text, posted_time) VALUES (?, ?, ?)")) {
            ps.setString(1, doubtId);
            ps.setString(2, answerText);
            ps.setString(3, LocalDateTime.now().format(TIME_FORMAT));
            return ps.executeUpdate() > 0;
        }
    }

    public List<Map<String, String>> getPeerAnswers(String doubtId) throws SQLException {
        Connection conn = Database.getConnection();
        try (PreparedStatement ps = conn.prepareStatement(
        "SELECT * FROM peer_answers WHERE doubt_id = ? ORDER BY id ASC")) {
            ps.setString(1, doubtId);
            try (ResultSet rs = ps.executeQuery()) {

                List<Map<String, String>> answers = new ArrayList<>();
                while (rs.next()) {
                    Map<String, String> item = new HashMap<>();
                    item.put("id", String.valueOf(rs.getInt("id")));
                    item.put("doubtId", rs.getString("doubt_id"));
                    item.put("answerText", rs.getString("answer_text"));
                    item.put("postedTime", rs.getString("posted_time"));
                    answers.add(item);
                }
                return answers;
            }
        }
    }

    public List<Doubt> searchDoubts(String query, String subjectFilter) throws SQLException {
        String q = "%" + (query == null ? "" : query.trim()).replace("!","!!").replace("%","!%").replace("_","!_") + "%";
        boolean filter=subjectFilter!=null && !subjectFilter.isBlank() && !"ALL".equalsIgnoreCase(subjectFilter);
        String sql="SELECT * FROM doubts WHERE status='Resolved' AND published_to_faq=1 AND (description LIKE ? ESCAPE '!' OR teacher_response LIKE ? ESCAPE '!' OR subject LIKE ? ESCAPE '!')" + (filter?" AND subject = ? COLLATE NOCASE":"") + " ORDER BY upvote_count DESC, posted_time DESC";
        try(PreparedStatement ps=Database.getConnection().prepareStatement(sql)) {
            ps.setString(1,q); ps.setString(2,q); ps.setString(3,q); if(filter) ps.setString(4,Validation.subject(subjectFilter));
            try(ResultSet rs=ps.executeQuery()) {List<Doubt> list=new ArrayList<>(); while(rs.next()) list.add(mapRow(rs)); return list;}
        }
    }

    private Doubt mapRow(ResultSet rs) throws SQLException {
        int upvotes = 1;
        try {
            upvotes = rs.getInt("upvote_count");
        } catch (SQLException ignored) {}
        return new Doubt(
        rs.getString("doubt_id"),
        rs.getString("subject"),
        rs.getString("section"),
        rs.getString("roll_number"),
        rs.getString("description"),
        rs.getString("status"),
        rs.getString("posted_time"),
        rs.getString("priority"),
        rs.getString("teacher_response"),
        rs.getString("rejection_reason"),
        rs.getString("response_file_name"),
        upvotes
        );
    }
}
