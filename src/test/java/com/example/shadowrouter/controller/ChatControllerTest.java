package com.example.shadowrouter.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shadowrouter.client.InferenceClient.InferenceResult;
import com.example.shadowrouter.service.ChatService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    @Test
    void chatReturnsPrimaryResponseAndRequestIdHeader() throws Exception {
        when(chatService.completeChat(anyString(), any()))
                .thenReturn(new InferenceResult(200, "{\"choices\":[]}"));

        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messages": [
                                    {"role": "user", "content": "hi"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(content().json("{\"choices\":[]}"));

        verify(chatService).completeChat(anyString(), any());
    }

    @Test
    void chatRejectsPayloadWithoutMessages() throws Exception {
        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"temperature\":0.2}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(
                        "{\"error\":\"payload must contain a non-empty 'messages' array\"}"));

        verifyNoInteractions(chatService);
    }

    @Test
    void chatRejectsEmptyMessagesArray() throws Exception {
        mockMvc.perform(post("/v1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(chatService);
    }
}
