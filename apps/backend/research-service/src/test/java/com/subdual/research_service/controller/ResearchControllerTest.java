package com.subdual.research_service.controller;

import com.subdual.research_service.domain.EntityType;
import com.subdual.research_service.domain.ResearchStatus;
import com.subdual.research_service.dto.request.ResearchRequest;
import com.subdual.research_service.dto.response.ResearchResponse;
import com.subdual.research_service.dto.response.ResearchResult;
import com.subdual.research_service.dto.response.SourceItem;
import com.subdual.research_service.exception.ExternalServiceException;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @DisplayName("Test 1: Valid request returns 200 OK with populated sources and correct structure")
    void shouldReturn200AndValidStructureForValidRequest() throws Exception {
        ResearchResult result = new ResearchResult(
                "Spring Boot",
                EntityType.REPOSITORY,
                "https://github.com/spring-projects/spring-boot",
                Map.of()
        );
        List<SourceItem> sources = List.of(
                new SourceItem(
                        "https://github.com/spring-projects/spring-boot",
                        "spring-projects/spring-boot",
                        "GITHUB",
                        Instant.parse("2026-09-05T06:45:00Z"),
                        1.00
                ),
                new SourceItem(
                        "https://spring.io/projects/spring-boot",
                        "Spring Boot Overview",
                        "OFFICIAL_WEBSITE",
                        Instant.parse("2026-09-05T06:45:01Z"),
                        0.95
                )
        );
        ResearchResponse mockResponse = new ResearchResponse(
                ResearchStatus.COMPLETED,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                result,
                sources,
                412
        );

        when(researchService.executeResearch(any(ResearchRequest.class))).thenReturn(mockResponse);

        String requestJson = """
                {
                  "url": "https://github.com/spring-projects/spring-boot",
                  "entityType": "REPOSITORY",
                  "name": "Spring Boot"
                }
                """;

        mockMvc.perform(post("/api/v1/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.entityId").value("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
                .andExpect(jsonPath("$.result.displayName").value("Spring Boot"))
                .andExpect(jsonPath("$.result.entityType").value("REPOSITORY"))
                .andExpect(jsonPath("$.result.canonicalUrl").value("https://github.com/spring-projects/spring-boot"))
                .andExpect(jsonPath("$.result.attributes").isMap())
                .andExpect(jsonPath("$.sources").isArray())
                .andExpect(jsonPath("$.sources.length()").value(2))
                .andExpect(jsonPath("$.sources[0].url").value("https://github.com/spring-projects/spring-boot"))
                .andExpect(jsonPath("$.sources[0].title").value("spring-projects/spring-boot"))
                .andExpect(jsonPath("$.sources[0].sourceType").value("GITHUB"))
                .andExpect(jsonPath("$.sources[0].relevance").value(1.00))
                .andExpect(jsonPath("$.sources[1].url").value("https://spring.io/projects/spring-boot"))
                .andExpect(jsonPath("$.sources[1].sourceType").value("OFFICIAL_WEBSITE"))
                .andExpect(jsonPath("$.executionTimeMs").value(412));
    }

    @Test
    @DisplayName("Test 2: Missing target (neither URL nor name) returns 400 Bad Request with ProblemDetail")
    void shouldReturn400WhenTargetIsMissing() throws Exception {
        String requestJson = """
                {
                  "entityType": "PERSON"
                }
                """;

        mockMvc.perform(post("/api/v1/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Either 'url' or 'name' must be provided for research"))
                .andExpect(jsonPath("$.instance").value("/api/v1/research"));
    }

    @Test
    @DisplayName("Test 2b: Discovery-first research succeeds when url is omitted but name is provided")
    void shouldAcceptDiscoveryFirstRequestWhenUrlIsMissing() throws Exception {
        ResearchResult result = new ResearchResult(
                "Jane Doe",
                EntityType.PERSON,
                "urn:entity:person:jane-doe",
                java.util.Map.of()
        );
        ResearchResponse mockResponse = new ResearchResponse(
                ResearchStatus.COMPLETED,
                "test-entity-id",
                result,
                List.of(),
                120
        );
        when(researchService.executeResearch(any(ResearchRequest.class))).thenReturn(mockResponse);

        String requestJson = """
                {
                  "entityType": "PERSON",
                  "name": "Jane Doe"
                }
                """;

        mockMvc.perform(post("/api/v1/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.entityId").value("test-entity-id"))
                .andExpect(jsonPath("$.result.displayName").value("Jane Doe"));
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

    @Test
    @DisplayName("Test 6: External provider failure returns 502 Bad Gateway with ProblemDetail")
    void shouldReturn502WhenExternalServiceFails() throws Exception {
        when(researchService.executeResearch(any(ResearchRequest.class)))
                .thenThrow(new ExternalServiceException("Search provider unavailable; upstream service returned 502"));

        String requestJson = """
                {
                  "url": "https://example.com/fail",
                  "entityType": "WEBSITE",
                  "name": "Fail Test"
                }
                """;

        mockMvc.perform(post("/api/v1/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.title").value("Bad Gateway"))
                .andExpect(jsonPath("$.detail").value("Search provider unavailable; upstream service returned 502"))
                .andExpect(jsonPath("$.instance").value("/api/v1/research"));
    }

    @Test
    @DisplayName("Test 7: External provider timeout returns 504 Gateway Timeout with ProblemDetail")
    void shouldReturn504WhenExternalServiceTimesOut() throws Exception {
        when(researchService.executeResearch(any(ResearchRequest.class)))
                .thenThrow(new ExternalServiceException("Search discovery timed out after 4000ms", new TimeoutException("Timed out")));

        String requestJson = """
                {
                  "url": "https://example.com/timeout",
                  "entityType": "WEBSITE",
                  "name": "Timeout Test"
                }
                """;

        mockMvc.perform(post("/api/v1/research")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status").value(504))
                .andExpect(jsonPath("$.title").value("Gateway Timeout"))
                .andExpect(jsonPath("$.detail").value("Search discovery timed out after 4000ms"))
                .andExpect(jsonPath("$.instance").value("/api/v1/research"));
    }

    @Test
    @DisplayName("Test 8: Asynchronous job submission returns 202 Accepted with jobId and status")
    void shouldSubmitJobAndReturnAccepted() throws Exception {
        String requestJson = """
                {
                  "url": "https://example.com/company",
                  "entityType": "ORGANIZATION",
                  "name": "Acme Inc"
                }
                """;

        mockMvc.perform(post("/api/v1/research/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.status").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("Test 9: Polling nonexistent job returns 404 Not Found")
    void shouldReturn404ForNonexistentJob() throws Exception {
        mockMvc.perform(get("/api/v1/research/jobs/nonexistent-id-xyz"))
                .andExpect(status().isNotFound());
    }
}
