package de.atstck.kitly.repository;

import de.atstck.kitly.entity.ApplicationSetting;
import de.atstck.kitly.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationSettingRepository extends JpaRepository<ApplicationSetting, UUID> {

    List<ApplicationSetting> findByTenant(Tenant tenant);

    List<ApplicationSetting> findByTenantAndIsPublic(Tenant tenant, Boolean isPublic);

    Optional<ApplicationSetting> findByTenantAndKey(Tenant tenant, String key);

    boolean existsByTenantAndKey(Tenant tenant, String key);

    void deleteByTenantAndKey(Tenant tenant, String key);
}

