package com.subdual.dataset_service.controller;

import com.subdual.dataset_service.dto.EntityDetailResponse;
import com.subdual.dataset_service.dto.EntitySummaryResponse;
import com.subdual.dataset_service.dto.PersistEntityRequest;
import com.subdual.dataset_service.exception.GlobalExceptionHandler;
import com.subdual.dataset_service.service.EntityPersistenceService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class EntityControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EntityPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new EntityController(persistenceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("Should return 201 Created when persisting a valid entity")
    void shouldReturn201WhenPersistingValidEntity() throws Exception {
        EntityDetailResponse mockResponse = new EntityDetailResponse(
                "abc123hash",
                "Spring Boot",
                "REPOSITORY",
                "https://github.com/spring-projects/spring-boot",
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );

        when(persistenceService.persistOrUpdate(any(PersistEntityRequest.class))).thenReturn(mockResponse);

        String json = """
                {
                  "entityId": "abc123hash",
                  "displayName": "Spring Boot",
                  "entityType": "REPOSITORY",
                  "canonicalUrl": "https://github.com/spring-projects/spring-boot",
                  "sources": [],
                  "attributes": {}
                }
                """;

        mockMvc.perform(post("/api/v1/entities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.entityId").value("abc123hash"))
                .andExpect(jsonPath("$.displayName").value("Spring Boot"));
    }

    @Test
    @DisplayName("Should return 200 OK when fetching an existing entity")
    void shouldReturn200WhenEntityExists() throws Exception {
        EntityDetailResponse mockResponse = new EntityDetailResponse(
                "abc123hash",
                "Spring Boot",
                "REPOSITORY",
                "https://github.com/spring-projects/spring-boot",
                List.of(),
                Map.of(),
                Instant.now(),
                Instant.now()
        );

        when(persistenceService.findById("abc123hash")).thenReturn(Optional.of(mockResponse));

        mockMvc.perform(get("/api/v1/entities/abc123hash")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityId").value("abc123hash"));
    }

    @Test
    @DisplayName("Should return 404 Not Found when entity does not exist")
    void shouldReturn404WhenNotFound() throws Exception {
        when(persistenceService.findById("missing-id")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/entities/missing-id")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return list of entity summaries on GET /api/v1/entities")
    void shouldListEntities() throws Exception {
        when(persistenceService.listAll()).thenReturn(List.of(
                new EntitySummaryResponse("id-1", "Spring Boot", "REPOSITORY", "https://github.com/spring-boot", 3, 4, Instant.now())
        ));

        mockMvc.perform(get("/api/v1/entities")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entityId").value("id-1"))
                .andExpect(jsonPath("$[0].displayName").value("Spring Boot"));
    }
}
