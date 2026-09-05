package com.subdual.research_service.controller;

import com.subdual.research_service.domain.EntityType;
import com.subdual.research_service.domain.ResearchStatus;
import com.subdual.research_service.dto.ResearchRequest;
import com.subdual.research_service.dto.ResearchResponse;
import com.subdual.research_service.dto.ResearchResult;
import com.subdual.research_service.exception.GlobalExceptionHandler;
import com.subdual.research_service.service.ResearchService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ResearchControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ResearchService researchService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new ResearchController(researchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("Test 1: Valid request returns 200 OK with correct response structure")
    void shouldReturn200AndValidStructureForValidRequest() throws Exception {
        ResearchResult result = new ResearchResult(
                "Jane Doe",
                EntityType.PERSON,
                "https://example.com/profiles/jane-doe",
                Map.of()
        );
        ResearchResponse mockResponse = new ResearchResponse(
                ResearchStatus.COMPLETED,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                result,
                List.of(),
                12
        );

        when(researchService.executeResearch(any(ResearchRequest.class))).thenReturn(mockResponse);

        String requestJson = """
                {
                  "url": "https://example.com/profiles/jane-doe",
                  "entityType": "PERSON",
                  "name": "Jane Doe"
                }
                """;

        mockMvc.perform(post("/api/v1/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.entityId").value("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
                .andExpect(jsonPath("$.result.displayName").value("Jane Doe"))
                .andExpect(jsonPath("$.result.entityType").value("PERSON"))
                .andExpect(jsonPath("$.result.canonicalUrl").value("https://example.com/profiles/jane-doe"))
                .andExpect(jsonPath("$.result.attributes").isMap())
                .andExpect(jsonPath("$.sources").isArray())
                .andExpect(jsonPath("$.executionTimeMs").value(12));
    }

    @Test
    @DisplayName("Test 2: Missing required field (url) returns 400 Bad Request with ProblemDetail")
    void shouldReturn400WhenUrlIsMissing() throws Exception {
        String requestJson = """
                {
                  "entityType": "PERSON",
                  "name": "Jane Doe"
                }
                """;

        mockMvc.perform(post("/api/v1/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Field 'url' must be a valid, well-formed HTTP/HTTPS URL"))
                .andExpect(jsonPath("$.instance").value("/api/v1/research"));
    }

    @Test
    @DisplayName("Test 3: Invalid entity type returns 400 Bad Request with ProblemDetail")
    void shouldReturn400WhenEntityTypeIsInvalid() throws Exception {
        String requestJson = """
                {
                  "url": "https://example.com/profiles/jane-doe",
                  "entityType": "INVALID",
                  "name": "Jane Doe"
                }
                """;

        mockMvc.perform(post("/api/v1/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.instance").value("/api/v1/research"));
    }

    @Test
    @DisplayName("Test 4: Invalid URL returns 400 Bad Request")
    void shouldReturn400WhenUrlIsInvalid() throws Exception {
        String requestJson = """
                {
                  "url": "not-a-valid-url",
                  "entityType": "PERSON",
                  "name": "Jane Doe"
                }
                """;

        mockMvc.perform(post("/api/v1/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Field 'url' must be a valid, well-formed HTTP/HTTPS URL"))
                .andExpect(jsonPath("$.instance").value("/api/v1/research"));
    }

    @Test
    @DisplayName("Test 5: Service interaction verifies controller delegates to ResearchService")
    void shouldDelegateToResearchService() throws Exception {
        ResearchResult result = new ResearchResult(
                "Example",
                EntityType.OTHER,
                "https://example.com",
                Map.of()
        );
        ResearchResponse mockResponse = new ResearchResponse(
                ResearchStatus.COMPLETED,
                "test-id",
                result,
                List.of(),
                5
        );

        when(researchService.executeResearch(any(ResearchRequest.class))).thenReturn(mockResponse);

        String requestJson = """
                {
                  "url": "https://example.com"
                }
                """;

        mockMvc.perform(post("/api/v1/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        verify(researchService).executeResearch(any(ResearchRequest.class));
    }
}
