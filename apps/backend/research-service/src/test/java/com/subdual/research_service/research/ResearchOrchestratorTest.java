package com.subdual.research_service.research;

import com.subdual.research_service.research.api.EvidenceTuple;
import com.subdual.research_service.research.api.ResearchRequest;
import com.subdual.research_service.research.api.ResearchResponse;
import com.subdual.research_service.research.api.ResearchResult;
import com.subdual.research_service.common.exception.BusinessRuleException;
import com.subdual.research_service.common.validation.ResearchRequestValidator;
import com.subdual.research_service.config.ResearchDiscoveryProperties;
import com.subdual.research_service.config.ResearchPipelineProperties;
import com.subdual.research_service.discovery.ResearchDiscoveryService;
import com.subdual.research_service.research.pipeline.ResearchOrchestrator;
import com.subdual.research_service.extraction.SourceEvidenceService;
import com.subdual.research_service.integration.persistence.ResearchSnapshotPersister;
import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchStatus;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.research.pipeline.EntityNormalizer;
import com.subdual.research_service.research.pipeline.ResearchContext;
import com.subdual.research_service.research.pipeline.ResearchResponseFactory;
import com.subdual.research_service.research.pipeline.SourceProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResearchOrchestratorTest {

    @Mock
    private ResearchRequestValidator validator;
    @Mock
    private EntityNormalizer entityNormalizer;
    @Mock
    private ResearchDiscoveryService discoveryService;
    @Mock
    private SourceProcessor sourceProcessor;
    @Mock
    private SourceEvidenceService sourceEvidenceService;
    @Mock
    private ResearchSnapshotPersister persister;
    @Mock
    private ResearchResponseFactory responseFactory;

    private ResearchOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        ResearchDiscoveryProperties discoveryProps = new ResearchDiscoveryProperties("mock", null, null, 5, 4000);
        ResearchPipelineProperties pipelineProps = new ResearchPipelineProperties(5, 50000);

        orchestrator = new ResearchOrchestrator(
                validator,
                entityNormalizer,
                discoveryService,
                sourceProcessor,
                sourceEvidenceService,
                persister,
                responseFactory,
                discoveryProps,
                pipelineProps
        );
    }

    @Test
    @DisplayName("Should coordinate all pipeline stages in strict sequential order")
    void shouldExecutePipelineInSequentialOrder() {
        ResearchRequest request = new ResearchRequest("https://example.com", EntityType.ORGANIZATION, "Example");
        ResearchContext context = new ResearchContext(request);

        ResearchTarget target = new ResearchTarget(
                "https://example.com",
                "https://example.com",
                "target-id-123",
                EntityType.ORGANIZATION,
                "Example"
        );

        DiscoveredSource rawSource = new DiscoveredSource(
                "https://example.com",
                "Example Title",
                "OFFICIAL",
                Instant.now(),
                1.0,
                "Snippet"
        );

        ResearchSource rankedSource = new ResearchSource(
                "https://example.com",
                "Example Title",
                "OFFICIAL",
                Instant.now(),
                1.0,
                1.0,
                "Snippet"
        );

        Map<String, EvidenceTuple> attributes = Map.of();

        ResearchResponse expectedResponse = new ResearchResponse(
                ResearchStatus.COMPLETED,
                "target-id-123",
                new ResearchResult("Example", EntityType.ORGANIZATION, "https://example.com", attributes),
                List.of(),
                50L
        );

        when(entityNormalizer.normalize(request)).thenReturn(target);
        when(discoveryService.discoverSources(target)).thenReturn(List.of(rawSource));
        when(sourceProcessor.processSources(eq(List.of(rawSource)), eq(target), eq(5), anyString())).thenReturn(List.of(rankedSource));
        when(sourceEvidenceService.extractEvidence(eq(target), eq(List.of(rankedSource)), any())).thenReturn(attributes);
        when(responseFactory.createResponse(any(), any(), any(), anyInt(), anyLong(), any(), anyString()))
                .thenReturn(expectedResponse);

        ResearchResponse actualResponse = orchestrator.execute(context);

        assertThat(actualResponse).isEqualTo(expectedResponse);

        InOrder inOrder = inOrder(
                validator,
                entityNormalizer,
                discoveryService,
                sourceProcessor,
                sourceEvidenceService,
                persister,
                responseFactory
        );

        inOrder.verify(validator).validate(request);
        inOrder.verify(entityNormalizer).normalize(request);
        inOrder.verify(discoveryService).discoverSources(target);
        inOrder.verify(sourceProcessor).processSources(eq(List.of(rawSource)), eq(target), eq(5), anyString());
        inOrder.verify(sourceEvidenceService).extractEvidence(eq(target), eq(List.of(rankedSource)), any());
        inOrder.verify(persister).persistSnapshot(eq(target), eq(List.of(rankedSource)), eq(attributes), any());
        inOrder.verify(responseFactory).createResponse(any(), any(), any(), anyInt(), anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("Should abort immediately when validator throws BusinessRuleException")
    void shouldAbortWhenValidationFails() {
        ResearchRequest request = new ResearchRequest(null, EntityType.OTHER, null);
        ResearchContext context = new ResearchContext(request);

        doThrow(new BusinessRuleException("Either 'url' or 'name' must be provided for research"))
                .when(validator).validate(request);

        assertThatThrownBy(() -> orchestrator.execute(context))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Either 'url' or 'name' must be provided");

        verifyNoInteractions(entityNormalizer, discoveryService, sourceProcessor, sourceEvidenceService, persister, responseFactory);
    }
}
