import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ClassPulse web server entry point. Uses Java's built-in HttpServer (com.sun.net.httpserver)
 * - no Spring/framework dependency, consistent with the project's original "no external
 * frameworks" design.
 */
public class ClassPulseServer {
    private static final int PORT = Integer.getInteger("classpulse.port", 8080);
    private static final Object REQUEST_LOCK = new Object();
    private static final StudentManager studentManager = new StudentManager();
    private static final TeacherManager teacherManager = new TeacherManager();
    private static final DoubtsManager doubtsManager = new DoubtsManager();
    private static final MLClient mlClient = new MLClient();
    private static final CollegeAccounts accounts = new CollegeAccounts();
    private static final SessionManager sessionManager = new SessionManager();

    public static void main(String[] args) throws Exception {
        Database.initSchema();

        HttpServer server = HttpServer.create(new InetSocketAddress(System.getProperty("classpulse.bind", "127.0.0.1"), PORT), 0);

        server.createContext("/api/student/login", guarded("/api/student/login", ClassPulseServer::handleStudentLogin));
        server.createContext("/api/student/doubt", guarded("/api/student/doubt", ClassPulseServer::handlePostDoubt));
        server.createContext("/api/student/mydoubts", guarded("/api/student/mydoubts", ClassPulseServer::handleMyDoubts));
        server.createContext("/api/student/classdoubts", guarded("/api/student/classdoubts", ClassPulseServer::handleClassDoubts));
        server.createContext("/api/teacher/login", guarded("/api/teacher/login", ClassPulseServer::handleTeacherLogin));
        server.createContext("/api/teacher/pending", guarded("/api/teacher/pending", ClassPulseServer::handleTeacherPending));
        server.createContext("/api/teacher/resolve", guarded("/api/teacher/resolve", ClassPulseServer::handleTeacherResolve));
        server.createContext("/api/archive", guarded("/api/archive", ClassPulseServer::handleArchive));
        server.createContext("/api/teacher/unpublished", guarded("/api/teacher/unpublished", ClassPulseServer::handleUnpublished));
        server.createContext("/api/teacher/publish", guarded("/api/teacher/publish", ClassPulseServer::handlePublishToFaq));
        server.createContext("/api/user/password", guarded("/api/user/password", ClassPulseServer::handleUpdatePassword));
        server.createContext("/api/student/upvote", guarded("/api/student/upvote", ClassPulseServer::handleUpvote));
        server.createContext("/api/student/pulse", guarded("/api/student/pulse", ClassPulseServer::handleStudentPulse));
        server.createContext("/api/teacher/pulse", guarded("/api/teacher/pulse", ClassPulseServer::handleTeacherPulse));
        server.createContext("/api/student/peer-answer", guarded("/api/student/peer-answer", ClassPulseServer::handleStudentPeerAnswer));
        server.createContext("/api/doubt/peer-answers", guarded("/api/doubt/peer-answers", ClassPulseServer::handleGetPeerAnswers));
        server.createContext("/api/logout", guarded("/api/logout", ClassPulseServer::handleLogout));
        server.createContext("/api/download", guarded("/api/download", ClassPulseServer::handleDownload));
        server.createContext("/api/teacher/report", guarded("/api/teacher/report", ClassPulseServer::handleTeacherReport));
        server.createContext("/api/search", guarded("/api/search", ClassPulseServer::handleSearch));
        server.createContext("/api/config", guarded("/api/config", ex->{if(requireMethod(ex,"GET")) sendJson(ex,200,"{"+JsonUtil.field("demo",CollegeAccounts.DEMO)+","+JsonUtil.field("emailDomain",CollegeAccounts.DOMAIN)+"}");}));
        server.createContext("/api/auth/start", guarded("/api/auth/start", ex->{if(!requireMethod(ex,"POST"))return; Map<String,String>b=strictBody(ex,"email","role");sendJson(ex,200,accounts.begin(b.get("email"),b.get("role")));}));
        server.createContext("/api/auth/complete", guarded("/api/auth/complete", ex->{if(!requireMethod(ex,"POST"))return; Map<String,String>b=strictBody(ex,"requestId","code","password");CollegeAccounts.Identity i=accounts.complete(b.get("requestId"),b.get("code"),b.get("password"));sendJson(ex,200,loginResult(i));}));
        server.createContext("/api/demo/inbox", guarded("/api/demo/inbox", ex->{if(!requireMethod(ex,"POST"))return;Map<String,String>b=strictBody(ex,"requestId");sendJson(ex,200,accounts.inbox(b.get("requestId")));}));
        server.createContext("/api/admin/login", guarded("/api/admin/login", ex->{if(!requireMethod(ex,"POST"))return;Map<String,String>b=strictBody(ex,"email","password");String admin=CollegeAccounts.adminLogin(b.get("email"),b.get("password"));sendJson(ex,200,"{"+JsonUtil.field("token",sessionManager.createSession("admin",CollegeAccounts.email(b.get("email"))))+",\"admin\":"+admin+"}");}));
        server.createContext("/api/admin/teachers", guarded("/api/admin/teachers", ClassPulseServer::handleAdminTeachers));
        server.createContext("/api/admin/students", guarded("/api/admin/students", ClassPulseServer::handleAdminStudents));
        Files.createDirectories(Path.of("uploads"));
        server.createContext("/", guarded(null, ClassPulseServer::handleStaticFile));

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("ClassPulse server running on http://localhost:" + PORT);
    }

