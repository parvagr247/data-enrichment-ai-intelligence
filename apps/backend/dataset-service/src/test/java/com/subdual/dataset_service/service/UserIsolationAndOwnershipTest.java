package com.subdual.dataset_service.service;

import com.subdual.dataset_service.config.EnrichmentProperties;
import com.subdual.dataset_service.domain.EnrichedEntity;
import com.subdual.dataset_service.dto.EntityDetailResponse;
import com.subdual.dataset_service.dto.PersistEntityRequest;
import com.subdual.dataset_service.dto.EnrichmentJobRequest;
import com.subdual.dataset_service.dto.EnrichmentJobResponse;
import com.subdual.dataset_service.integration.client.AiServiceClient;
import com.subdual.dataset_service.integration.client.ResearchServiceClient;
import com.subdual.dataset_service.repository.EnrichedEntityRepository;
import com.subdual.dataset_service.service.executor.EnrichmentTaskExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserIsolationAndOwnershipTest {

    @Mock
    private EnrichedEntityRepository entityRepository;

    @Mock
    private ResearchServiceClient researchServiceClient;

    @Mock
    private AiServiceClient aiServiceClient;

    private DefaultEntityPersistenceService persistenceService;
    private DefaultDatasetEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        persistenceService = new DefaultEntityPersistenceService(entityRepository);
        EnrichmentTaskExecutor taskExecutor = new EnrichmentTaskExecutor(
                new EnrichmentProperties(
                        new EnrichmentProperties.Execution(3, 100),
                        new EnrichmentProperties.Research(5),
                        new EnrichmentProperties.Timeout(60, 30),
                        new EnrichmentProperties.Ai(true)
                )
        );
        enrichmentService = new DefaultDatasetEnrichmentService(
                researchServiceClient,
                aiServiceClient,
                persistenceService,
                Executors.newSingleThreadExecutor(),
                taskExecutor
        );
    }

    @Test
    @DisplayName("Entity Persistence: Persisting with userId binds entity to that user")
    void persistEntity_bindsUserId() {
        PersistEntityRequest request = new PersistEntityRequest(
                "ent-1",
                "Entity One",
                "ORGANIZATION",
                "https://example.com/ent1",
                List.of(),
                Map.of()
        );

        EnrichedEntity entityWithUser = EnrichedEntity.builder()
                .entityId("ent-1")
                .displayName("Entity One")
                .entityType("ORGANIZATION")
                .canonicalUrl("https://example.com/ent1")
                .userId("user-alice")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(entityRepository.findById("ent-1")).thenReturn(Optional.empty());
        when(entityRepository.save(any(EnrichedEntity.class))).thenAnswer(invocation -> {
            EnrichedEntity e = invocation.getArgument(0);
            assertThat(e.getUserId()).isEqualTo("user-alice");
            return entityWithUser;
        });

        EntityDetailResponse response = persistenceService.persistOrUpdate(request, "user-alice");
        assertThat(response).isNotNull();
        assertThat(response.entityId()).isEqualTo("ent-1");
    }

    @Test
    @DisplayName("IDOR Protection: User B cannot retrieve User A's entity")
    void findById_preventsCrossUserAccess() {
        EnrichedEntity aliceEntity = EnrichedEntity.builder()
                .entityId("ent-alice")
                .displayName("Alice Entity")
                .entityType("PERSON")
                .canonicalUrl("https://example.com/alice")
                .userId("user-alice")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(entityRepository.findById("ent-alice")).thenReturn(Optional.of(aliceEntity));

        // Alice accessing her own entity -> Success
        Optional<EntityDetailResponse> aliceAccess = persistenceService.findById("ent-alice", "user-alice");
        assertThat(aliceAccess).isPresent();

        // Bob attempting to access Alice's entity -> Denied (empty / 404)
        Optional<EntityDetailResponse> bobAccess = persistenceService.findById("ent-alice", "user-bob");
        assertThat(bobAccess).isEmpty();
    }

    @Test
    @DisplayName("IDOR Protection: User B cannot view or cancel User A's batch enrichment job")
    void batchJob_preventsCrossUserAccess() {
        EnrichmentJobRequest jobRequest = new EnrichmentJobRequest(
                "Alice Dataset",
                "Find current position",
                "PERSON",
                Map.of("nameColumn", "Name"),
                List.of(Map.of("Name", "Entity 1"))
        );

        EnrichmentJobResponse job = enrichmentService.createAndSubmitJob(jobRequest, "user-alice");
        String jobId = job.jobId();

        // Alice accessing her own job -> Present
        Optional<EnrichmentJobResponse> aliceJob = enrichmentService.getJob(jobId, "user-alice");
        assertThat(aliceJob).isPresent();

        // Bob attempting to view Alice's job -> Denied
        Optional<EnrichmentJobResponse> bobJob = enrichmentService.getJob(jobId, "user-bob");
        assertThat(bobJob).isEmpty();

        // Bob attempting to cancel Alice's job -> Denied (returns false)
        boolean bobCancelled = enrichmentService.cancelJob(jobId, "user-bob");
        assertThat(bobCancelled).isFalse();

        // Alice cancelling her own job -> Allowed
        boolean aliceCancelled = enrichmentService.cancelJob(jobId, "user-alice");
        assertThat(aliceCancelled).isTrue();
    }

    @Test
    @DisplayName("Job Listing: User B only sees their own jobs")
    void listJobs_filtersByUser() {
        EnrichmentJobRequest reqA = new EnrichmentJobRequest("Alice Dataset", "req", "PERSON", Map.of(), List.of());
        EnrichmentJobRequest reqB = new EnrichmentJobRequest("Bob Dataset", "req", "PERSON", Map.of(), List.of());

        enrichmentService.createAndSubmitJob(reqA, "user-alice");
        enrichmentService.createAndSubmitJob(reqB, "user-bob");

        List<EnrichmentJobResponse> aliceJobs = enrichmentService.listJobs("user-alice");
        assertThat(aliceJobs).hasSize(1);
        assertThat(aliceJobs.get(0).datasetName()).isEqualTo("Alice Dataset");

        List<EnrichmentJobResponse> bobJobs = enrichmentService.listJobs("user-bob");
        assertThat(bobJobs).hasSize(1);
        assertThat(bobJobs.get(0).datasetName()).isEqualTo("Bob Dataset");
    }
}
