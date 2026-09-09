package com.subdual.research_service.extraction.service.impl;

import com.subdual.research_service.api.dto.response.EvidenceTuple;
import com.subdual.research_service.config.ResearchPipelineProperties;
import com.subdual.research_service.extraction.extractor.EvidenceExtractor;
import com.subdual.research_service.integration.web.FetchedContent;
import com.subdual.research_service.integration.web.WebContentFetcher;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.research.pipeline.ResearchDiagnostics;
import com.subdual.research_service.extraction.document.ContentExtractor;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.support.EntityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSourceEvidenceServiceTest {

    @Mock
    private WebContentFetcher webContentFetcher;
    @Mock
    private ContentExtractor contentExtractor;
    @Mock
    private EntityResolver entityResolver;
    @Mock
    private EvidenceExtractor evidenceExtractor;

    private DefaultSourceEvidenceService service;

    @BeforeEach
    void setUp() {
        ResearchPipelineProperties properties = new ResearchPipelineProperties(5, 50000);
        service = new DefaultSourceEvidenceService(
                webContentFetcher,
                contentExtractor,
                entityResolver,
                evidenceExtractor,
                properties
        );
    }

    @Test
    @DisplayName("Should return empty map when target or sources are null/empty")
    void shouldReturnEmptyMapWhenNoSourcesOrTarget() {
        ResearchDiagnostics diagnostics = new ResearchDiagnostics();

        assertThat(service.extractEvidence(null, List.of(), diagnostics)).isEmpty();

        ResearchTarget target = new ResearchTarget("https://example.com", "https://example.com", "t1", EntityType.ORGANIZATION, "Acme");
        assertThat(service.extractEvidence(target, null, diagnostics)).isEmpty();
        assertThat(service.extractEvidence(target, List.of(), diagnostics)).isEmpty();

        verifyNoInteractions(webContentFetcher, contentExtractor, entityResolver, evidenceExtractor);
    }

    @Test
    @DisplayName("Should fetch content, extract document, resolve entity, and delegate to EvidenceExtractor")
    void shouldProcessSourcesAndExtractEvidence() {
        ResearchTarget target = new ResearchTarget("https://example.com", "https://example.com", "t1", EntityType.ORGANIZATION, "Acme");
        ResearchSource source = new ResearchSource("https://example.com/about", "About Acme", "ABOUT_PAGE", Instant.now(), 0.9, 0.9, "Snippet");
        ResearchDiagnostics diagnostics = new ResearchDiagnostics();

        FetchedContent fetched = FetchedContent.success("https://example.com/about", 200, "text/html", "<html>About Acme</html>");
        ExtractedDocument document = new ExtractedDocument("https://example.com/about", "About Acme", "About Acme", "Meta", "Acme");
        EntityResolver.ResolutionResult resolution = new EntityResolver.ResolutionResult(ConfidenceTier.HIGH, true, "Direct match");

        Map<String, EvidenceTuple> expectedEvidence = Map.of(
                "company_name", new EvidenceTuple("Acme", "https://example.com/about", "Snippet", ConfidenceTier.HIGH)
        );

        when(webContentFetcher.fetch("https://example.com/about")).thenReturn(fetched);
        when(contentExtractor.extract(eq(fetched), anyInt())).thenReturn(document);
        when(entityResolver.resolve(target, document)).thenReturn(resolution);
        when(evidenceExtractor.extractEvidence(eq(target), eq(List.of(source)), any(), any()))
                .thenReturn(expectedEvidence);

        Map<String, EvidenceTuple> actualEvidence = service.extractEvidence(target, List.of(source), diagnostics);

        assertThat(actualEvidence).isEqualTo(expectedEvidence);
        assertThat(diagnostics.warningCount()).isEqualTo(0);
        verify(webContentFetcher).fetch("https://example.com/about");
        verify(contentExtractor).extract(eq(fetched), anyInt());
        verify(entityResolver).resolve(target, document);
    }

    @Test
    @DisplayName("Should record warning when source is inaccessible and continue with other sources")
    void shouldHandleInaccessibleSourceGracefully() {
        ResearchTarget target = new ResearchTarget("https://example.com", "https://example.com", "t1", EntityType.ORGANIZATION, "Acme");
        ResearchSource badSource = new ResearchSource("https://bad.example.com", "Bad", "OTHER", Instant.now(), 0.5, 0.5, "Snippet");
        ResearchDiagnostics diagnostics = new ResearchDiagnostics();

        FetchedContent failed = FetchedContent.failed("https://bad.example.com", 500, "Connection refused");
        when(webContentFetcher.fetch("https://bad.example.com")).thenReturn(failed);
        when(evidenceExtractor.extractEvidence(eq(target), eq(List.of(badSource)), any(), any()))
                .thenReturn(Map.of());

        Map<String, EvidenceTuple> result = service.extractEvidence(target, List.of(badSource), diagnostics);

        assertThat(result).isEmpty();
        assertThat(diagnostics.warningCount()).isEqualTo(1);
        assertThat(diagnostics.warnings().get(0)).contains("bad.example.com");
    }
}
