package com.resolveit.grievancemanagement.config;

import com.resolveit.grievancemanagement.entity.User;
import com.resolveit.grievancemanagement.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the database with sample L1 and L2 officers and default admin
 * for development and testing. Passwords can be overridden via environment variables.
 */
@Component
@Profile({"default", "dev"})
public class OfficerDataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(OfficerDataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.password:Admin@ResolveIT2026!}")
    private String adminPassword;

    @Value("${app.seed.officer-password:Officer@ResolveIT2026!}")
    private String defaultOfficerPassword;

    public static final String SYSTEM_ACTOR_USERNAME = "system_escalation_daemon";
    public static final String SYSTEM_ACTOR_EMAIL = "system@resolveit.local";

    public OfficerDataInitializer(UserRepository userRepository,
                                  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // Ensure the automated system escalation daemon actor exists for status history audit compliance
        seedSystemActor();

        // Ensure default system administrator exists for admin dashboard access
        seedAdminUser();

        // If there are already officers in the system, don't create duplicates
        if (!userRepository.findByRole(User.Role.OFFICER).isEmpty()) {
            return;
        }

        // Password for seeded officers (override with app.seed.officer-password)
        String encodedPassword = passwordEncoder.encode(defaultOfficerPassword);

        // Create 8 L1 officers
        for (int i = 1; i <= 8; i++) {
            String username = "officer_l1_" + i;
            if (userRepository.existsByUsername(username)) {
                continue;
            }
            User officer = new User();
            officer.setUsername(username);
            officer.setEmail("officer_l1_" + i + "@resolveit.local");
            officer.setPassword(encodedPassword);
            officer.setRole(User.Role.OFFICER);
            officer.setOfficerLevel(User.OfficerLevel.L1);
            officer.setFirstName("Officer L1-" + i);
            officer.setLastName("ResolveIT");
            officer.setPhoneNumber("99900010" + i);
            userRepository.save(officer);
        }

        // Create 2 L2 officers
        for (int i = 1; i <= 2; i++) {
            String username = "officer_l2_" + i;
            if (userRepository.existsByUsername(username)) {
                continue;
            }
            User officer = new User();
            officer.setUsername(username);
            officer.setEmail("officer_l2_" + i + "@resolveit.local");
            officer.setPassword(encodedPassword);
            officer.setRole(User.Role.OFFICER);
            officer.setOfficerLevel(User.OfficerLevel.L2);
            officer.setFirstName("Officer L2-" + i);
            officer.setLastName("ResolveIT");
            officer.setPhoneNumber("99900020" + i);
            userRepository.save(officer);
        }
    }

    private void seedSystemActor() {
        if (!userRepository.existsByUsername(SYSTEM_ACTOR_USERNAME)) {
            User systemUser = new User();
            systemUser.setUsername(SYSTEM_ACTOR_USERNAME);
            systemUser.setEmail(SYSTEM_ACTOR_EMAIL);
            systemUser.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            systemUser.setRole(User.Role.ADMIN);
            systemUser.setFirstName("System");
            systemUser.setLastName("Daemon");
            systemUser.setPhoneNumber("0000000000");
            userRepository.save(systemUser);
        }
    }

    private void seedAdminUser() {
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@resolveit.local");
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole(User.Role.ADMIN);
            admin.setFirstName("System");
            admin.setLastName("Administrator");
            admin.setPhoneNumber("9999999999");
            userRepository.save(admin);
            logger.info("Default administrator account initialized with username: 'admin'. To customize password, set app.admin.password property or ADMIN_PASSWORD env var.");
        }
    }
}
