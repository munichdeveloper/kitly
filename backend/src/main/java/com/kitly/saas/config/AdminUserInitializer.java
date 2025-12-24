package com.kitly.saas.config;

import com.kitly.saas.entity.Role;
import com.kitly.saas.entity.User;
import com.kitly.saas.repository.RoleRepository;
import com.kitly.saas.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserInitializer {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_EMAIL = "admin@kitly.local";
    private static final int PASSWORD_LENGTH = 16;
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";

    @PostConstruct
    public void initAdminUser() {
        // Prüfen, ob Admin-User bereits existiert
        if (userRepository.existsByUsername(ADMIN_USERNAME)) {
            log.info("Admin-Benutzer '{}' existiert bereits.", ADMIN_USERNAME);
            return;
        }

        // Admin-Rolle finden oder erstellen
        Role adminRole = roleRepository.findByName(Role.RoleName.ROLE_ADMIN)
                .orElseGet(() -> {
                    Role newRole = Role.builder()
                            .name(Role.RoleName.ROLE_ADMIN)
                            .build();
                    return roleRepository.save(newRole);
                });

        // Passwort generieren
        String generatedPassword = generateSecurePassword();

        // Admin-Benutzer erstellen
        Set<Role> roles = new HashSet<>();
        roles.add(adminRole);

        User adminUser = User.builder()
                .username(ADMIN_USERNAME)
                .email(ADMIN_EMAIL)
                .password(passwordEncoder.encode(generatedPassword))
                .firstName("System")
                .lastName("Administrator")
                .roles(roles)
                .isActive(true)
                .build();

        userRepository.save(adminUser);

        // Passwort auf der Konsole ausgeben
        log.warn("╔═══════════════════════════════════════════════════════════════════╗");
        log.warn("║           ADMIN-BENUTZER ERFOLGREICH ERSTELLT                     ║");
        log.warn("╠═══════════════════════════════════════════════════════════════════╣");
        log.warn("║  Benutzername: {}                                            ║", ADMIN_USERNAME);
        log.warn("║  E-Mail:       {}                                  ║", ADMIN_EMAIL);
        log.warn("║  Passwort:     {}                          ║", generatedPassword);
        log.warn("╠═══════════════════════════════════════════════════════════════════╣");
        log.warn("║  WICHTIG: Bitte ändern Sie das Passwort nach dem ersten Login!   ║");
        log.warn("║  Speichern Sie diese Anmeldedaten an einem sicheren Ort.         ║");
        log.warn("╚═══════════════════════════════════════════════════════════════════╝");
    }

    private String generateSecurePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);

        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            int index = random.nextInt(PASSWORD_CHARS.length());
            password.append(PASSWORD_CHARS.charAt(index));
        }

        return password.toString();
    }
}

