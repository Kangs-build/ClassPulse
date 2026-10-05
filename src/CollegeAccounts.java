import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.security.SecureRandom;
import java.util.function.LongSupplier;

/** Approved roster and prototype email verification. No college mailbox passwords are collected. */
public final class CollegeAccounts {
    public static final boolean DEMO = Boolean.getBoolean("classpulse.demo");
    public static final String DOMAIN = System.getProperty("classpulse.emailDomain", "college.example").toLowerCase(Locale.ROOT);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String,Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String,Long> sentAt = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    public CollegeAccounts() {this(System::currentTimeMillis);}
    CollegeAccounts(LongSupplier clock) {this.clock=clock;}
    public static class Failure extends RuntimeException {
        public final int status;
        Failure(int status,String message) {super(message);this.status=status;}
    }
    public static class Identity {
        public String email,role,userId,name,section,subjects,sections;
        public boolean approved,verified;
        public String toJson() {
            return "{"+JsonUtil.field("email",email)+","+JsonUtil.field("role",role)+","+JsonUtil.field("userId",userId)+","+JsonUtil.field("name",name)+","+JsonUtil.field("section",section)+","+JsonUtil.field("subjects",subjects)+","+JsonUtil.field("sections",sections)+","+JsonUtil.field("approved",approved)+","+JsonUtil.field("verified",verified)+"}";
        }
    }
    private static class Challenge {
        final String id,email,role,code;final long expires;int attempts;
        Challenge(String email,String role,long now) {this.id=UUID.randomUUID().toString();this.email=email;this.role=role;this.code=String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1000000));this.expires=now+5*60*1000;}
    }
    public static void initSchema(Connection conn) throws SQLException {
        try(Statement s=conn.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS college_identities(email TEXT PRIMARY KEY, role TEXT NOT NULL CHECK(role IN ('student','teacher')), user_id TEXT NOT NULL, name TEXT NOT NULL, section TEXT NOT NULL DEFAULT '', subjects TEXT NOT NULL DEFAULT '', sections TEXT NOT NULL DEFAULT '', approved INTEGER NOT NULL DEFAULT 0, verified INTEGER NOT NULL DEFAULT 0, UNIQUE(role,user_id))");
            s.execute("CREATE TABLE IF NOT EXISTS college_admins(email TEXT PRIMARY KEY, name TEXT NOT NULL, password_hash TEXT NOT NULL)");
        }
    }
    public static String email(String raw) {
        String value=Validation.text(raw,"College email",254).toLowerCase(Locale.ROOT);
        if(!value.matches("[a-z0-9][a-z0-9._+\\-]*@[a-z0-9.\\-]+") || !value.substring(value.indexOf('@')+1).equals(DOMAIN)) throw new Failure(400,"Use your approved college email ending in @"+DOMAIN);
        return value;
    }
    public static Identity find(String email) throws SQLException {
        try(PreparedStatement ps=Database.getConnection().prepareStatement("SELECT * FROM college_identities WHERE email=?")) {
            ps.setString(1,email);try(ResultSet rs=ps.executeQuery()) {return rs.next()?map(rs):null;}
        }
    }
    private static Identity map(ResultSet rs) throws SQLException {
        Identity i=new Identity();i.email=rs.getString("email");i.role=rs.getString("role");i.userId=rs.getString("user_id");i.name=rs.getString("name");i.section=rs.getString("section");i.subjects=rs.getString("subjects");i.sections=rs.getString("sections");i.approved=rs.getInt("approved")==1;i.verified=rs.getInt("verified")==1;return i;
    }
    public static Identity require(String raw,String role,boolean verified) throws SQLException {
        Identity i=find(email(raw));
        if(i==null || !role.equals(i.role)) throw new Failure(403,"This email is not on the approved "+role+" roster. Contact the administrator.");
        if(!i.approved) throw new Failure(403,"Administrator approval is required before this account can be activated or used.");
        if(verified && !i.verified) throw new Failure(403,"Verify your approved college email and create a ClassPulse password first.");
        return i;
    }
    public static boolean active(String role,String userId) {
        String sql=role.equals("admin") ? "SELECT 1 FROM college_admins WHERE email=?" : "SELECT 1 FROM college_identities WHERE role=? AND user_id=? AND approved=1 AND verified=1";
        try(PreparedStatement ps=Database.getConnection().prepareStatement(sql)) {
            if(role.equals("admin")) ps.setString(1,userId);else {ps.setString(1,role);ps.setString(2,userId);}
            try(ResultSet rs=ps.executeQuery()) {return rs.next();}
        } catch(SQLException e) {return false;}
    }
    public static List<String> sectionsForTeacher(String userId) throws SQLException {
        try(PreparedStatement ps=Database.getConnection().prepareStatement("SELECT sections FROM college_identities WHERE role='teacher' AND user_id=? AND approved=1 AND verified=1")) {
            ps.setString(1,userId);try(ResultSet rs=ps.executeQuery()) {return rs.next() && !rs.getString(1).isBlank()?List.of(rs.getString(1).split(",")):List.of();}
        }
    }
    public synchronized String begin(String raw,String role) throws SQLException {
        if(!DEMO) throw new Failure(503,"Real email delivery is not configured. Run demo.cmd to demonstrate email verification locally.");
        if(role==null || !Set.of("student","teacher").contains(role)) throw new Failure(400,"Choose student or teacher enrollment");
        Identity i=require(raw,role,false);
        if(i.verified) throw new Failure(409,"This account is already activated. Please log in.");
        long now=clock.getAsLong();purge(now);
        if(sentAt.containsKey(i.email) && now-sentAt.get(i.email)<30000) throw new Failure(429,"Wait 30 seconds before requesting another code.");
        if(challenges.size()>=1000) throw new Failure(429,"Too many verification requests. Try again later.");
        challenges.values().removeIf(c->c.email.equals(i.email));
        Challenge c=new Challenge(i.email,role,now);challenges.put(c.id,c);sentAt.put(i.email,now);
        return "{"+JsonUtil.field("requestId",c.id)+","+JsonUtil.field("demo",true)+",\"expiresInSeconds\":300,\"profile\":"+i.toJson()+"}";
    }
    private void purge(long now) {challenges.values().removeIf(c->c.expires<=now);sentAt.entrySet().removeIf(e->now-e.getValue()>5*60*1000);}
    private Challenge challenge(String id) {
        if(id==null) throw new Failure(400,"Start email verification first");
        Challenge c=challenges.get(id);
        if(c==null || c.expires<=clock.getAsLong()) {challenges.remove(id);throw new Failure(400,"Verification expired or already used. Start again.");}
        return c;
    }
    public synchronized String inbox(String requestId) {
        if(!DEMO) throw new Failure(404,"Demo inbox is disabled");
        Challenge c=challenge(requestId);
        return "{"+JsonUtil.field("demo",true)+","+JsonUtil.field("email",c.email)+","+JsonUtil.field("code",c.code)+","+JsonUtil.field("message","DEMO EMAIL INBOX: no real email was sent. This code expires in five minutes.")+"}";
    }
    public synchronized Identity complete(String requestId,String code,String password) throws SQLException {
        Challenge c=challenge(requestId);
        if(code==null || !java.security.MessageDigest.isEqual(c.code.getBytes(java.nio.charset.StandardCharsets.UTF_8),code.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            c.attempts++;
            if(c.attempts>=5) challenges.remove(c.id);
            throw new Failure(400,"Incorrect verification code");
        }
        Validation.password(password);
        Identity i=require(c.email,c.role,false);
        if(i.verified) {challenges.remove(c.id);throw new Failure(409,"Account already activated");}
        String hash=PasswordUtil.hash(password);
        Connection conn=Database.getConnection();conn.setAutoCommit(false);
        try {
            String sql=i.role.equals("student") ? "INSERT INTO students(roll_number,name,section,password_hash) VALUES(?,?,?,?) ON CONFLICT(roll_number) DO UPDATE SET name=excluded.name,section=excluded.section,password_hash=excluded.password_hash" : "INSERT INTO teachers(teacher_id,name,subjects,password_hash) VALUES(?,?,?,?) ON CONFLICT(teacher_id) DO UPDATE SET name=excluded.name,subjects=excluded.subjects,password_hash=excluded.password_hash";
            try(PreparedStatement ps=conn.prepareStatement(sql)) {ps.setString(1,i.userId);ps.setString(2,i.name);ps.setString(3,i.role.equals("student")?i.section:i.subjects);ps.setString(4,hash);ps.executeUpdate();}
            try(PreparedStatement ps=conn.prepareStatement("UPDATE college_identities SET verified=1 WHERE email=?")) {ps.setString(1,i.email);ps.executeUpdate();}
            conn.commit();i.verified=true;challenges.remove(c.id);return i;
        } catch(SQLException e) {conn.rollback();throw e;} finally {conn.setAutoCommit(true);}
    }
    public static void addStudent(String raw,String userId,String name,String section) throws SQLException {
        String email=email(raw);userId=Validation.identifier(userId,"Roll number");name=Validation.text(name,"Name",100);section=Validation.identifier(section,"Section").toUpperCase(Locale.ROOT);
        try(PreparedStatement ps=Database.getConnection().prepareStatement("INSERT INTO college_identities(email,role,user_id,name,section,approved) VALUES(?,'student',?,?,?,1)")) {ps.setString(1,email);ps.setString(2,userId);ps.setString(3,name);ps.setString(4,section);ps.executeUpdate();}
    }
    public static void saveTeacher(String raw,String name,String subjectsCsv,String sectionsCsv,boolean approved) throws SQLException {
        String email=email(raw);name=Validation.text(name,"Teacher name",100);Identity existing=find(email);
        if(existing!=null && !existing.role.equals("teacher")) throw new Failure(409,"This email belongs to a student");
        if((subjectsCsv!=null && subjectsCsv.length()>1000) || (sectionsCsv!=null && sectionsCsv.length()>1000)) throw new Failure(400,"Assignments exceed maximum length");
        LinkedHashSet<String> subjects=new LinkedHashSet<>(),sections=new LinkedHashSet<>();
        if(subjectsCsv!=null && !subjectsCsv.isBlank()) for(String s:subjectsCsv.split(",",-1)) subjects.add(Validation.subject(s));
        if(sectionsCsv!=null && !sectionsCsv.isBlank()) for(String s:sectionsCsv.split(",",-1)) sections.add(Validation.identifier(s,"Assigned section").toUpperCase(Locale.ROOT));
        if(approved && (subjects.isEmpty() || sections.isEmpty())) throw new Failure(400,"Assign at least one subject and one section before approval");
        String subjectsText=String.join(",",subjects),sectionsText=String.join(",",sections);
        String id=existing==null ? "T_"+UUID.randomUUID().toString().replace("-", "").substring(0,12):existing.userId;
        Connection conn=Database.getConnection();conn.setAutoCommit(false);
        try {
            try(PreparedStatement ps=conn.prepareStatement("INSERT INTO college_identities(email,role,user_id,name,subjects,sections,approved) VALUES(?,'teacher',?,?,?,?,?) ON CONFLICT(email) DO UPDATE SET name=excluded.name,subjects=excluded.subjects,sections=excluded.sections,approved=excluded.approved")) {
                ps.setString(1,email);ps.setString(2,id);ps.setString(3,name);ps.setString(4,subjectsText);ps.setString(5,sectionsText);ps.setInt(6,approved?1:0);ps.executeUpdate();
            }
            try(PreparedStatement ps=conn.prepareStatement("UPDATE teachers SET name=?,subjects=? WHERE teacher_id=?")) {ps.setString(1,name);ps.setString(2,subjectsText);ps.setString(3,id);ps.executeUpdate();}
            conn.commit();
        } catch(SQLException e) {conn.rollback();throw e;}finally {conn.setAutoCommit(true);}
    }
    public static String rosterJson(String role) throws SQLException {
        StringJoiner j=new StringJoiner(",","[","]");
        try(PreparedStatement ps=Database.getConnection().prepareStatement("SELECT * FROM college_identities WHERE role=? ORDER BY email")) {ps.setString(1,role);try(ResultSet rs=ps.executeQuery()) {while(rs.next()) j.add(map(rs).toJson());}}
        return j.toString();
    }
    public static void seedAdmin(String raw,String name,String password) throws SQLException {
        Validation.password(password);
        try(PreparedStatement ps=Database.getConnection().prepareStatement("INSERT INTO college_admins(email,name,password_hash) VALUES(?,?,?)")) {ps.setString(1,email(raw));ps.setString(2,Validation.text(name,"Name",100));ps.setString(3,PasswordUtil.hash(password));ps.executeUpdate();}
    }
    public static String adminLogin(String raw,String password) throws SQLException {
        String email=email(raw);
        try(PreparedStatement ps=Database.getConnection().prepareStatement("SELECT name,password_hash FROM college_admins WHERE email=?")) {
            ps.setString(1,email);try(ResultSet rs=ps.executeQuery()) {if(!rs.next() || !PasswordUtil.verify(password,rs.getString("password_hash"))) throw new Failure(401,"Incorrect email or ClassPulse password");return "{"+JsonUtil.field("email",email)+","+JsonUtil.field("name",rs.getString("name"))+"}";}
        }
    }
}
