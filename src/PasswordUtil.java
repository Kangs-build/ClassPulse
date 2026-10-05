import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
public class PasswordUtil {
    private static final int ITERATIONS = 210000;
    public static String hash(String password) {
        byte[] salt=new byte[16]; new SecureRandom().nextBytes(salt);
        return "pbkdf2:"+ITERATIONS+":"+Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(password,salt,ITERATIONS));
    }
    public static boolean verify(String password,String stored) {
        if(password==null || stored==null) return false;
        try {
            String[] p=stored.split(":");
            if(p.length==4 && p[0].equals("pbkdf2")) {
                int rounds=Integer.parseInt(p[1]); if(rounds<1 || rounds>1000000) return false;
                return MessageDigest.isEqual(Base64.getDecoder().decode(p[3]),derive(password,Base64.getDecoder().decode(p[2]),rounds));
            }
            if(p.length!=2) return false;
            MessageDigest digest=MessageDigest.getInstance("SHA-256"); digest.update(Base64.getDecoder().decode(p[0]));
            return MessageDigest.isEqual(Base64.getDecoder().decode(p[1]),digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch(Exception e) {return false;}
    }
    private static byte[] derive(String password,byte[] salt,int rounds) {
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt,rounds,256);
        try {return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        catch(Exception e) {throw new IllegalStateException("Password hashing failed",e);}
        finally {spec.clearPassword();}
    }
}
