package com.kitly.saas.dto;

import com.kitly.saas.entity.ApplicationSetting;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationSettingRequest {

    @NotBlank(message = "Setting key is required")
    @Size(max = 100, message = "Key must not exceed 100 characters")
    private String key;

    private String value;

    @NotNull(message = "Setting type is required")
    private ApplicationSetting.SettingType type;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Builder.Default
    private Boolean isPublic = false;
}

