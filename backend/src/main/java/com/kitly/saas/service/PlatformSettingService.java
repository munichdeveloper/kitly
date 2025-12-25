package com.kitly.saas.service;

import com.kitly.saas.dto.PlatformSettingDTO;
import com.kitly.saas.dto.PlatformSettingRequest;
import com.kitly.saas.entity.PlatformSetting;
import com.kitly.saas.repository.PlatformSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformSettingService {

    private final PlatformSettingRepository platformSettingRepository;

    @Transactional(readOnly = true)
    public List<PlatformSettingDTO> getAllSettings() {
        return platformSettingRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PlatformSettingDTO getSetting(String key) {
        PlatformSetting setting = platformSettingRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Platform setting not found: " + key));
        return toDTO(setting);
    }

    @Transactional(readOnly = true)
    public String getSettingValue(String key, String defaultValue) {
        return platformSettingRepository.findByKey(key)
                .map(PlatformSetting::getValue)
                .orElse(defaultValue);
    }

    @Transactional
    public PlatformSettingDTO createOrUpdateSetting(PlatformSettingRequest request, UUID updatedBy) {
        PlatformSetting setting = platformSettingRepository.findByKey(request.getKey())
                .orElse(PlatformSetting.builder()
                        .key(request.getKey())
                        .build());

        setting.setValue(request.getValue());
        setting.setType(request.getType() != null ? request.getType() : PlatformSetting.SettingType.STRING);
        setting.setDescription(request.getDescription());
        setting.setIsEncrypted(request.getIsEncrypted() != null ? request.getIsEncrypted() : false);
        setting.setUpdatedBy(updatedBy);

        setting = platformSettingRepository.save(setting);
        log.info("Platform setting {} updated by user {}", request.getKey(), updatedBy);

        return toDTO(setting);
    }

    @Transactional
    public void deleteSetting(String key) {
        PlatformSetting setting = platformSettingRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Platform setting not found: " + key));
        platformSettingRepository.delete(setting);
        log.info("Platform setting {} deleted", key);
    }

    @Transactional
    public List<PlatformSettingDTO> bulkUpdateSettings(List<PlatformSettingRequest> requests, UUID updatedBy) {
        return requests.stream()
                .map(request -> createOrUpdateSetting(request, updatedBy))
                .collect(Collectors.toList());
    }

    private PlatformSettingDTO toDTO(PlatformSetting setting) {
        return PlatformSettingDTO.builder()
                .id(setting.getId())
                .key(setting.getKey())
                .value(setting.getValue())
                .type(setting.getType())
                .description(setting.getDescription())
                .isEncrypted(setting.getIsEncrypted())
                .createdAt(setting.getCreatedAt())
                .updatedAt(setting.getUpdatedAt())
                .updatedBy(setting.getUpdatedBy())
                .build();
    }
}

