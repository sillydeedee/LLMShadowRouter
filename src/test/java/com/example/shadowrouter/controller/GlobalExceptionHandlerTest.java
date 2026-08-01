package com.example.shadowrouter.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.http.HttpTimeoutException;

import com.example.shadowrouter.exception.PrimaryInferenceException;
import com.example.shadowrouter.service.ChatService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ChatController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    void mapsPrimaryTimeoutToGatewayTimeout() throws Exception {
        when(chatService.completeChat(anyString(), any()))
                .thenThrow(PrimaryInferenceException.timedOut(new HttpTimeoutException("timed out")));

        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isGatewayTimeout())
                .andExpect(content().json("{\"error\":\"primary model timed out\"}"));
    }

    @Test
    void mapsPrimaryIoFailureToBadGateway() throws Exception {
        when(chatService.completeChat(anyString(), any()))
                .thenThrow(PrimaryInferenceException.unreachable(new IOException("connection refused")));

        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messages":[{"role":"user","content":"hi"}]}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("{\"error\":\"primary model is unreachable\"}"));
    }
}
