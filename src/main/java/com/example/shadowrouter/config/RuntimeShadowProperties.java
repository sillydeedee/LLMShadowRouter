package com.example.shadowrouter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Boot-time defaults for runtime-tunable shadow behavior.
 *
 * {@code shadowRoutingPercentage} is the initial mirror rate; live updates go
 * through {@link com.example.shadowrouter.service.ShadowRoutingConfig}.
 */
@ConfigurationProperties(prefix = "shadow")
public record RuntimeShadowProperties(
        int routingPercentage,
        String sqlitePath) {
}
