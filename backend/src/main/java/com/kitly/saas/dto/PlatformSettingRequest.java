package com.kitly.saas.dto;

import com.kitly.saas.entity.PlatformSetting;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformSettingRequest {
    @NotBlank(message = "Key is required")
    private String key;

    private String value;

    private PlatformSetting.SettingType type;

    private String description;

    private Boolean isEncrypted;
}

