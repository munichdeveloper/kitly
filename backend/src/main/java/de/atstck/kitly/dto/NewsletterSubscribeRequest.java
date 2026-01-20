package de.atstck.kitly.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterSubscribeRequest {

    @NotBlank(message = "E-Mail ist erforderlich")
    @Email(message = "Ungültige E-Mail-Adresse")
    private String email;

    @NotBlank(message = "Kanal ist erforderlich")
    private String channel;

    private String firstName;

    private String lastName;

    private String locale;
}
