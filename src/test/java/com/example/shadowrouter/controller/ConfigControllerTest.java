package com.example.shadowrouter.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shadowrouter.config.RuntimeShadowProperties;
import com.example.shadowrouter.service.ShadowRoutingConfig;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ConfigController.class)
@Import(ConfigControllerTest.TestConfig.class)
class ConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAndPutConfigUpdateRoutingPercentage() throws Exception {
        mockMvc.perform(get("/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shadowRoutingPercentage").value(100));

        mockMvc.perform(put("/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shadowRoutingPercentage\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shadowRoutingPercentage").value(50));

        mockMvc.perform(get("/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shadowRoutingPercentage").value(50));
    }

    @Test
    void putConfigRejectsInvalidPercentage() throws Exception {
        mockMvc.perform(put("/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shadowRoutingPercentage\":150}"))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        RuntimeShadowProperties runtimeShadowProperties() {
            return new RuntimeShadowProperties(100, "./target/test-data/config-test.db");
        }

        @Bean
        ShadowRoutingConfig shadowRoutingConfig(RuntimeShadowProperties properties) {
            return new ShadowRoutingConfig(properties);
        }
    }
}
