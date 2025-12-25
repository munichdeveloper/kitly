package com.kitly.saas.dto;

import com.kitly.saas.entity.PlatformSetting;
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
public class PlatformSettingDTO {
    private UUID id;
    private String key;
    private String value;
    private PlatformSetting.SettingType type;
    private String description;
    private Boolean isEncrypted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID updatedBy;
}

