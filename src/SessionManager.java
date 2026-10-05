import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages in-memory user authentication sessions for ClassPulse.
 * Thread-safe using ConcurrentHashMap to handle concurrent HTTP requests safely.
 */
public class SessionManager {

    public static class UserSession {
        private final String role; // "student" or "teacher"
        private final String userId; // roll_number or teacher_id
        private final long createdAt;

        public UserSession(String role, String userId) {
            this.role = role;
            this.userId = userId;
            this.createdAt = System.currentTimeMillis();
        }

        public String getRole() {
            return role;
        }

        public String getUserId() {
            return userId;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }

    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    /**
     * Creates a new session token for the given user and role.
     */
    public String createSession(String role, String userId) {
        sessions.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().getCreatedAt() >= 8 * 60 * 60 * 1000L);
        String token = UUID.randomUUID().toString();
        sessions.put(token, new UserSession(role, userId));
        return token;
    }

    /**
     * Retrieves an active session by token, or null if invalid.
     */
    public UserSession getSession(String token) {
        if (token == null || token.isBlank()) return null;
        UserSession session = sessions.get(token);
        if (session != null && System.currentTimeMillis() - session.getCreatedAt() >= 8 * 60 * 60 * 1000L) {
            sessions.remove(token); return null;
        }
        return session;
    }

    /**
     * Invalidates a session (e.g., on logout).
     */
    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /**
     * Extracts and validates the session token from the X-Session-Token header.
     */
    public UserSession getAuthenticatedSession(HttpExchange ex) {
        String token = ex.getRequestHeaders().getFirst("X-Session-Token");
        UserSession session=getSession(token);
        if(session!=null && !CollegeAccounts.active(session.getRole(),session.getUserId())) {invalidate(token);return null;}
        return session;
    }

    /**
     * Enforces a valid student session on the request.
     * If invalid or missing, sends a 401/403 response and returns null.
     */
    public UserSession requireStudentSession(HttpExchange ex) throws IOException {
        UserSession session = getAuthenticatedSession(ex);
        if (session == null) {
            sendJsonError(ex, 401, "Authentication required. Please log in.");
            return null;
        }
        if (!"student".equals(session.getRole())) {
            sendJsonError(ex, 403, "Access denied. Student session required.");
            return null;
        }
        return session;
    }

    /**
     * Enforces a valid teacher session on the request.
     * If invalid or missing, sends a 401/403 response and returns null.
     */
    public UserSession requireTeacherSession(HttpExchange ex) throws IOException {
        UserSession session = getAuthenticatedSession(ex);
        if (session == null) {
            sendJsonError(ex, 401, "Authentication required. Please log in.");
            return null;
        }
        if (!"teacher".equals(session.getRole())) {
            sendJsonError(ex, 403, "Access denied. Teacher session required.");
            return null;
        }
        return session;
    }

    /**
     * Enforces any valid user session on the request.
     * If invalid or missing, sends a 401 response and returns null.
     */
    public UserSession requireAnySession(HttpExchange ex) throws IOException {
        UserSession session = getAuthenticatedSession(ex);
        if (session == null) {
            sendJsonError(ex, 401, "Authentication required. Please log in.");
            return null;
        }
        return session;
    }

    public UserSession requireAdminSession(HttpExchange ex) throws IOException {
        UserSession s=requireAnySession(ex); if(s==null) return null;
        if(!"admin".equals(s.getRole())) {sendJsonError(ex,403,"Administrator access required");return null;} return s;
    }
    public void invalidateOtherSessions(String role, String userId, String keepToken) {
        sessions.entrySet().removeIf(e -> e.getValue().getRole().equals(role) && e.getValue().getUserId().equals(userId) && !e.getKey().equals(keepToken));
    }

    private void sendJsonError(HttpExchange ex, int statusCode, String message) throws IOException {
        byte[] bytes = ("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}").getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
