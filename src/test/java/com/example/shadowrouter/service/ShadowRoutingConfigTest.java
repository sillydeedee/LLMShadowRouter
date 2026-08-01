package com.example.shadowrouter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.shadowrouter.config.RuntimeShadowProperties;
import com.example.shadowrouter.exception.InvalidConfigException;

import org.junit.jupiter.api.Test;

class ShadowRoutingConfigTest {

    @Test
    void zeroPercentNeverMirrorsAndHundredAlwaysDoes() {
        ShadowRoutingConfig config = new ShadowRoutingConfig(
                new RuntimeShadowProperties(100, "./data/test.db"));

        assertTrue(config.shouldMirrorRequest());

        config.setShadowRoutingPercentage(0);
        assertFalse(config.shouldMirrorRequest());
        assertEquals(0, config.getShadowRoutingPercentage());
    }

    @Test
    void rejectsOutOfRangePercentage() {
        ShadowRoutingConfig config = new ShadowRoutingConfig(
                new RuntimeShadowProperties(100, "./data/test.db"));

        assertThrows(InvalidConfigException.class, () -> config.setShadowRoutingPercentage(-1));
        assertThrows(InvalidConfigException.class, () -> config.setShadowRoutingPercentage(101));
    }
}
