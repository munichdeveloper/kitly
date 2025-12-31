package de.atstck.kitly.config;

import de.atstck.kitly.entity.Role;
import de.atstck.kitly.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {
    
    @Bean
    public CommandLineRunner initData(RoleRepository roleRepository) {
        return args -> {
            // Initialize roles if they don't exist
            for (Role.RoleName roleName : Role.RoleName.values()) {
                if (roleRepository.findByName(roleName).isEmpty()) {
                    Role role = Role.builder()
                            .name(roleName)
                            .build();
                    roleRepository.save(role);
                }
            }
        };
    }
}