    private interface Endpoint {void handle(HttpExchange ex) throws Exception;}
    private static HttpHandler guarded(String path, Endpoint handler) {
        return ex -> {
            ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            ex.getResponseHeaders().set("X-Frame-Options", "DENY");
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            try {
                if (path != null && !path.equals(ex.getRequestURI().getPath())) { sendJson(ex,404,JsonUtil.error("Not found")); return; }
                // The shared SQLite connection and compound read/update operations use one lock.
                synchronized (REQUEST_LOCK) { handler.handle(ex); }
            } catch (CollegeAccounts.Failure e) {sendJson(ex,e.status,JsonUtil.error(e.getMessage()));}
              catch (IllegalArgumentException e) { sendJson(ex,400,JsonUtil.error(e.getMessage())); }
              catch (Exception e) { e.printStackTrace(); sendJson(ex,500,JsonUtil.error("Internal server error")); }
            finally { ex.close(); }
        };
    }

    // ---------- Endpoint handlers ----------

    private static void handleStudentLogin(HttpExchange ex) throws IOException {
        if(!requireMethod(ex,"POST")) return;
        try {
            Map<String,String>b=strictBody(ex,"email","password");
            CollegeAccounts.Identity i=CollegeAccounts.require(b.get("email"),"student",true);
            if(studentManager.login(i.userId,b.get("password"))==null) {sendJson(ex,401,JsonUtil.error("Incorrect email or ClassPulse password"));return;}
            sendJson(ex,200,loginResult(i));
        } catch(SQLException e) {sendJson(ex,500,JsonUtil.error("Database operation failed"));}
    }

