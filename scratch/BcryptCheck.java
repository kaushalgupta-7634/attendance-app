// BcryptCheck.java
package scratch;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptCheck {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "password123";
        String hash = "$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xD0YM1b62Is19j5u";
        boolean matches = encoder.matches(rawPassword, hash);
        System.out.println("Matches: " + matches);
    }
}
