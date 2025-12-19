# Entitlements System Documentation

## Overview

The entitlements system provides a flexible way to manage feature flags, limits, and quotas for tenants in the Kitly SaaS platform. It implements a hierarchical merge strategy where plan-based entitlements can be overridden at the tenant level.

## Architecture

### Components

1. **PlanCatalog** (`com.kitly.saas.entitlement.PlanCatalog`)
   - Static definitions of available subscription plans
   - Plans: `starter`, `pro`, `enterprise`
   - Each plan defines:
     - `features.ai_assistant` (boolean)
     - `limits.projects` (number or "unlimited")
     - `limits.api_calls_per_month` (number or "unlimited")

2. **EntitlementService** (`com.kitly.saas.entitlement.EntitlementService`)
   - Core business logic for entitlement computation
   - Merges entitlements in priority order: PLAN → ADDON → OVERRIDE
   - Manages version bumping for cache invalidation
   - Computes active seats count

3. **EntitlementController** (`com.kitly.saas.entitlement.controller.EntitlementController`)
   - REST API endpoints for accessing entitlements
   - Secured with Spring Security annotations

4. **Entity Listeners**
   - Event-driven version bumping using Spring's ApplicationEventPublisher
   - `EntitlementVersionBumpEvent` - Event published when version should bump
   - `EntitlementVersionBumpListener` - Handles version bump events
   - Services publish events on subscription/membership changes

## API Endpoints

### GET /api/plans
Returns the catalog of available plans.

**Response:**
```json
{
  "starter": {
    "code": "starter",
    "name": "Starter",
    "entitlements": {
      "features.ai_assistant": "false",
      "limits.projects": "10",
      "limits.api_calls_per_month": "1000"
    }
  },
  "pro": { ... },
  "enterprise": { ... }
}
```

### GET /api/tenants/{tenantId}/entitlements
Returns computed entitlements for a specific tenant.

**Security:** Requires `OWNER`, `ADMIN`, or `MEMBER` role and tenant access check.

**Response:**
```json
{
  "tenantId": "uuid",
  "planCode": "pro",
  "status": "ACTIVE",
  "seatsQuantity": 50,
  "activeSeats": 7,
  "entitlementVersion": 15,
  "items": [
    {
      "key": "features.ai_assistant",
      "value": "true",
      "source": "PLAN"
    },
    {
      "key": "limits.projects",
      "value": "100",
      "source": "PLAN"
    }
  ]
}
```

### GET /api/entitlements/me
Returns entitlements for the current tenant (from TenantContext).

**Security:** Requires `OWNER`, `ADMIN`, or `MEMBER` role. Uses `TenantContextHolder` to get current tenant.

**Response:** Same format as above.

## Entitlement Merging

Entitlements are computed using a hierarchical merge strategy:

1. **PLAN** - Base entitlements from the subscription plan
2. **ADDON** - Additional entitlements from add-ons (not yet implemented)
3. **OVERRIDE** - Tenant-specific overrides stored in the `entitlements` table

Later sources override earlier ones for the same entitlement key.

## Version Bumping

The `entitlement_versions` table tracks changes for cache invalidation. The version is automatically bumped through an event-driven mechanism:

1. Services (MembershipService, etc.) publish `EntitlementVersionBumpEvent` when changes occur
2. `EntitlementVersionBumpListener` handles these events in a separate transaction
3. This ensures thread-safe version bumping without static dependencies

Events are published when:
- Subscription is created or updated
- Membership is created, updated, or deleted
- Manual override entitlements are added/modified

This allows client applications to detect when they need to refresh cached entitlements.

## Security Considerations

### Access Control
- All entitlement endpoints are protected with Spring Security
- Tenant access is validated using `@TenantAccessCheck` annotation
- Users must be authenticated and have appropriate roles

### Data Isolation
- Entitlements are strictly scoped to tenants
- The `TenantContextHolder` ensures proper tenant isolation
- Entity listeners check for null tenants before bumping versions

### Input Validation
- Plan codes are validated against the static catalog
- Tenant IDs must be valid UUIDs
- Missing tenant context in `/api/entitlements/me` returns 400 Bad Request

### Rate Limiting
- Consider adding rate limiting to entitlement endpoints
- These endpoints are read-heavy and suitable for caching

## Database Schema

### entitlements table
Stores tenant-specific entitlement overrides.

```sql
- id (UUID)
- tenant_id (UUID, FK to tenants)
- feature_key (VARCHAR(100))
- feature_type (ENUM: BOOLEAN, LIMIT, QUOTA)
- limit_value (BIGINT, nullable)
- enabled (BOOLEAN)
- metadata (JSONB, nullable)
- created_at, updated_at (TIMESTAMP)
- UNIQUE(tenant_id, feature_key)
```

### entitlement_versions table
Tracks version for cache invalidation.

```sql
- id (UUID)
- tenant_id (UUID, FK to tenants)
- version (BIGINT)
- updated_at (TIMESTAMP)
- UNIQUE(tenant_id)
```

## Usage Examples

### Checking if AI Assistant is Enabled

```java
EntitlementResponse entitlements = entitlementService.computeEntitlements(tenantId);
Optional<EntitlementResponse.EntitlementItem> aiAssistant = entitlements.getItems()
    .stream()
    .filter(item -> "features.ai_assistant".equals(item.getKey()))
    .findFirst();

boolean isEnabled = aiAssistant
    .map(item -> "true".equalsIgnoreCase(item.getValue()))
    .orElse(false);
```

### Getting Project Limit

```java
EntitlementResponse entitlements = entitlementService.computeEntitlements(tenantId);
Optional<EntitlementResponse.EntitlementItem> projectLimit = entitlements.getItems()
    .stream()
    .filter(item -> "limits.projects".equals(item.getKey()))
    .findFirst();

String limitValue = projectLimit.map(EntitlementResponse.EntitlementItem::getValue)
    .orElse("0");

if ("unlimited".equalsIgnoreCase(limitValue)) {
    // No limit
} else {
    int limit = Integer.parseInt(limitValue);
    // Check against limit
}
```

### Adding a Tenant Override

```java
Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

Entitlement override = Entitlement.builder()
    .tenant(tenant)
    .featureKey("features.ai_assistant")
    .featureType(Entitlement.FeatureType.BOOLEAN)
    .enabled(true)
    .build();

entitlementRepository.save(override);
entitlementService.bumpEntitlementVersion(tenantId);
```

## Testing

The system includes comprehensive tests:

- `PlanCatalogTest` - Tests plan definitions and catalog operations
- `EntitlementServiceTest` - Tests entitlement computation and version bumping
- `EntitlementControllerTest` - Tests REST endpoints and security

All tests use Mockito for mocking dependencies and JUnit 5 for test execution.

## Future Enhancements

1. **Add-ons Support** - Implement ADDON source for additional entitlements
2. **Usage Tracking** - Track actual usage against limits
3. **Webhooks** - Notify external systems when entitlements change
4. **Admin UI** - Build interface for managing tenant overrides
5. **Audit Log** - Log all entitlement changes for compliance
6. **Caching** - Add Redis cache layer for frequently accessed entitlements
