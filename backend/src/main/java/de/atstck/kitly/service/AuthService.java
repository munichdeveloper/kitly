package de.atstck.kitly.service;

import de.atstck.kitly.dto.AuthResponse;
import de.atstck.kitly.dto.LoginRequest;
import de.atstck.kitly.dto.SignupRequest;
import de.atstck.kitly.dto.TenantRequest;
import de.atstck.kitly.entity.EmailVerificationToken;
import de.atstck.kitly.entity.PasswordResetToken;
import de.atstck.kitly.entity.Role;
import de.atstck.kitly.entity.User;
import de.atstck.kitly.repository.EmailVerificationTokenRepository;
import de.atstck.kitly.repository.PasswordResetTokenRepository;
import de.atstck.kitly.repository.RoleRepository;
import de.atstck.kitly.repository.UserRepository;
import de.atstck.kitly.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final TenantService tenantService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    @Value("${app.email-verification.enabled:false}")
    private boolean emailVerificationEnabled;

    @Value("${app.email-verification.token-validity-hours:24}")
    private int tokenValidityHours;

    @Value("${app.password-reset.token-validity-hours:24}")
    private int passwordResetTokenValidityHours;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       JwtUtil jwtUtil, TenantService tenantService,
                       EmailVerificationTokenRepository emailVerificationTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.tenantService = tenantService;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already in use");
        }

        // Wenn E-Mail-Verifizierung aktiviert ist, Token erstellen und E-Mail senden
        if (emailVerificationEnabled) {
            return createVerificationTokenAndSendEmail(request);
        }

        // Ansonsten direkt Benutzer erstellen (alte Logik)
        return createUserDirectly(request);
    }

    private AuthResponse createVerificationTokenAndSendEmail(SignupRequest request) {
        // Prüfen, ob bereits ein Token für diese E-Mail existiert
        emailVerificationTokenRepository.findByEmail(request.getEmail())
                .ifPresent(emailVerificationTokenRepository::delete);

        // Neues Token erstellen
        String token = UUID.randomUUID().toString();

        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .token(token)
                .email(request.getEmail())
                .username(request.getUsername())
                .encodedPassword(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .companyName(request.getCompanyName())
                .expiryDate(LocalDateTime.now().plusHours(tokenValidityHours))
                .verified(false)
                .build();

        emailVerificationTokenRepository.save(verificationToken);

        String name = request.getFirstName() != null ? request.getFirstName() : request.getUsername();

        // E-Mail senden
        emailService.sendVerificationEmail(request.getEmail(), name, token, request.getUsername());

        // Antwort ohne JWT Token zurückgeben
        return new AuthResponse(null, null, request.getEmail());
    }

    private AuthResponse createUserDirectly(SignupRequest request) {
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .isActive(true)
                .emailVerified(true) // Direkt als verifiziert markieren
                .build();

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        roles.add(userRole);
        user.setRoles(roles);

        userRepository.save(user);

        // Create default tenant
        createDefaultTenant(request, user);

        // Generate token for the new user
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities("ROLE_USER")
                .build();

        String token = jwtUtil.generateToken(userDetails);

        return new AuthResponse(token, user.getUsername(), user.getEmail());
    }

    @Transactional
    public AuthResponse verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));

        if (verificationToken.isExpired()) {
            throw new RuntimeException("Verification token has expired");
        }

        if (verificationToken.getVerified()) {
            throw new RuntimeException("Email already verified");
        }

        if (userRepository.existsByUsername(verificationToken.getUsername())) {
            throw new RuntimeException("Username is already taken");
        }

        if (userRepository.existsByEmail(verificationToken.getEmail())) {
            throw new RuntimeException("Email is already in use");
        }

        // Benutzer erstellen
        User user = User.builder()
                .username(verificationToken.getUsername())
                .email(verificationToken.getEmail())
                .password(verificationToken.getEncodedPassword()) // Bereits verschlüsselt
                .firstName(verificationToken.getFirstName())
                .lastName(verificationToken.getLastName())
                .isActive(true)
                .emailVerified(true)
                .build();

        Set<Role> roles = new HashSet<>();
        Role userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        roles.add(userRole);
        user.setRoles(roles);

        userRepository.save(user);

        // Create default tenant
        SignupRequest request = new SignupRequest();
        request.setUsername(verificationToken.getUsername());
        request.setEmail(verificationToken.getEmail());
        request.setFirstName(verificationToken.getFirstName());
        request.setLastName(verificationToken.getLastName());
        request.setCompanyName(verificationToken.getCompanyName());

        createDefaultTenant(request, user);

        // Token als verifiziert markieren
        verificationToken.setVerified(true);
        emailVerificationTokenRepository.save(verificationToken);

        // Generate token for the new user
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities("ROLE_USER")
                .build();

        String jwtToken = jwtUtil.generateToken(userDetails);

        return new AuthResponse(jwtToken, user.getUsername(), user.getEmail());
    }

    private void createDefaultTenant(SignupRequest request, User user) {
        String workspaceName;
        String baseSlug;

        if (request.getCompanyName() != null && !request.getCompanyName().trim().isEmpty()) {
            workspaceName = request.getCompanyName();
            baseSlug = request.getCompanyName().toLowerCase().replaceAll("[^a-z0-9]", "");
            if (baseSlug.isEmpty()) {
                baseSlug = "workspace";
            }
        } else {
            workspaceName = (request.getFirstName() != null ? request.getFirstName() : request.getUsername()) + "'s Workspace";
            baseSlug = request.getUsername().toLowerCase().replaceAll("[^a-z0-9]", "") + "-workspace";
        }

        String slug = baseSlug;

        // Simple retry logic for slug uniqueness
        int attempt = 0;
        while (attempt < 3) {
            try {
                TenantRequest tenantRequest = TenantRequest.builder()
                        .name(workspaceName)
                        .slug(slug)
                        .build();
                tenantService.createTenant(tenantRequest, user.getUsername());
                break;
            } catch (Exception e) {
                attempt++;
                slug = baseSlug + "-" + System.currentTimeMillis();
            }
        }
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return new AuthResponse(token, user.getUsername(), user.getEmail());
    }

    @Transactional
    public void initiatePasswordReset(String email) {
        // Benutzer suchen
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Kein Benutzer mit dieser E-Mail-Adresse gefunden"));

        // Alte Tokens für diesen Benutzer löschen
        passwordResetTokenRepository.deleteByUser(user);

        // Neues Token erstellen
        String token = UUID.randomUUID().toString();
        LocalDateTime expiryDate = LocalDateTime.now().plusHours(passwordResetTokenValidityHours);

        PasswordResetToken resetToken = new PasswordResetToken(token, user, expiryDate);
        passwordResetTokenRepository.save(resetToken);

        // E-Mail senden
        emailService.sendPasswordResetEmail(user.getEmail(), token, user.getFirstName() != null ? user.getFirstName() : user.getUsername());
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        // Token suchen
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Ungültiger oder abgelaufener Reset-Link"));

        // Prüfen ob Token bereits verwendet wurde
        if (resetToken.isUsed()) {
            throw new RuntimeException("Dieser Reset-Link wurde bereits verwendet");
        }

        // Prüfen ob Token abgelaufen ist
        if (resetToken.isExpired()) {
            throw new RuntimeException("Dieser Reset-Link ist abgelaufen. Bitte fordere einen neuen an");
        }

        // Benutzer holen
        User user = resetToken.getUser();

        // Neues Passwort setzen
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Token als verwendet markieren
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }
}
