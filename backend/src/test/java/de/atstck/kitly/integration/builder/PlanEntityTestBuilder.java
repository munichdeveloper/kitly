package de.atstck.kitly.integration.builder;

import de.atstck.kitly.entity.PlanEntity;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Builder for PlanEntity test data
 */
public class PlanEntityTestBuilder {

    private String code = "test_plan";
    private String name = "Test Plan";
    private String description = "Test plan description";
    private Boolean isActive = true;
    private Integer displayOrder = 0;
    private String stripeStatus = "active";
    private Map<String, Object> metadata;

    public static PlanEntityTestBuilder aPlanEntity() {
        return new PlanEntityTestBuilder();
    }

    public PlanEntityTestBuilder withCode(String code) {
        this.code = code;
        return this;
    }

    public PlanEntityTestBuilder withName(String name) {
        this.name = name;
        return this;
    }

    public PlanEntityTestBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public PlanEntityTestBuilder withIsActive(Boolean isActive) {
        this.isActive = isActive;
        return this;
    }

    public PlanEntityTestBuilder withDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
        return this;
    }

    public PlanEntityTestBuilder withStripeStatus(String stripeStatus) {
        this.stripeStatus = stripeStatus;
        return this;
    }

    public PlanEntityTestBuilder withMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
        return this;
    }

    public PlanEntity build() {
        return PlanEntity.builder()
                .code(code)
                .name(name)
                .description(description)
                .isActive(isActive)
                .displayOrder(displayOrder)
                .stripeStatus(stripeStatus)
                .metadata(metadata)
                .build();
    }
}

