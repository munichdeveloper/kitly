package de.atstck.kitly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsletterSubscriptionResponse {

    private UUID id;
    private String email;
    private String channel;
    private boolean active;
    private boolean confirmed;
    private LocalDateTime subscribedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime unsubscribedAt;
    private String firstName;
    private String lastName;
    private String locale;
}
