package com.subdual.dataset_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.dataset_service.dataset.api.controller.DatasetIngestionController;
import com.subdual.dataset_service.dataset.api.dto.request.DatasetProfileRequest;
import com.subdual.dataset_service.dataset.api.dto.response.DatasetProfileReport;
import com.subdual.dataset_service.dataset.service.DatasetIngestionService;
import com.subdual.dataset_service.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DatasetIngestionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DatasetIngestionService ingestionService;

    @InjectMocks
    private DatasetIngestionController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Should successfully upload multipart CSV dataset file and return profile report")
    void shouldUploadAndProfileMultipartFile() throws Exception {
        String csvContent = "name,company,linkedin_url\nAlice Smith,Acme Corp,https://linkedin.com/in/alicesmith\n";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-dataset.csv",
                "text/csv",
                csvContent.getBytes(StandardCharsets.UTF_8)
        );

        DatasetProfileReport mockReport = new DatasetProfileReport(
                "test-dataset.csv",
                1,
                1,
                0,
                0,
                List.of(),
                List.of("name"),
                List.of("linkedin_url"),
                List.of("company"),
                List.of(),
                List.of(),
                95.0,
                "High quality",
                Map.of("nameColumn", "name", "organizationColumn", "company", "urlColumn", "linkedin_url"),
                List.of(Map.of("name", "Alice Smith", "company", "Acme Corp"))
        );

        when(ingestionService.ingestAndProfile(any(InputStream.class), eq("test-dataset.csv")))
                .thenReturn(mockReport);

        mockMvc.perform(multipart("/api/v2/datasets/upload")
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.datasetName").value("test-dataset.csv"))
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.recommendedMapping.nameColumn").value("name"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request when uploaded file is empty")
    void shouldRejectEmptyMultipartFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.csv",
                "text/csv",
                new byte[0]
        );

        mockMvc.perform(multipart("/api/v2/datasets/upload")
                        .file(emptyFile)
                        .contentType(MediaType.MULTIPART_FORM_DATA_VALUE))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should profile raw rows via JSON request body")
    void shouldProfileJsonRows() throws Exception {
        DatasetProfileRequest request = new DatasetProfileRequest(
                "manual.json",
                List.of(Map.of("name", "Bob Jones", "company", "Beta Inc"))
        );

        DatasetProfileReport mockReport = new DatasetProfileReport(
                "manual.json",
                1,
                1,
                0,
                0,
                List.of(),
                List.of("name"),
                List.of(),
                List.of("company"),
                List.of(),
                List.of(),
                90.0,
                "Good quality",
                Map.of("nameColumn", "name", "organizationColumn", "company"),
                List.of(Map.of("name", "Bob Jones", "company", "Beta Inc"))
        );

        when(ingestionService.profileRawRows(eq("manual.json"), any()))
                .thenReturn(mockReport);

        mockMvc.perform(post("/api/v2/datasets/profile")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datasetName").value("manual.json"))
                .andExpect(jsonPath("$.totalRows").value(1));
    }
}