    private static void handlePostDoubt(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        SessionManager.UserSession session = sessionManager.requireStudentSession(ex);
        if (session == null) return;

        try {
            Map<String, String> body = SimpleJsonParser.parseFlatObject(readBody(ex));
            String rollNumber = session.getUserId();
            String subject = Validation.subject(body.get("subject"));
            String description = Validation.text(body.get("description"), "Description", 5000);
            boolean forcePost = "true".equals(body.get("forcePost"));

            if (subject == null || description == null || description.isBlank()) {
                sendJson(ex, 400, JsonUtil.error("subject and description are required"));
                return;
            }

            Student student = studentManager.find(rollNumber);
            if (student == null) {
                sendJson(ex, 404, JsonUtil.error("Student not found - please log in first"));
                return;
            }

            String relevanceError = AcademicPolicy.questionError(description);
            if (relevanceError != null) {
                sendJson(ex, 200, "{" + JsonUtil.field("blocked", true) + "," + JsonUtil.field("reason", "OFF_TOPIC") + "," + JsonUtil.field("message", relevanceError) + "}");
                return;
            }
            // Toxicity is an additional check; the local relevance rule always runs.
            MLClient.ModerationResult moderation = mlClient.check(description);
            if (moderation.flagged) {
                doubtsManager.logModerationEvent(rollNumber, moderation.confidence);
                sendJson(ex, 200, "{" + JsonUtil.field("blocked", true) +
                        "," + JsonUtil.field("message", "Your doubt contains inappropriate language. Please rephrase and try again.") +
                        "}");
                return;
            }

            // Similar-doubt check (skipped if student already confirmed via forcePost)
            if (!forcePost) {
                List<Doubt> similar = doubtsManager.findSimilarDoubts(subject, student.getSection(), description);
                if (!similar.isEmpty()) {
                    StringBuilder arr = new StringBuilder("[");
                    for (int i = 0; i < similar.size(); i++) {
                        if (i > 0) arr.append(",");
                        arr.append(similar.get(i).toTeacherJson());
                    }
                    arr.append("]");
                    sendJson(ex, 200, "{" + JsonUtil.field("similarFound", true) +
                            ",\"similarDoubts\":" + arr + "}");
                    return;
                }
            }

            Doubt created = doubtsManager.addDoubt(subject, student.getSection(), rollNumber, description);
            sendJson(ex, 200, "{" + JsonUtil.field("posted", true) + ",\"doubt\":" + created.toJson() + "}");

        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static void handleMyDoubts(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        SessionManager.UserSession session = sessionManager.requireStudentSession(ex);
        if (session == null) return;

        try {
            String rollNumber = session.getUserId();
            List<Doubt> doubts = doubtsManager.getDoubtsByRollNumber(rollNumber);
            sendJson(ex, 200, doubtListToJson(doubts, "self"));
        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static void handleClassDoubts(HttpExchange ex) throws IOException {
        if (!requireMethod(ex,"GET")) return;
        SessionManager.UserSession session=sessionManager.requireStudentSession(ex);
        if(session==null) return;
        try {
            Student student=studentManager.find(session.getUserId());
            if(student==null) {sendJson(ex,404,JsonUtil.error("Student not found")); return;}
            sendJson(ex,200,doubtListToJson(doubtsManager.getPendingForSection(student.getSection()),"teacher"));
        } catch(SQLException e) {sendJson(ex,500,JsonUtil.error("Database operation failed"));}
    }

    private static void handleTeacherLogin(HttpExchange ex) throws IOException {
        if(!requireMethod(ex,"POST")) return;
        try {
            Map<String,String>b=strictBody(ex,"email","password");
            CollegeAccounts.Identity i=CollegeAccounts.require(b.get("email"),"teacher",true);
            if(teacherManager.login(i.userId,b.get("password"))==null) {sendJson(ex,401,JsonUtil.error("Incorrect email or ClassPulse password"));return;}
            sendJson(ex,200,loginResult(i));
        } catch(SQLException e) {sendJson(ex,500,JsonUtil.error("Database operation failed"));}
    }

    private static void handleTeacherPending(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        SessionManager.UserSession session = sessionManager.requireTeacherSession(ex);
        if (session == null) return;

        try {
            String teacherId = session.getUserId();
            Teacher teacher = teacherManager.find(teacherId);
            if (teacher == null) {
                sendJson(ex, 404, JsonUtil.error("Teacher not found"));
                return;
            }

            StringBuilder arr = new StringBuilder("[");
            boolean first = true;
            for (String subject : teacher.getSubjectsHandled()) {
                for (Doubt d : doubtsManager.getDoubtsForSubject(subject)) {
                    if(!teacher.handlesDoubt(d)) continue;
                    if (!first) arr.append(",");
                    arr.append(d.toTeacherJson());
                    first = false;
                }
            }
            arr.append("]");
            sendJson(ex, 200, "{\"teacher\":"+teacher.toJson()+",\"doubts\":" + arr + "}");
        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static void handleTeacherResolve(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        SessionManager.UserSession session = sessionManager.requireTeacherSession(ex);
        if (session == null) return;

        try {
            Map<String, String> body = SimpleJsonParser.parseFlatObject(readBody(ex));
            String teacherId = session.getUserId();
            String doubtId = body.get("doubtId");
            String action = body.get("action");
            if (!"resolve".equals(action) && !"reject".equals(action)) throw new IllegalArgumentException("Action must be resolve or reject");
            String text = Validation.text(body.get("text"), "Response or rejection reason", 10000);
            String fileName = body.get("fileName");       // original filename, optional
            String fileDataB64 = body.get("fileData");     // base64 file content, optional

            Teacher teacher = teacherManager.find(teacherId);
            if (teacher == null) {
                sendJson(ex, 404, JsonUtil.error("Teacher not found"));
                return;
            }
            Doubt target = doubtsManager.findById(doubtId);
            if (target == null) {
                sendJson(ex, 404, JsonUtil.error("Doubt not found"));
                return;
            }
            if (!teacher.handlesDoubt(target)) {
                sendJson(ex, 403, JsonUtil.error("This doubt belongs to a subject you don't handle"));
                return;
            }
            if (!target.getStatus().equals("Pending")) {
                sendJson(ex, 409, JsonUtil.error("This doubt is already " + target.getStatus()));
                return;
            }

            if ((fileName != null && !fileName.isBlank()) != (fileDataB64 != null && !fileDataB64.isBlank())) throw new IllegalArgumentException("File name and data must be supplied together");
            if ("reject".equals(action) && fileName != null && !fileName.isBlank()) throw new IllegalArgumentException("Attachments are only for resolutions");
            String relevanceError = AcademicPolicy.answerError(text, target.getDescription());
            if (relevanceError != null) {sendJson(ex,400,JsonUtil.error(relevanceError)); return;}
            if (mlClient.check(text).flagged) {sendJson(ex,400,JsonUtil.error("Inappropriate language is not allowed in teacher responses")); return;}
            String storedFileName = "";
            if (fileName != null && !fileName.isBlank() && fileDataB64 != null && !fileDataB64.isBlank()) {
                storedFileName = saveUploadedFile(doubtId, fileName, fileDataB64);
            }

            String newStatus = action.equals("resolve") ? "Resolved" : "Rejected";
            if (!doubtsManager.updateDoubtStatus(doubtId, newStatus, text, storedFileName)) {
                if (!storedFileName.isBlank()) Files.deleteIfExists(Path.of("uploads", storedFileName));
                sendJson(ex,409,JsonUtil.error("Doubt already processed")); return;
            }
            sendJson(ex, 200, "{" + JsonUtil.field("updated", true) + "}");

        } catch (IllegalArgumentException e) {
            sendJson(ex, 400, JsonUtil.error(e.getMessage()));
        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of("pdf", "png", "jpg", "jpeg", "doc", "docx", "mp4");

    /**
     * Saves a base64-encoded uploaded file under uploads/<doubtId>_<uuid>_<originalName>
     * after validating file extension and file size limit (10MB max).
     */
    private static String saveUploadedFile(String doubtId, String originalName, String base64Data) throws IOException {
        int dotIdx = originalName.lastIndexOf('.');
        if (dotIdx == -1 || dotIdx == originalName.length() - 1) {
            throw new IllegalArgumentException("File extension is missing");
        }
        String ext = originalName.substring(dotIdx + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("Invalid file type ." + ext + ". Allowed types: pdf, png, jpg, jpeg, doc, docx, mp4");
        }

        if (originalName.length()>200 || base64Data.length()>((MAX_FILE_SIZE_BYTES+2)/3)*4) throw new IllegalArgumentException("Attachment is too large");
        byte[] fileBytes = Base64.getDecoder().decode(base64Data);
        if (!hasExpectedSignature(ext,fileBytes)) throw new IllegalArgumentException("Attachment contents do not match the file type");
        if (fileBytes.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds 10 MB limit");
        }

        String safeName = doubtId.replaceAll("[^A-Za-z0-9_-]", "_") + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path target = Path.of("uploads", safeName);
        Files.write(target, fileBytes);
        return safeName;
    }

    private static boolean hasExpectedSignature(String ext, byte[] b) {
        if(b.length<8) return false;
        return switch(ext) {
            case "pdf" -> b[0]=='%' && b[1]=='P' && b[2]=='D' && b[3]=='F' && b[4]=='-';
            case "png" -> java.util.Arrays.equals(java.util.Arrays.copyOf(b,8), new byte[]{(byte)137,80,78,71,13,10,26,10});
            case "jpg", "jpeg" -> (b[0]&255)==255 && (b[1]&255)==216 && (b[2]&255)==255;
            case "doc" -> java.util.Arrays.equals(java.util.Arrays.copyOf(b,8), new byte[]{(byte)208,(byte)207,17,(byte)224,(byte)161,(byte)177,26,(byte)225});
            case "docx" -> isDocx(b);
            case "mp4" -> b[4]=='f' && b[5]=='t' && b[6]=='y' && b[7]=='p';
            default -> false;
        };
    }
    private static boolean isDocx(byte[] b) {
        try (java.util.zip.ZipInputStream zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(b))) {
            boolean types=false, document=false; int entries=0; long expanded=0;
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                if(++entries>2000) return false;
                if(entry.getName().equals("[Content_Types].xml")) types=true;
                if(entry.getName().equals("word/document.xml")) document=true;
                byte[] buffer=new byte[8192]; int count;
                while((count=zip.read(buffer))!=-1) {expanded+=count; if(expanded>50*1024*1024) return false;}
            }
            return types && document;
        } catch(IOException e) {return false;}
    }

    private static void handleUpdatePassword(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        SessionManager.UserSession session = sessionManager.requireAnySession(ex);
        if (session == null) return;

        try {
            Map<String, String> body = SimpleJsonParser.parseFlatObject(readBody(ex));
            String oldPassword = body.get("oldPassword");
            String newPassword = body.get("newPassword");

            if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
                sendJson(ex, 400, JsonUtil.error("oldPassword and newPassword are required"));
                return;
            }

            boolean updated = false;
            if ("student".equals(session.getRole())) {
                updated = studentManager.updatePassword(session.getUserId(), oldPassword, newPassword);
            } else if ("teacher".equals(session.getRole())) {
                updated = teacherManager.updatePassword(session.getUserId(), oldPassword, newPassword);
            }

            if (updated) {
                sessionManager.invalidateOtherSessions(session.getRole(),session.getUserId(),ex.getRequestHeaders().getFirst("X-Session-Token"));
                sendJson(ex, 200, "{\"updated\":true}");
            } else {
                sendJson(ex, 400, JsonUtil.error("Current password is incorrect"));
            }

        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static void handleDownload(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        String fileName = getQueryParam(ex, "file");
        if (fileName == null || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            sendPlainText(ex, 400, "Invalid file request");
            return;
        }
        if (!fileName.matches("[A-Za-z0-9._-]+")) {sendPlainText(ex,400,"Invalid file request"); return;}
        try (java.sql.PreparedStatement ps = Database.getConnection().prepareStatement("SELECT doubt_id, roll_number, subject, section, published_to_faq, status FROM doubts WHERE response_file_name = ?")) {
            ps.setString(1,fileName);
            try (java.sql.ResultSet rs=ps.executeQuery()) {
                if (!rs.next()) {sendPlainText(ex,404,"File not found"); return;}
                if (!(rs.getInt("published_to_faq")==1 && "Resolved".equals(rs.getString("status")))) {
                    SessionManager.UserSession session=sessionManager.requireAnySession(ex); if(session==null) return;
                    boolean allowed="student".equals(session.getRole()) && session.getUserId().equals(rs.getString("roll_number"));
                    if("teacher".equals(session.getRole())) {Teacher t=teacherManager.find(session.getUserId()); allowed=t!=null && t.handlesClass(rs.getString("subject"),rs.getString("section"));}
                    if(!allowed) {sendPlainText(ex,403,"Forbidden"); return;}
                }
            }
        } catch(SQLException e) {sendPlainText(ex,500,"Database operation failed"); return;}
        Path filePath = Path.of("uploads", fileName);
        if (!Files.exists(filePath)) {
            sendPlainText(ex, 404, "File not found");
            return;
        }
        byte[] content = Files.readAllBytes(filePath);
        ex.getResponseHeaders().set("Content-Type", "application/octet-stream");
        ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        ex.sendResponseHeaders(200, content.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(content);
        }
    }

    private static void handleUnpublished(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        SessionManager.UserSession session = sessionManager.requireTeacherSession(ex);
        if (session == null) return;

        try {
            String teacherId = session.getUserId();
            Teacher teacher = teacherManager.find(teacherId);
            if (teacher == null) {
                sendJson(ex, 404, JsonUtil.error("Teacher not found"));
                return;
            }
            List<Doubt> unpublished = doubtsManager.getUnpublishedResolved(teacher.getSubjectsHandled());
            unpublished.removeIf(d->!teacher.handlesDoubt(d));
            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < unpublished.size(); i++) {
                if (i > 0) arr.append(",");
                arr.append(unpublished.get(i).toArchiveJson());
            }
            arr.append("]");
            sendJson(ex, 200, "{\"doubts\":" + arr + "}");
        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static void handlePublishToFaq(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        SessionManager.UserSession session = sessionManager.requireTeacherSession(ex);
        if (session == null) return;

        try {
            Map<String, String> body = SimpleJsonParser.parseFlatObject(readBody(ex));
            String doubtId = body.get("doubtId");
            Doubt target = doubtsManager.findById(doubtId);
            if (target == null) {
                sendJson(ex, 404, JsonUtil.error("Doubt not found"));
                return;
            }
            Teacher teacher = teacherManager.find(session.getUserId());
            if (teacher == null || !teacher.handlesDoubt(target)) {
                sendJson(ex, 403, JsonUtil.error("This doubt belongs to a subject you don't handle"));
                return;
            }
            boolean ok = doubtsManager.publishToFaq(doubtId);
            sendJson(ex, 200, "{" + JsonUtil.field("published", ok) + "}");
        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static void handleLogout(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        String token = ex.getRequestHeaders().getFirst("X-Session-Token");
        sessionManager.invalidate(token);
        sendJson(ex, 200, "{\"success\":true}");
    }

    private static void handleArchive(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        try {
            List<Doubt> archive = doubtsManager.getArchive();
            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < archive.size(); i++) {
                if (i > 0) arr.append(",");
                arr.append(archive.get(i).toArchiveJson());
            }
            arr.append("]");
            sendJson(ex, 200, "{\"archive\":" + arr + "}");
        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static void handleUpvote(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        SessionManager.UserSession session = sessionManager.requireStudentSession(ex);
        if (session == null) return;

        try {
            Map<String, String> body = SimpleJsonParser.parseFlatObject(readBody(ex));
            String doubtId = body.get("doubtId");
            if (doubtId == null || doubtId.isBlank()) {
                sendJson(ex, 400, JsonUtil.error("doubtId is required"));
                return;
            }
            Doubt target = accessibleDoubt(ex, session, doubtId);
            if (target == null) return;
            boolean ok = doubtsManager.upvoteDoubt(doubtId, session.getUserId());
            sendJson(ex, 200, "{\"upvoted\":" + ok + "}");
        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static void handleStudentPulse(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        SessionManager.UserSession session = sessionManager.requireStudentSession(ex);
        if (session == null) return;

        try {
            Map<String, String> body = SimpleJsonParser.parseFlatObject(readBody(ex));
            String subject = Validation.subject(body.get("subject"));
            if (subject == null || subject.isBlank()) {
                sendJson(ex, 400, JsonUtil.error("subject is required"));
                return;
            }
            Student student = studentManager.find(session.getUserId());
            String section = student != null ? student.getSection() : "L";
            boolean pulsed = doubtsManager.logLivePulse(subject, section, session.getUserId());
            sendJson(ex, 200, "{" + JsonUtil.field("pulsed", pulsed) + "}");
        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static void handleTeacherPulse(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        SessionManager.UserSession session = sessionManager.requireTeacherSession(ex);
        if (session == null) return;

        try {
            Teacher teacher = teacherManager.find(session.getUserId());
            if (teacher == null) {
                sendJson(ex, 404, JsonUtil.error("Teacher not found"));
                return;
            }
            Map<String, Integer> stats = doubtsManager.getLivePulseStats(teacher);
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Integer> entry : stats.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
                first = false;
            }
            sb.append("}");
            sendJson(ex, 200, "{\"pulseStats\":" + sb.toString() + "}");
        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static void handleTeacherReport(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        SessionManager.UserSession session = sessionManager.requireTeacherSession(ex);
        if (session == null) return;

        try {
            Teacher teacher = teacherManager.find(session.getUserId());
            if (teacher == null) {
                sendJson(ex, 404, JsonUtil.error("Teacher not found"));
                return;
            }
            String reportHtml = ReportGenerator.generateHtmlReport(teacher.getSubjectsHandled(), teacher, doubtsManager);
            byte[] bytes = reportHtml.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"ClassPulse_Report_" + teacher.getTeacherId().replaceAll("[^A-Za-z0-9_-]", "_") + ".html\"");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static void handleSearch(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        try {
            String query = getQueryParam(ex, "q");
            String subject = getQueryParam(ex, "subject");
            List<Doubt> results = doubtsManager.searchDoubts(query, subject);

            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < results.size(); i++) {
                if (i > 0) arr.append(",");
                arr.append(results.get(i).toArchiveJson());
            }
            arr.append("]");
            sendJson(ex, 200, "{\"results\":" + arr + ",\"count\":" + results.size() + "}");
        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static void handleStudentPeerAnswer(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "POST")) return;
        SessionManager.UserSession session = sessionManager.requireStudentSession(ex);
        if (session == null) return;

        try {
            Map<String, String> body = SimpleJsonParser.parseFlatObject(readBody(ex));
            String doubtId = body.get("doubtId");
            String answerText = Validation.text(body.get("answerText"), "Answer", 5000);

            if (doubtId == null || answerText == null || answerText.isBlank()) {
                sendJson(ex, 400, JsonUtil.error("doubtId and answerText are required"));
                return;
            }

            Doubt target = accessibleDoubt(ex, session, doubtId);
            if (target == null) return;
            if (!"Pending".equals(target.getStatus())) { sendJson(ex,409,JsonUtil.error("Doubt is already processed")); return; }
            String relevanceError = AcademicPolicy.answerError(answerText, target.getDescription());
            if (relevanceError != null) { sendJson(ex,400,JsonUtil.error(relevanceError)); return; }
            if (mlClient.check(answerText).flagged) { sendJson(ex,400,JsonUtil.error("Please rephrase inappropriate language")); return; }
            boolean ok = doubtsManager.addPeerAnswer(doubtId, answerText);
            sendJson(ex, 200, "{\"submitted\":" + ok + "}");
        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static void handleGetPeerAnswers(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        SessionManager.UserSession session = sessionManager.requireAnySession(ex);
        if (session == null) return;

        try {
            String doubtId = getQueryParam(ex, "doubtId");
            if (doubtId == null || doubtId.isBlank()) {
                sendJson(ex, 400, JsonUtil.error("doubtId parameter is required"));
                return;
            }
            if (accessibleDoubt(ex, session, doubtId) == null) return;
            List<Map<String, String>> list = doubtsManager.getPeerAnswers(doubtId);
            StringBuilder arr = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) arr.append(",");
                Map<String, String> item = list.get(i);
                arr.append("{")
                   .append(JsonUtil.field("id", item.get("id"))).append(",")
                   .append(JsonUtil.field("doubtId", item.get("doubtId"))).append(",")
                   .append(JsonUtil.field("answerText", item.get("answerText"))).append(",")
                   .append(JsonUtil.field("postedTime", item.get("postedTime")))
                   .append("}");
            }
            arr.append("]");
            sendJson(ex, 200, "{\"peerAnswers\":" + arr.toString() + "}");
        } catch (SQLException e) {
            sendJson(ex, 500, JsonUtil.error("Database operation failed"));
        }
    }

    private static Doubt accessibleDoubt(HttpExchange ex, SessionManager.UserSession session, String id) throws SQLException, IOException {
        Doubt target=doubtsManager.findById(id);
        if(target==null) {sendJson(ex,404,JsonUtil.error("Doubt not found")); return null;}
        boolean allowed;
        if ("teacher".equals(session.getRole())) {
            Teacher teacher=teacherManager.find(session.getUserId()); allowed=teacher!=null && teacher.handlesDoubt(target);
        } else {
            Student student=studentManager.find(session.getUserId()); allowed=student!=null && student.getSection().equalsIgnoreCase(target.getSection());
        }
        if(!allowed) {sendJson(ex,403,JsonUtil.error("Doubt is outside your class or subjects")); return null;} return target;
    }

    private static void handleStaticFile(HttpExchange ex) throws IOException {
        if (!requireMethod(ex, "GET")) return;
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path root = Path.of("public").toAbsolutePath().normalize();
        Path filePath = root.resolve(path.substring(1)).normalize();
        if (!filePath.startsWith(root)) {sendPlainText(ex,403,"Forbidden"); return;}

        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            sendPlainText(ex, 404, "Not found");
            return;
        }

        byte[] content = Files.readAllBytes(filePath);
        String contentType = guessContentType(path);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, content.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(content);
        }
    }

    private static Map<String,String> strictBody(HttpExchange ex,String...keys) throws IOException {
        String raw=readBody(ex);if(raw.length()>8192) throw new IllegalArgumentException("Account request is too large");
        Map<String,String>b=SimpleJsonParser.parseFlatObject(raw);
        if(!java.util.Set.of(keys).containsAll(b.keySet())) throw new IllegalArgumentException("Unexpected account fields. Identity and assignments come from the approved roster.");
        return b;
    }
    private static String loginResult(CollegeAccounts.Identity i) throws SQLException {
        String profile=i.role.equals("student")?studentManager.find(i.userId).toJson():teacherManager.find(i.userId).toJson();
        return "{"+JsonUtil.field("token",sessionManager.createSession(i.role,i.userId))+","+JsonUtil.field("email",i.email)+",\""+i.role+"\":"+profile+"}";
    }
    private static void handleAdminTeachers(HttpExchange ex) throws IOException {
        if(sessionManager.requireAdminSession(ex)==null)return;
        try {
            if("POST".equals(ex.getRequestMethod())) {
                Map<String,String>b=strictBody(ex,"email","name","subjects","sections","approved");
                if(!"true".equals(b.get("approved")) && !"false".equals(b.get("approved")))throw new IllegalArgumentException("Approval must be true or false");
                CollegeAccounts.saveTeacher(b.get("email"),b.get("name"),b.get("subjects"),b.get("sections"),"true".equals(b.get("approved")));
            } else if(!requireMethod(ex,"GET"))return;
            sendJson(ex,200,"{\"teachers\":"+CollegeAccounts.rosterJson("teacher")+"}");
        } catch(SQLException e) {sendJson(ex,409,JsonUtil.error("Roster update failed. Check for duplicate email or identity."));}
    }
    private static void handleAdminStudents(HttpExchange ex) throws IOException {
        if(sessionManager.requireAdminSession(ex)==null)return;
        try {
            if("POST".equals(ex.getRequestMethod())) {
                Map<String,String>b=strictBody(ex,"email","rollNumber","name","section");CollegeAccounts.addStudent(b.get("email"),b.get("rollNumber"),b.get("name"),b.get("section"));
            } else if(!requireMethod(ex,"GET"))return;
            sendJson(ex,200,"{\"students\":"+CollegeAccounts.rosterJson("student")+"}");
        }catch(SQLException e) {sendJson(ex,409,JsonUtil.error("Email or roll number already exists"));}
    }
    // ---------- Helpers ----------

    private static String doubtListToJson(List<Doubt> doubts, String view) {
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < doubts.size(); i++) {
            if (i > 0) arr.append(",");
            arr.append(view.equals("self") ? doubts.get(i).toJson() : doubts.get(i).toTeacherJson());
        }
        arr.append("]");
        return "{\"doubts\":" + arr + "}";
    }

    private static boolean requireMethod(HttpExchange ex, String method) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase(method)) {
            sendJson(ex, 405, JsonUtil.error("Method not allowed, expected " + method));
            return false;
        }
        return true;
    }

    private static String readBody(HttpExchange ex) throws IOException {
        int max = 14 * 1024 * 1024;
        byte[] body=ex.getRequestBody().readNBytes(max+1);
        if(body.length>max) throw new IllegalArgumentException("Request body exceeds 14 MB limit");
        return new String(body, StandardCharsets.UTF_8);
    }

    private static String getQueryParam(HttpExchange ex, String key) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void sendJson(HttpExchange ex, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendPlainText(HttpExchange ex, int statusCode, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String guessContentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        return "application/octet-stream";
    }
}

