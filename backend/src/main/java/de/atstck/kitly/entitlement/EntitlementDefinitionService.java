package de.atstck.kitly.entitlement;

import de.atstck.kitly.common.exception.ResourceNotFoundException;
import de.atstck.kitly.entity.EntitlementDefinition;
import de.atstck.kitly.repository.EntitlementDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing entitlement definitions.
 */
@Service
public class EntitlementDefinitionService {

    private final EntitlementDefinitionRepository repository;

    public EntitlementDefinitionService(EntitlementDefinitionRepository repository) {
        this.repository = repository;
    }

    /**
     * Get all entitlement definitions
     */
    @Transactional(readOnly = true)
    public List<EntitlementDefinition> getAllDefinitions() {
        return repository.findAllByOrderByTypeAscNameAsc();
    }

    /**
     * Get entitlement definitions by type
     */
    @Transactional(readOnly = true)
    public List<EntitlementDefinition> getDefinitionsByType(EntitlementType type) {
        return repository.findByType(type);
    }

    /**
     * Get entitlement definition by type and name
     */
    @Transactional(readOnly = true)
    public Optional<EntitlementDefinition> getDefinition(EntitlementType type, String name) {
        return repository.findByTypeAndName(type, name);
    }

    /**
     * Get entitlement definition by ID
     */
    @Transactional(readOnly = true)
    public EntitlementDefinition getDefinitionById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entitlement definition not found"));
    }

    /**
     * Create a new entitlement definition
     */
    @Transactional
    public EntitlementDefinition createDefinition(
            EntitlementType type,
            String name,
            String displayName,
            String description,
            String defaultValue) {

        // Check if already exists
        if (repository.findByTypeAndName(type, name).isPresent()) {
            throw new IllegalArgumentException(
                    "Entitlement definition already exists: " + type.buildKey(name));
        }

        EntitlementDefinition definition = EntitlementDefinition.builder()
                .type(type)
                .name(name)
                .displayName(displayName)
                .description(description)
                .defaultValue(defaultValue)
                .build();

        return repository.save(definition);
    }

    /**
     * Update an entitlement definition
     */
    @Transactional
    public EntitlementDefinition updateDefinition(
            UUID id,
            String displayName,
            String description,
            String defaultValue) {

        EntitlementDefinition definition = getDefinitionById(id);

        if (displayName != null) {
            definition.setDisplayName(displayName);
        }
        if (description != null) {
            definition.setDescription(description);
        }
        if (defaultValue != null) {
            definition.setDefaultValue(defaultValue);
        }

        return repository.save(definition);
    }

    /**
     * Delete an entitlement definition
     * Note: This will fail if the definition is used by any plan
     */
    @Transactional
    public void deleteDefinition(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Entitlement definition not found");
        }
        repository.deleteById(id);
    }

    /**
     * Create definition if it doesn't exist
     */
    @Transactional
    public EntitlementDefinition getOrCreateDefinition(
            EntitlementType type,
            String name,
            String displayName,
            String description,
            String defaultValue) {

        return repository.findByTypeAndName(type, name)
                .orElseGet(() -> createDefinition(type, name, displayName, description, defaultValue));
    }
}

