import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.atomic.AtomicLong;
import com.sun.net.httpserver.HttpServer;

/** Exercises the real HTTP server against an isolated, disposable demo database. */
public class CollegeAuthTests {
    static final HttpClient HTTP=HttpClient.newHttpClient();
    static String base;static int passed;static Path temp;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
    static String field(String json,String key){Matcher m=Pattern.compile("\""+key+"\"\\s*:\\s*\"([^\"]*)\"").matcher(json);if(!m.find())throw new AssertionError("Missing "+key+" in "+json);return m.group(1);}
    static String json(String...pairs){StringJoiner j=new StringJoiner(",","{","}");for(int i=0;i<pairs.length;i+=2)j.add(JsonUtil.field(pairs[i],pairs[i+1]));return j.toString();}
    static HttpResponse<String> call(String path,String token,String body)throws Exception {
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(java.time.Duration.ofSeconds(15));
        if(token!=null)b.header("X-Session-Token",token);
        if(body!=null)b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));else b.GET();
        return HTTP.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    static String ok(String path,String token,String body)throws Exception{var r=call(path,token,body);check(r.statusCode()==200,path+" succeeds");return r.body();}
    static void status(int code,String path,String token,String body,String name)throws Exception {var r=call(path,token,body);check(r.statusCode()==code,name+" (got "+r.statusCode()+")");}
    static String enroll(String email,String role)throws Exception {
        String start=ok("/api/auth/start",null,json("email",email,"role",role));String id=field(start,"requestId");
        check(!start.contains("\"code\""),"verification response contains no code");
        String code=field(ok("/api/demo/inbox",null,json("requestId",id)),"code");
        status(400,"/api/auth/complete",null,json("requestId",id,"code",code,"password","DemoPass123!","section","M"),"roster identity tampering rejected");
        status(400,"/api/auth/complete",null,json("requestId",id,"code","invalid","password","DemoPass123!"),"wrong verification code rejected");
        String result=ok("/api/auth/complete",null,json("requestId",id,"code",code,"password","DemoPass123!"));
        status(400,"/api/auth/complete",null,json("requestId",id,"code",code,"password","DemoPass123!"),"used code cannot be replayed");return result;
    }
    static Process launch(boolean demo,int mlPort)throws Exception {
        int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}base="http://127.0.0.1:"+port;
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Dclasspulse.port="+port,"-Dclasspulse.db="+temp.resolve("college-demo.db"),"-Dclasspulse.mlUrl=http://127.0.0.1:"+mlPort+"/check"));
        if(demo)command.add("-Dclasspulse.demo=true");command.addAll(List.of("-cp",System.getProperty("java.class.path"),"ClassPulseServer"));
        Process process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(temp.resolve(demo?"server-demo.log":"server-normal.log").toFile()).start();
        for(int n=0;n<100;n++){try{if(call("/api/config",null,null).statusCode()==200)return process;}catch(Exception e){}if(!process.isAlive())break;Thread.sleep(100);}
        process.destroyForcibly();throw new AssertionError("Server failed to start. See "+temp);
    }
    static void stop(Process p)throws Exception{p.destroy();if(!p.waitFor(5,java.util.concurrent.TimeUnit.SECONDS))p.destroyForcibly();}
    public static void main(String[] args)throws Exception {
        temp=Files.createTempDirectory("ClassPulse-college-tests-");System.setProperty("classpulse.demo","true");System.setProperty("classpulse.db",temp.resolve("college-demo.db").toString());DemoSetup.main(new String[0]);
        // Deterministic verification expiry, cooldown and attempt-limit tests.
        Database.getConnection();AtomicLong time=new AtomicLong(1000000);CollegeAccounts a=new CollegeAccounts(time::get);
        String id=field(a.begin("student1@college.example","student"),"requestId");
        try{a.begin("student1@college.example","student");check(false,"cooldown");}catch(CollegeAccounts.Failure e){check(e.status==429,"verification resend cooldown");}
        time.addAndGet(300001);try{a.inbox(id);check(false,"expiry");}catch(CollegeAccounts.Failure e){check(e.status==400,"verification expires after five minutes");}
        id=field(a.begin("student1@college.example","student"),"requestId");for(int n=0;n<5;n++){try{a.complete(id,"wrong","DemoPass123!");check(false,"attempts");}catch(CollegeAccounts.Failure e){check(e.status==400,"wrong code attempt "+(n+1));}}
        try{a.inbox(id);check(false,"locked code");}catch(CollegeAccounts.Failure e){check(e.status==400,"five wrong codes invalidate challenge");}
        var doubts=new DoubtsManager();String outside=doubts.getDoubtsForSubject("Java").stream().filter(d->d.getSection().equals("M")).findFirst().orElseThrow().getDoubtId();String inside=doubts.getDoubtsForSubject("Java").stream().filter(d->d.getSection().equals("L")).findFirst().orElseThrow().getDoubtId();
        Doubt privateResolved=doubts.addDoubt("Java","M","DEMO_OTHER","How does Java encapsulation protect fields in section M?");
        String attachment="scope-test-"+UUID.randomUUID()+".pdf";Path fixture=Path.of("uploads",attachment);Files.createDirectories(fixture.getParent());Files.writeString(fixture,"demo PDF fixture");
        doubts.updateDoubtStatus(privateResolved.getDoubtId(),"Resolved","Java encapsulation uses private fields and public accessors.",attachment);
        Database.getConnection().close();
        HttpServer ml=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);ml.createContext("/check",ex->{ex.getRequestBody().readAllBytes();byte[] b="{\"flagged\":false,\"confidence\":0}".getBytes();ex.sendResponseHeaders(200,b.length);ex.getResponseBody().write(b);ex.close();});ml.start();Process server=null;
        try {
            server=launch(true,ml.getAddress().getPort());
            status(400,"/api/auth/start",null,json("email","student@gmail.com","role","student"),"non-college domain rejected");
            status(403,"/api/auth/start",null,json("email","unknown@college.example","role","student"),"unlisted college email rejected");
            status(400,"/api/auth/start",null,json("email","student1@college.example"),"missing role rejected");
            status(403,"/api/auth/start",null,json("email","teacher@college.example","role","teacher"),"unapproved faculty cannot activate");
            status(403,"/api/student/login",null,json("email","student1@college.example","password","DemoPass123!"),"unverified student cannot login");
            status(400,"/api/student/login",null,json("rollNumber","NEW","name","Imposter","section","M","password","DemoPass123!"),"legacy student registration bypass closed");
            status(400,"/api/teacher/login",null,json("teacherId","NEW","subjects","Java","secretKey","key","password","DemoPass123!"),"legacy faculty registration bypass closed");
            String s=field(enroll("student1@college.example","student"),"token");
            String me=ok("/api/student/login",null,json("email","STUDENT1@college.example","password","DemoPass123!"));check(me.contains("DEMO_STUDENT")&&me.contains("\"section\":\"L\""),"authoritative student identity and case-insensitive email");
            status(401,"/api/student/login",null,json("email","student1@college.example","password","wrong"),"incorrect ClassPulse password rejected");
            status(409,"/api/auth/start",null,json("email","student1@college.example","role","student"),"activated account cannot overwrite password by enrollment");
            status(403,"/api/admin/teachers",s,null,"student cannot administer faculty");
            status(401,"/api/admin/teachers",null,null,"anonymous roster access blocked");
            status(401,"/api/admin/login",null,json("email","admin@college.example","password","wrong"),"wrong administrator password blocked");
            String admin=field(ok("/api/admin/login",null,json("email","admin@college.example","password","AdminDemo123!")),"token");
            status(400,"/api/admin/teachers",admin,json("email","teacher@college.example","name","Demo Teacher","subjects","Java","sections","","approved","true"),"approval requires section assignment");
            ok("/api/admin/teachers",admin,json("email","teacher@college.example","name","Demo Teacher","subjects","Java,DAA","sections","L","approved","true"));
            String t=field(enroll("teacher@college.example","teacher"),"token");
            status(403,"/api/admin/students",t,null,"teacher cannot access administrator roster");
            String pending=ok("/api/teacher/pending",t,null);check(pending.contains(inside)&&!pending.contains(outside),"pending doubts scoped to assigned section");
            status(403,"/api/teacher/resolve",t,json("doubtId",outside,"action","resolve","text","Java supports inheritance."),"teacher cannot resolve another section");
            status(403,"/api/teacher/publish",t,json("doubtId",outside),"teacher cannot publish another section");
            status(403,"/api/teacher/publish",t,json("doubtId",privateResolved.getDoubtId()),"teacher cannot publish resolved doubt from another section");
            check(!ok("/api/teacher/unpublished",t,null).contains(privateResolved.getDoubtId()),"unpublished resolutions exclude other sections");
            status(403,"/api/download?file="+attachment,t,null,"private attachments blocked for unassigned teacher");
            status(403,"/api/download?file="+attachment,s,null,"private attachments blocked for other students");
            status(403,"/api/doubt/peer-answers?doubtId="+outside,t,null,"teacher cannot read other section peer answers");
            status(403,"/api/doubt/peer-answers?doubtId="+outside,s,null,"student cannot read other section peer answers");
            check(!ok("/api/teacher/report",t,null).contains(outside),"report excludes unassigned section");
            String other=field(enroll("student3@college.example","student"),"token");ok("/api/student/pulse",other,json("subject","Java"));
            check(ok("/api/download?file="+attachment,other,null).equals("demo PDF fixture"),"private attachment available to its question author");
            check(ok("/api/teacher/pulse",t,null).contains("\"Java\":0"),"confusion meter excludes unassigned section");
            String blocked=ok("/api/student/doubt",s,json("subject","Java","description","How was your weekend?","forcePost","true"));check(blocked.contains("\"blocked\":true"),"off-topic chat still blocked");
            String posted=ok("/api/student/doubt",s,json("subject","Java","description","How do Java interfaces differ from abstract classes?","forcePost","true"));check(posted.contains("\"posted\":true"),"academic question posts successfully");
            String peer=field(enroll("student2@college.example","student"),"token");
            String voteId=field(posted,"doubtId");check(ok("/api/student/upvote",peer,json("doubtId",voteId)).contains("true"),"classmate can support question");check(ok("/api/student/upvote",peer,json("doubtId",voteId)).contains("false"),"duplicate vote rejected");
            String answer=ok("/api/teacher/resolve",t,json("doubtId",inside,"action","resolve","text","The Java JVM garbage collector reclaims unreachable heap objects automatically."));check(answer.contains("true"),"assigned teacher can resolve academic doubt");
            check(ok("/api/teacher/unpublished",t,null).contains(inside),"resolved question available for assigned teacher publication");ok("/api/teacher/publish",t,json("doubtId",inside));check(ok("/api/archive",null,null).contains(inside),"published FAQ publicly visible");
            ok("/api/admin/teachers",admin,json("email","teacher@college.example","name","Demo Teacher","subjects","Java","sections","M","approved","true"));
            String changed=ok("/api/teacher/pending",t,null);check(changed.contains(outside)&&!changed.contains(voteId),"assignment change affects existing session immediately");
            ok("/api/admin/teachers",admin,json("email","teacher@college.example","name","Demo Teacher","subjects","Java","sections","M","approved","false"));status(401,"/api/teacher/pending",t,null,"revoked approval invalidates existing session");
            status(403,"/api/teacher/login",null,json("email","teacher@college.example","password","DemoPass123!"),"revoked teacher cannot login again");
            ok("/api/admin/students",admin,json("email","newstudent@college.example","rollNumber","NEW_ROLL","name","New Student","section","N"));check(ok("/api/admin/students",admin,null).contains("NEW_ROLL"),"administrator can extend approved student roster");
            status(409,"/api/admin/teachers",admin,json("email","student1@college.example","name","Imposter","subjects","Java","sections","L","approved","true"),"student email cannot be reassigned to teacher role");
            String secondToken=field(ok("/api/student/login",null,json("email","student1@college.example","password","DemoPass123!")),"token");
            ok("/api/user/password",s,json("oldPassword","DemoPass123!","newPassword","ChangedDemo123!"));
            status(401,"/api/student/mydoubts",secondToken,null,"password change revokes other sessions");
            ok("/api/student/mydoubts",s,null);ok("/api/student/login",null,json("email","student1@college.example","password","ChangedDemo123!"));
            ok("/api/logout",peer,"{}");status(401,"/api/student/mydoubts",peer,null,"logout revokes token");
            stop(server);server=null;server=launch(false,ml.getAddress().getPort());
            check(ok("/api/config",null,null).contains("\"demo\":false"),"normal mode not demo");status(404,"/api/demo/inbox",null,json("requestId","anything"),"normal mode disables demo inbox");status(503,"/api/auth/start",null,json("email","newstudent@college.example","role","student"),"unconfigured real email cannot pretend to verify");
            System.out.println("PASS: "+passed+" checks. Isolated test logs: "+temp);
        } finally {if(server!=null)stop(server);ml.stop(0);Files.deleteIfExists(fixture);}
    }
}
