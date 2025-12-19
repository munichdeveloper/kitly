package com.kitly.saas.common.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {
    
    private LocalDateTime timestamp;
    private Integer status;
    private String error;
    private String message;
    private String code;  // Error code for specific error types
    private Map<String, Object> details;  // Additional error details
    private Map<String, String> validationErrors;
}
