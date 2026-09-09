package com.subdual.research_service.research.job;

import com.subdual.research_service.api.dto.response.ResearchJobResponse;
import com.subdual.research_service.api.dto.request.ResearchRequest;
import com.subdual.research_service.api.dto.response.ResearchResponse;
import com.subdual.research_service.api.dto.response.ResearchResult;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchStatus;
import com.subdual.research_service.research.service.ResearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResearchJobServiceTest {

    @Mock
    private ResearchService researchService;

    private InMemoryResearchJobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new InMemoryResearchJobService(researchService, Executors.newSingleThreadExecutor());
    }

    @Test
    @DisplayName("Should submit job and complete execution asynchronously")
    void shouldSubmitAndCompleteJobAsynchronously() throws Exception {
        ResearchResult result = new ResearchResult("Acme", EntityType.ORGANIZATION, "https://acme.org", Map.of());
        ResearchResponse mockResponse = new ResearchResponse(ResearchStatus.COMPLETED, "acme-id", result, List.of(), 100);

        when(researchService.executeResearch(any(ResearchRequest.class))).thenReturn(mockResponse);

        ResearchRequest request = new ResearchRequest("https://acme.org", EntityType.ORGANIZATION, "Acme");
        ResearchJobResponse initialResponse = jobService.submitJob(request);

        assertThat(initialResponse).isNotNull();
        assertThat(initialResponse.jobId()).isNotBlank();
        assertThat(initialResponse.status()).isIn(ResearchJobStatus.SUBMITTED, ResearchJobStatus.IN_PROGRESS, ResearchJobStatus.COMPLETED);

        // Wait up to 2 seconds for worker thread to finish
        long start = System.currentTimeMillis();
        ResearchJobResponse finalResponse = null;
        while (System.currentTimeMillis() - start < 2000) {
            Optional<ResearchJobResponse> opt = jobService.getJob(initialResponse.jobId());
            if (opt.isPresent() && opt.get().status() == ResearchJobStatus.COMPLETED) {
                finalResponse = opt.get();
                break;
            }
            Thread.sleep(50);
        }

        assertThat(finalResponse).isNotNull();
        assertThat(finalResponse.status()).isEqualTo(ResearchJobStatus.COMPLETED);
        assertThat(finalResponse.progress()).isEqualTo(100);
        assertThat(finalResponse.result()).isNotNull();
        assertThat(finalResponse.result().entityId()).isEqualTo("acme-id");
    }

    @Test
    @DisplayName("Should mark job as FAILED when execution throws exception")
    void shouldMarkJobAsFailedOnException() throws Exception {
        when(researchService.executeResearch(any(ResearchRequest.class)))
                .thenThrow(new RuntimeException("Discovery failure"));

        ResearchRequest request = new ResearchRequest("https://fail.com", EntityType.OTHER, "Fail");
        ResearchJobResponse initialResponse = jobService.submitJob(request);

        long start = System.currentTimeMillis();
        ResearchJobResponse finalResponse = null;
        while (System.currentTimeMillis() - start < 2000) {
            Optional<ResearchJobResponse> opt = jobService.getJob(initialResponse.jobId());
            if (opt.isPresent() && opt.get().status() == ResearchJobStatus.FAILED) {
                finalResponse = opt.get();
                break;
            }
            Thread.sleep(50);
        }

        assertThat(finalResponse).isNotNull();
        assertThat(finalResponse.status()).isEqualTo(ResearchJobStatus.FAILED);
        assertThat(finalResponse.error()).contains("Discovery failure");
    }

    @Test
    @DisplayName("Should return empty Optional for nonexistent jobId")
    void shouldReturnEmptyForNonexistentJobId() {
        Optional<ResearchJobResponse> response = jobService.getJob("nonexistent-id-1234");
        assertThat(response).isEmpty();
    }
}
