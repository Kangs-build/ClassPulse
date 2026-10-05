import java.nio.file.*;
import java.util.UUID;
/** Seeds only the isolated fictional college demo. */
public class DemoSetup {
 public static void main(String[] args) throws Exception {
  String db=System.getProperty("classpulse.db");
  if(!CollegeAccounts.DEMO || db==null || !Path.of(db).getFileName().toString().equals("college-demo.db"))throw new IllegalArgumentException("Use demo.cmd: demo mode and data/college-demo.db required");
  boolean exists=Files.exists(Path.of(db));Database.initSchema();
  if(exists){try(var s=Database.getConnection().createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM college_admins")){if(!r.next() || r.getInt(1)==0)throw new IllegalStateException("Incomplete demo database. Stop the server and rename data/college-demo.db before retrying.");}System.out.println("Existing college demo retained.");Database.getConnection().close();return;}
  StudentManager students=new StudentManager();DoubtsManager doubts=new DoubtsManager();
  String[] ids={"DEMO_STUDENT","DEMO_PEER","DEMO_OTHER"};String[] names={"Demo Student","Demo Classmate","Other Section Student"};
  for(int n=0;n<3;n++){String section=n==2?"M":"L";CollegeAccounts.addStudent("student"+(n+1)+"@college.example",ids[n],names[n],section);students.register(ids[n],names[n],section,UUID.randomUUID().toString());}
  CollegeAccounts.saveTeacher("teacher@college.example","Demo Teacher","Java,DAA","L",false);
  Doubt pending=doubts.addDoubt("Java","L",ids[0],"How does Java garbage collection work?");doubts.addPeerAnswer(pending.getDoubtId(),"The JVM reclaims unreachable heap objects automatically.");doubts.upvoteDoubt(pending.getDoubtId(),ids[1]);
  doubts.addDoubt("DAA","L",ids[0],"Why does binary search take logarithmic time?");
  doubts.addDoubt("Java","M",ids[2],"How does Java inheritance work in section M?");
  Doubt resolved=doubts.addDoubt("ML","L",ids[1],"How does gradient descent adjust weights in linear regression?");doubts.updateDoubtStatus(resolved.getDoubtId(),"Resolved","Update weights using learning rate times the gradient until loss converges.","");doubts.publishToFaq(resolved.getDoubtId());
  // Administrator written last, to detect an interrupted setup.
  CollegeAccounts.seedAdmin("admin@college.example","Demo Administrator","AdminDemo123!");
  System.out.println("College demo ready. Administrator: admin@college.example / AdminDemo123!");Database.getConnection().close();
 }
}
