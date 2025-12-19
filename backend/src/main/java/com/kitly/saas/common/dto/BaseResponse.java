package com.kitly.saas.common.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Base response class for all API responses.
 * Provides common fields like timestamp that can be extended by specific response DTOs.
 */
@Data
@NoArgsConstructor
@SuperBuilder
public abstract class BaseResponse {
    
    private LocalDateTime timestamp;
}
