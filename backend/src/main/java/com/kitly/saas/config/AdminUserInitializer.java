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

    private static final String PLATFORM_ADMIN_USERNAME = "platform-admin";
    private static final String PLATFORM_ADMIN_EMAIL = "platform-admin@kitly.local";
    private static final int PASSWORD_LENGTH = 16;
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";

    @PostConstruct
    public void initAdminUser() {
        // Prüfen, ob Platform-Admin bereits existiert
        if (userRepository.existsByUsername(PLATFORM_ADMIN_USERNAME)) {
            log.info("Platform-Admin '{}' existiert bereits.", PLATFORM_ADMIN_USERNAME);
            return;
        }

        // Platform-Admin-Rolle finden oder erstellen
        Role platformAdminRole = roleRepository.findByName(Role.RoleName.ROLE_PLATFORM_ADMIN)
                .orElseGet(() -> {
                    log.info("Platform-Admin-Rolle nicht gefunden, erstelle neue Rolle...");
                    Role newRole = Role.builder()
                            .name(Role.RoleName.ROLE_PLATFORM_ADMIN)
                            .build();
                    return roleRepository.save(newRole);
                });

        // Optional: Auch ROLE_ADMIN hinzufügen für erweiterte Berechtigungen
        Role adminRole = roleRepository.findByName(Role.RoleName.ROLE_ADMIN)
                .orElseGet(() -> {
                    Role newRole = Role.builder()
                            .name(Role.RoleName.ROLE_ADMIN)
                            .build();
                    return roleRepository.save(newRole);
                });

        // Passwort generieren
        String generatedPassword = generateSecurePassword();

        // Platform-Admin-Benutzer erstellen
        Set<Role> roles = new HashSet<>();
        roles.add(platformAdminRole);
        roles.add(adminRole); // Auch normale Admin-Rechte für volle Funktionalität

        User platformAdminUser = User.builder()
                .username(PLATFORM_ADMIN_USERNAME)
                .email(PLATFORM_ADMIN_EMAIL)
                .password(passwordEncoder.encode(generatedPassword))
                .firstName("Platform")
                .lastName("Administrator")
                .roles(roles)
                .isActive(true)
                .build();

        userRepository.save(platformAdminUser);

        // Passwort auf der Konsole ausgeben
        log.warn("╔═══════════════════════════════════════════════════════════════════╗");
        log.warn("║         PLATFORM-ADMIN ERFOLGREICH ERSTELLT                       ║");
        log.warn("╠═══════════════════════════════════════════════════════════════════╣");
        log.warn("║  Benutzername: {}                                   ║", PLATFORM_ADMIN_USERNAME);
        log.warn("║  E-Mail:       {}                       ║", PLATFORM_ADMIN_EMAIL);
        log.warn("║  Passwort:     {}                          ║", generatedPassword);
        log.warn("╠═══════════════════════════════════════════════════════════════════╣");
        log.warn("║  ROLLEN: ROLE_PLATFORM_ADMIN, ROLE_ADMIN                          ║");
        log.warn("╠═══════════════════════════════════════════════════════════════════╣");
        log.warn("║  WICHTIG: Bitte ändern Sie das Passwort nach dem ersten Login!   ║");
        log.warn("║  Speichern Sie diese Anmeldedaten an einem sicheren Ort.         ║");
        log.warn("║  Mit diesem Account können Sie auf /admin/platform zugreifen.    ║");
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

