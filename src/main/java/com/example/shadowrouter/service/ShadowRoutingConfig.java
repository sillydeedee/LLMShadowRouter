package com.example.shadowrouter.service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.shadowrouter.config.RuntimeShadowProperties;
import com.example.shadowrouter.exception.InvalidConfigException;

import org.springframework.stereotype.Component;

/**
 * Thread-safe runtime configuration for shadow traffic mirroring.
 *
 * {@code shadowRoutingPercentage} controls what fraction of chat requests are
 * offered to the candidate path (0 = never mirror, 100 = always try to mirror).
 */
@Component
public class ShadowRoutingConfig {

    private final AtomicInteger shadowRoutingPercentage;

    public ShadowRoutingConfig(RuntimeShadowProperties properties) {
        this.shadowRoutingPercentage = new AtomicInteger(
                validatePercentage(properties.routingPercentage()));
    }

    public int getShadowRoutingPercentage() {
        return shadowRoutingPercentage.get();
    }

    public void setShadowRoutingPercentage(int percentage) {
        shadowRoutingPercentage.set(validatePercentage(percentage));
    }

    /**
     * Probabilistic gate used by {@link ChatService} before offering shadow work.
     */
    public boolean shouldMirrorRequest() {
        int percentage = shadowRoutingPercentage.get();
        if (percentage >= 100) {
            return true;
        }
        if (percentage <= 0) {
            return false;
        }
        return ThreadLocalRandom.current().nextInt(100) < percentage;
    }

    private static int validatePercentage(int percentage) {
        if (percentage < 0 || percentage > 100) {
            throw new InvalidConfigException(
                    "shadowRoutingPercentage must be between 0 and 100 (inclusive)");
        }
        return percentage;
    }
}
