package com.kitly.saas.integration.builder;

import com.kitly.saas.entity.User;
import com.kitly.saas.entity.Role;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Set;

/**
 * Builder for User test data
 */
public class UserTestBuilder {
    
    private String username = "testuser";
    private String email = "test@example.com";
    private String password = "password123";
    private String firstName = "Test";
    private String lastName = "User";
    private boolean isActive = true;
    private Set<Role> roles = new HashSet<>();
    
    public static UserTestBuilder aUser() {
        return new UserTestBuilder();
    }
    
    public UserTestBuilder withUsername(String username) {
        this.username = username;
        return this;
    }
    
    public UserTestBuilder withEmail(String email) {
        this.email = email;
        return this;
    }
    
    public UserTestBuilder withPassword(String password) {
        this.password = password;
        return this;
    }
    
    public UserTestBuilder withFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }
    
    public UserTestBuilder withLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }
    
    public UserTestBuilder withActive(boolean isActive) {
        this.isActive = isActive;
        return this;
    }
    
    public UserTestBuilder withRole(Role role) {
        this.roles.add(role);
        return this;
    }
    
    public User build(PasswordEncoder passwordEncoder) {
        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .firstName(firstName)
                .lastName(lastName)
                .isActive(isActive)
                .build();
        user.setRoles(roles);
        return user;
    }
    
    public User buildWithPlainPassword() {
        User user = User.builder()
                .username(username)
                .email(email)
                .password(password)
                .firstName(firstName)
                .lastName(lastName)
                .isActive(isActive)
                .build();
        user.setRoles(roles);
        return user;
    }
}
