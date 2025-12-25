package com.kitly.saas.dto;

import com.kitly.saas.entity.ApplicationSetting;
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
public class ApplicationSettingDTO {
    private UUID id;
    private String key;
    private String value;
    private ApplicationSetting.SettingType type;
    private String description;
    private Boolean isPublic;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID updatedBy;

    public static ApplicationSettingDTO fromEntity(ApplicationSetting setting) {
        return ApplicationSettingDTO.builder()
                .id(setting.getId())
                .key(setting.getKey())
                .value(setting.getValue())
                .type(setting.getType())
                .description(setting.getDescription())
                .isPublic(setting.getIsPublic())
                .createdAt(setting.getCreatedAt())
                .updatedAt(setting.getUpdatedAt())
                .updatedBy(setting.getUpdatedBy())
                .build();
    }
}

