package com.example.shadowrouter.controller;

import com.example.shadowrouter.service.ShadowRoutingConfig;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runtime configuration API for shadow traffic mirroring.
 */
@RestController
public class ConfigController {

    private final ShadowRoutingConfig shadowRoutingConfig;

    public ConfigController(ShadowRoutingConfig shadowRoutingConfig) {
        this.shadowRoutingConfig = shadowRoutingConfig;
    }

    @GetMapping("/config")
    public ConfigResponse getConfig() {
        return new ConfigResponse(shadowRoutingConfig.getShadowRoutingPercentage());
    }

    /**
     * Updates the percentage of chat requests that are mirrored to the candidate.
     * Example: {@code {"shadowRoutingPercentage": 50}} throttles mirroring to half.
     */
    @PutMapping("/config")
    public ConfigResponse updateConfig(@Valid @RequestBody ConfigUpdateRequest request) {
        shadowRoutingConfig.setShadowRoutingPercentage(request.shadowRoutingPercentage());
        return new ConfigResponse(shadowRoutingConfig.getShadowRoutingPercentage());
    }

    public record ConfigUpdateRequest(
            @NotNull
            @Min(0)
            @Max(100)
            Integer shadowRoutingPercentage) {
    }

    public record ConfigResponse(int shadowRoutingPercentage) {
    }
}
