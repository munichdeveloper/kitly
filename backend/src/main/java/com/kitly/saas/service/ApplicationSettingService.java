package com.kitly.saas.service;

import com.kitly.saas.dto.ApplicationSettingDTO;
import com.kitly.saas.dto.ApplicationSettingRequest;
import com.kitly.saas.entity.ApplicationSetting;
import com.kitly.saas.entity.Tenant;
import com.kitly.saas.repository.ApplicationSettingRepository;
import com.kitly.saas.repository.TenantRepository;
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
public class ApplicationSettingService {

    private final ApplicationSettingRepository settingRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public List<ApplicationSettingDTO> getAllSettings(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        return settingRepository.findByTenant(tenant).stream()
                .map(ApplicationSettingDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApplicationSettingDTO getSetting(UUID tenantId, String key) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        ApplicationSetting setting = settingRepository.findByTenantAndKey(tenant, key)
                .orElseThrow(() -> new RuntimeException("Setting not found"));

        return ApplicationSettingDTO.fromEntity(setting);
    }

    @Transactional
    public ApplicationSettingDTO createOrUpdateSetting(UUID tenantId, ApplicationSettingRequest request, UUID userId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        ApplicationSetting setting = settingRepository.findByTenantAndKey(tenant, request.getKey())
                .orElse(ApplicationSetting.builder()
                        .tenant(tenant)
                        .key(request.getKey())
                        .build());

        setting.setValue(request.getValue());
        setting.setType(request.getType());
        setting.setDescription(request.getDescription());
        setting.setIsPublic(request.getIsPublic());
        setting.setUpdatedBy(userId);

        ApplicationSetting savedSetting = settingRepository.save(setting);
        log.info("Setting {} {} for tenant {}",
                setting.getId() == null ? "created" : "updated",
                request.getKey(),
                tenantId);

        return ApplicationSettingDTO.fromEntity(savedSetting);
    }

    @Transactional
    public void deleteSetting(UUID tenantId, String key) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        if (!settingRepository.existsByTenantAndKey(tenant, key)) {
            throw new RuntimeException("Setting not found");
        }

        settingRepository.deleteByTenantAndKey(tenant, key);
        log.info("Setting {} deleted for tenant {}", key, tenantId);
    }

    @Transactional
    public List<ApplicationSettingDTO> bulkUpdateSettings(UUID tenantId, List<ApplicationSettingRequest> requests, UUID userId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new RuntimeException("Tenant not found"));

        List<ApplicationSetting> settings = requests.stream()
                .map(request -> {
                    ApplicationSetting setting = settingRepository.findByTenantAndKey(tenant, request.getKey())
                            .orElse(ApplicationSetting.builder()
                                    .tenant(tenant)
                                    .key(request.getKey())
                                    .build());

                    setting.setValue(request.getValue());
                    setting.setType(request.getType());
                    setting.setDescription(request.getDescription());
                    setting.setIsPublic(request.getIsPublic());
                    setting.setUpdatedBy(userId);

                    return setting;
                })
                .collect(Collectors.toList());

        List<ApplicationSetting> savedSettings = settingRepository.saveAll(settings);
        log.info("Bulk updated {} settings for tenant {}", savedSettings.size(), tenantId);

        return savedSettings.stream()
                .map(ApplicationSettingDTO::fromEntity)
                .collect(Collectors.toList());
    }
}

