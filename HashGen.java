import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class HashGen { public static void main(String[] a) { System.out.println(new BCryptPasswordEncoder().encode("123456")); } }
