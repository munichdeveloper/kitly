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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class AuthService {

    private static final String EMAIL_VERIFICATION_ENABLED_PLATFORM_KEY_SUFFIX = ".email.verification.enabled";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final TenantService tenantService;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final PlatformSettingService platformSettingService;

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
                       EmailService emailService,
                       PlatformSettingService platformSettingService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.tenantService = tenantService;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.platformSettingService = platformSettingService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email is already in use");
        }

        // App-spezifisches Plattform-Setting nutzen, ansonsten globales Fallback
        if (isEmailVerificationEnabled(request.getAppId())) {
            return createVerificationTokenAndSendEmail(request);
        }

        // Ansonsten direkt Benutzer erstellen (alte Logik)
        return createUserDirectly(request);
    }

    private boolean isEmailVerificationEnabled(String appId) {
        if (appId == null || appId.trim().isEmpty()) {
            return emailVerificationEnabled;
        }

        try {
            String settingKey = appId.trim() + EMAIL_VERIFICATION_ENABLED_PLATFORM_KEY_SUFFIX;
            String settingValue = platformSettingService.getSettingValue(settingKey, null);

            if (settingValue == null || settingValue.trim().isEmpty()) {
                return emailVerificationEnabled;
            }

            if (!"true".equalsIgnoreCase(settingValue) && !"false".equalsIgnoreCase(settingValue)) {
                log.warn("Invalid value '{}' for platform setting '{}' (appId '{}'). Falling back to global config.",
                        settingValue, settingKey, appId);
                return emailVerificationEnabled;
            }

            return Boolean.parseBoolean(settingValue);
        } catch (RuntimeException ex) {
            log.warn("Failed to resolve app-specific email verification setting for appId '{}'. Falling back to global config.",
                    appId, ex);
            return emailVerificationEnabled;
        }
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
        // TenantService will automatically link the user to the created tenant
        try {
            createDefaultTenant(request, user.getUsername());
        } catch (Exception e) {
            log.warn("Failed to create default tenant for user: {}, but user account was created. Error: {}",
                     user.getUsername(), e.getMessage());
        }

        // Generate token for the new user
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities("ROLE_USER")
                .build();

        String token = jwtUtil.generateToken(userDetails);

        return new AuthResponse(token, user.getUsername(), user.getEmail());
    }

    /**
     * Verify email and create user account with default tenant.
     * If tenant creation fails, user is still created but without a default tenant.
     * No @Transactional to avoid transaction management issues with nested transactions.
     */
    public AuthResponse verifyEmail(String token) {
        try {
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

            // STEP 1: Create user FIRST (in this transaction, so it's persisted before tenant creation)
            User user = User.builder()
                    .username(verificationToken.getUsername())
                    .email(verificationToken.getEmail())
                    .password(verificationToken.getEncodedPassword())
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
            log.info("User created successfully: {}", verificationToken.getUsername());

            // STEP 2: Try to create tenant AFTER user is created
            // If this fails, the user is already created and will be committed
            SignupRequest tenantRequest = new SignupRequest();
            tenantRequest.setUsername(verificationToken.getUsername());
            tenantRequest.setEmail(verificationToken.getEmail());
            tenantRequest.setFirstName(verificationToken.getFirstName());
            tenantRequest.setLastName(verificationToken.getLastName());
            tenantRequest.setCompanyName(verificationToken.getCompanyName());

            try {
                createDefaultTenant(tenantRequest, verificationToken.getUsername());
                log.info("Tenant creation successful for user: {}", verificationToken.getUsername());
            } catch (Exception e) {
                // Log the error but continue - user was successfully created
                // The user can create a tenant manually later
                log.warn("Failed to create default tenant for user: {}, but user account was created. Error: {}",
                         verificationToken.getUsername(), e.getMessage());
            }

            // STEP 3: Mark token as verified
            verificationToken.setVerified(true);
            emailVerificationTokenRepository.save(verificationToken);

            // STEP 4: Generate JWT token for the new user
            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username(user.getUsername())
                    .password(user.getPassword())
                    .authorities("ROLE_USER")
                    .build();

            String jwtToken = jwtUtil.generateToken(userDetails);

            log.info("Email verification completed successfully for user: {}", user.getUsername());

            return new AuthResponse(jwtToken, user.getUsername(), user.getEmail());
        } catch (RuntimeException e) {
            log.error("Error during email verification: {}", e.getMessage(), e);


            throw e;
        }
    }

    @Transactional
    private void createDefaultTenant(SignupRequest request, String username) {
        String workspaceName;
        String baseSlug;

        if (request.getCompanyName() != null && !request.getCompanyName().trim().isEmpty()) {
            workspaceName = request.getCompanyName();
            baseSlug = request.getCompanyName().toLowerCase().replaceAll("[^a-z0-9]", "");
            if (baseSlug.isEmpty()) {
                baseSlug = "workspace";
            }
        } else {
            workspaceName = (request.getFirstName() != null ? request.getFirstName() : username) + "'s Workspace";
            baseSlug = username.toLowerCase().replaceAll("[^a-z0-9]", "") + "-workspace";
        }

        String slug = baseSlug;
        log.debug("Creating default tenant for user: {} with base slug: {}", username, baseSlug);

        // Simple retry logic for slug uniqueness
        int attempt = 0;
        RuntimeException lastException = null;

        while (attempt < 3) {
            try {
                log.debug("Attempt {} to create tenant with slug: {}", attempt + 1, slug);
                TenantRequest tenantRequest = TenantRequest.builder()
                        .name(workspaceName)
                        .slug(slug)
                        .build();
                tenantService.createTenant(tenantRequest, username);
                log.info("Successfully created default tenant for user: {} with slug: {}", username, slug);
                return; // Success - exit method
            } catch (Exception e) {
                lastException = new RuntimeException("Attempt " + (attempt + 1) + " failed: " + e.getMessage(), e);
                log.warn("Failed to create tenant on attempt {}: {}. Retrying with different slug.", attempt + 1, e.getMessage());
                attempt++;
                slug = baseSlug + "-" + System.currentTimeMillis();
            }
        }

        log.error("Failed to create default tenant for user: {} after {} attempts", username, attempt);
        if (lastException != null) {
            throw lastException;
        }
        throw new RuntimeException("Failed to create default tenant after multiple attempts");
    }


    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        try {
            // Authenticate with email and password
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            // Get user details from authentication
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String token = jwtUtil.generateToken(userDetails);

            // Load full user entity
            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            log.info("Login successful for user: {}", user.getUsername());
            return new AuthResponse(token, user.getUsername(), user.getEmail());

        } catch (AuthenticationException e) {
            // BadCredentialsException, UsernameNotFoundException, etc.
            log.warn("Login failed for email: {} - {}", request.getEmail(), e.getMessage());
            throw new RuntimeException("Invalid email or password");
        } catch (Exception e) {
            log.error("Unexpected error during login for email: {}", request.getEmail(), e);
            throw new RuntimeException("Login failed: " + e.getMessage());
        }
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
