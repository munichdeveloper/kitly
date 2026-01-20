package de.atstck.kitly.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "newsletter_subscriptions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"email", "channel"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsletterSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotBlank
    @Email
    @Column(nullable = false)
    private String email;

    @NotBlank
    @Column(nullable = false, length = 50)
    private String channel;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(length = 100)
    private String confirmationToken;

    @Column
    private LocalDateTime confirmedAt;

    @Column(nullable = false)
    private LocalDateTime subscribedAt;

    @Column
    private LocalDateTime unsubscribedAt;

    @Column(length = 100)
    private String unsubscribeToken;

    @Column(length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Column(length = 10)
    private String locale;

    @PrePersist
    protected void onCreate() {
        if (subscribedAt == null) {
            subscribedAt = LocalDateTime.now();
        }
        if (unsubscribeToken == null) {
            unsubscribeToken = UUID.randomUUID().toString();
        }
        if (confirmationToken == null) {
            confirmationToken = UUID.randomUUID().toString();
        }
    }
}
