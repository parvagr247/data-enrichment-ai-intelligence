package com.subdual.ai_intelligent_service.controller;

import com.subdual.ai_intelligent_service.dto.ExtractedFact;
import com.subdual.ai_intelligent_service.dto.ExtractionRequest;
import com.subdual.ai_intelligent_service.dto.ExtractionResponse;
import com.subdual.ai_intelligent_service.exception.GlobalExceptionHandler;
import com.subdual.ai_intelligent_service.service.ExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ExtractionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ExtractionService extractionService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new ExtractionController(extractionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("Should return 200 OK with extracted facts for valid payload")
    void shouldReturn200ForValidRequest() throws Exception {
        ExtractionResponse mockResponse = new ExtractionResponse(
                "Jane Doe",
                "https://example.com/jane-doe",
                Map.of("currentRole", new ExtractedFact("Principal Engineer", "Jane Doe is a Principal Engineer.", 0.95)),
                "deterministic-rule-engine",
                25
        );

        when(extractionService.extractFacts(any(ExtractionRequest.class))).thenReturn(mockResponse);

        String json = """
                {
                  "entityName": "Jane Doe",
                  "entityType": "PERSON",
                  "sourceUrl": "https://example.com/jane-doe",
                  "textContent": "Jane Doe is a Principal Engineer specializing in cloud.",
                  "targetFields": ["currentRole"]
                }
                """;

        mockMvc.perform(post("/api/v1/ai/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.entityName").value("Jane Doe"))
                .andExpect(jsonPath("$.sourceUrl").value("https://example.com/jane-doe"))
                .andExpect(jsonPath("$.facts.currentRole.value").value("Principal Engineer"))
                .andExpect(jsonPath("$.facts.currentRole.exactQuote").value("Jane Doe is a Principal Engineer."))
                .andExpect(jsonPath("$.facts.currentRole.confidenceScore").value(0.95));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when entityName or textContent is missing")
    void shouldReturn400WhenFieldsMissing() throws Exception {
        String invalidJson = """
                {
                  "sourceUrl": "https://example.com/jane-doe"
                }
                """;

        mockMvc.perform(post("/api/v1/ai/extract")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"));
    }
}
