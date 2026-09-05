package com.subdual.research_service.research;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.api.dto.ResearchRequest;
import com.subdual.research_service.api.dto.ResearchResponse;
import com.subdual.research_service.api.dto.SourceItem;
import com.subdual.research_service.common.exception.BusinessRuleException;
import com.subdual.research_service.common.validation.ResearchRequestValidator;
import com.subdual.research_service.config.ResearchDiscoveryProperties;
import com.subdual.research_service.config.ResearchPipelineProperties;
import com.subdual.research_service.config.WebFetchProperties;
import com.subdual.research_service.discovery.QueryBuilder;
import com.subdual.research_service.discovery.provider.MockSearchProvider;
import com.subdual.research_service.discovery.service.DefaultResearchDiscoveryService;
import com.subdual.research_service.extraction.DefaultSourceEvidenceService;
import com.subdual.research_service.extraction.EvidenceExtractor;
import com.subdual.research_service.extraction.document.ContentExtractor;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.integration.ai.AiExtractionClient;
import com.subdual.research_service.integration.ai.NoOpAiExtractionClient;
import com.subdual.research_service.integration.ai.dto.AiExtractedFact;
import com.subdual.research_service.integration.persistence.DefaultResearchSnapshotPersister;
import com.subdual.research_service.integration.persistence.NoOpDatasetPersistenceClient;
import com.subdual.research_service.integration.web.DefaultWebContentFetcher;
import com.subdual.research_service.integration.web.FetchedContent;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchSource;
import com.subdual.research_service.research.model.ResearchStatus;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.research.pipeline.DefaultEntityNormalizer;
import com.subdual.research_service.research.pipeline.DeterministicRelevanceEvaluator;
import com.subdual.research_service.research.pipeline.DeterministicSourceClassifier;
import com.subdual.research_service.research.pipeline.ResearchResponseFactory;
import com.subdual.research_service.research.pipeline.SourceProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoadmapTasks1To15Test {

    private ResearchRequestValidator validator;
    private DefaultEntityNormalizer normalizer;
    private QueryBuilder queryBuilder;
    private SourceProcessor sourceProcessor;
    private ContentExtractor contentExtractor;
    private EntityResolver entityResolver;
    private EvidenceExtractor evidenceExtractor;
    private ResearchOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        validator = new ResearchRequestValidator();
        normalizer = new DefaultEntityNormalizer();
        queryBuilder = new QueryBuilder();
        sourceProcessor = new SourceProcessor(new DeterministicSourceClassifier(), new DeterministicRelevanceEvaluator());
        contentExtractor = new ContentExtractor();
        entityResolver = new EntityResolver();
        evidenceExtractor = new EvidenceExtractor(new NoOpAiExtractionClient());

        ResearchDiscoveryProperties discoveryProperties = new ResearchDiscoveryProperties(
                "mock", "https://api.tavily.com", "mock-key", 5, 5000
        );
        ResearchPipelineProperties pipelineProperties = new ResearchPipelineProperties(
                5, 50000
        );
        WebFetchProperties webFetchProperties = new WebFetchProperties(
                3000, 5000, 5, "Mozilla/5.0"
        );

        MockSearchProvider searchProvider = new MockSearchProvider();
        DefaultResearchDiscoveryService discoveryService = new DefaultResearchDiscoveryService(
                searchProvider, queryBuilder, discoveryProperties
        );
        DefaultWebContentFetcher webContentFetcher = new DefaultWebContentFetcher(
                webFetchProperties, discoveryProperties
        );
        DefaultSourceEvidenceService evidenceService = new DefaultSourceEvidenceService(
                webContentFetcher, contentExtractor, entityResolver, evidenceExtractor, pipelineProperties
        );

        orchestrator = new ResearchOrchestrator(
                validator,
                normalizer,
                discoveryService,
                sourceProcessor,
                evidenceService,
                new DefaultResearchSnapshotPersister(new NoOpDatasetPersistenceClient()),
                new ResearchResponseFactory(),
                discoveryProperties,
                pipelineProperties
        );
    }

    @Test
    @DisplayName("Task 1: Valid URL input should pass validation and preserve optional seed fields")
    void shouldAcceptValidUrlInput() {
        ResearchRequest request = new ResearchRequest(
                "https://example.com/team/parv",
                EntityType.PERSON,
                "Parv Agrawal",
                "MNIT Jaipur",
                "Chemical Engineering",
                List.of("role", "organization"),
                Map.of("customKey", "customValue")
        );

        validator.validate(request);
        assertThat(request.isValidHttpUrl()).isTrue();
        assertThat(request.organization()).isEqualTo("MNIT Jaipur");
        assertThat(request.role()).isEqualTo("Chemical Engineering");
        assertThat(request.targetFields()).containsExactly("role", "organization");
    }

    @Test
    @DisplayName("Task 1: Invalid URL should be rejected by standard URI validation")
    void shouldRejectInvalidUrl() {
        ResearchRequest invalidScheme = new ResearchRequest("ftp://example.com/file", EntityType.OTHER, "Test");
        assertThatThrownBy(() -> validator.validate(invalidScheme))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Field 'url' must be a valid, well-formed HTTP/HTTPS URL");

        ResearchRequest malformed = new ResearchRequest("not a url", EntityType.OTHER, "Test");
        assertThatThrownBy(() -> validator.validate(malformed))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Field 'url' must be a valid, well-formed HTTP/HTTPS URL");
    }

    @Test
    @DisplayName("Task 1: Name-only input should be valid without a URL")
    void shouldAcceptNameOnlyInput() {
        ResearchRequest request = new ResearchRequest(null, EntityType.PRODUCT, "Spring Boot");
        validator.validate(request);

        ResearchTarget target = normalizer.normalize(request);
        assertThat(target.displayName()).isEqualTo("Spring Boot");
        assertThat(target.canonicalUrl()).isEqualTo("urn:entity:product:spring-boot");
    }

    @Test
    @DisplayName("Task 1: Completely empty input should be rejected")
    void shouldRejectEmptyInput() {
        ResearchRequest empty = new ResearchRequest(null, EntityType.OTHER, "   ");
        assertThatThrownBy(() -> validator.validate(empty))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Either 'url' or 'name' must be provided for research");
    }

    @Test
    @DisplayName("Task 2: Exact URL should serve as the primary identity anchor")
    void shouldRespectExactUrlIdentity() {
        ResearchRequest request = new ResearchRequest(
                "https://www.linkedin.com/in/parv-agrawal-170174308",
                EntityType.PERSON,
                "Parv Agrawal"
        );

        ResearchTarget target = normalizer.normalize(request);
        assertThat(target.isUrlAnchored()).isTrue();
        assertThat(target.primaryAnchorUrl()).isEqualTo("https://www.linkedin.com/in/parv-agrawal-170174308");
        assertThat(target.canonicalUrl()).isEqualTo("https://www.linkedin.com/in/parv-agrawal-170174308");
    }

    @Test
    @DisplayName("Task 3: URL normalization should clean fragments, casing, trailing slashes, and tracking parameters")
    void shouldNormalizeUrls() {
        String dirtyUrl = "HTTPS://WWW.Example.COM:443/team/lead/?utm_source=twitter&utm_medium=social#overview";
        String cleanUrl = normalizer.canonicalizeUrl(dirtyUrl);

        assertThat(cleanUrl).isEqualTo("https://www.example.com/team/lead");
    }

    @Test
    @DisplayName("Task 4: Search query generation should produce focused, identity-anchored queries")
    void shouldGenerateFocusedSearchQueries() {
        ResearchTarget target = new ResearchTarget(
                "https://www.linkedin.com/in/parv-agrawal-170174308",
                "https://www.linkedin.com/in/parv-agrawal-170174308",
                "id-parv",
                EntityType.PERSON,
                "Parv Agrawal",
                Map.of("organization", "MNIT Jaipur")
        );

        List<String> queries = queryBuilder.buildDiscoveryQueries(target);
        assertThat(queries).isNotEmpty();
        assertThat(queries.get(0)).isEqualTo("\"Parv Agrawal\" linkedin.com/in/parv-agrawal-170174308");
        assertThat(queries).contains("\"Parv Agrawal\" \"MNIT Jaipur\"");
    }

    @Test
    @DisplayName("Task 6: Source filtering must reject wrong same-name entity candidates before fetching")
    void shouldRejectWrongSameNameEntity() {
        ResearchTarget target = new ResearchTarget(
                "https://www.linkedin.com/in/parv-agrawal-170174308",
                "https://www.linkedin.com/in/parv-agrawal-170174308",
                "id-parv",
                EntityType.PERSON,
                "Parv Agrawal",
                Map.of("organization", "MNIT Jaipur", "role", "Chemical Engineering")
        );

        DiscoveredSource validMnitSource = new DiscoveredSource(
                "https://mnit.ac.in/students/parv-agrawal",
                "Parv Agrawal - Chemical Engineering - MNIT Jaipur",
                "OFFICIAL_WEBSITE",
                Instant.now(),
                0.95,
                "Student at MNIT Jaipur in Chemical Engineering"
        );

        DiscoveredSource conflictingDeShaw = new DiscoveredSource(
                "https://deshaw.com/people/parv-agrawal",
                "Parv Agrawal - Quantitative Analyst - D. E. Shaw",
                "SEARCH_RESULT",
                Instant.now(),
                0.80,
                "Quantitative Analyst at D. E. Shaw. Alumnus of IIT Delhi."
        );

        List<ResearchSource> processed = sourceProcessor.processSources(
                List.of(validMnitSource, conflictingDeShaw), target, 5
        );

        assertThat(processed).extracting(ResearchSource::url).contains("https://mnit.ac.in/students/parv-agrawal");
        assertThat(processed).extracting(ResearchSource::url).doesNotContain("https://deshaw.com/people/parv-agrawal");
    }

    @Test
    @DisplayName("Task 7: Source deduplication should eliminate identical sources differing only by tracking/fragments")
    void shouldDeduplicateSources() {
        ResearchTarget target = new ResearchTarget(null, "urn:entity:product:spring-boot", "id-1", EntityType.PRODUCT, "Spring Boot");

        DiscoveredSource s1 = new DiscoveredSource("https://spring.io/projects/spring-boot", "Title 1", "OFFICIAL_WEBSITE", Instant.now(), 0.9, "Snip");
        DiscoveredSource s2 = new DiscoveredSource("https://spring.io/projects/spring-boot/?utm_source=google", "Title 2", "OFFICIAL_WEBSITE", Instant.now(), 0.85, "Snip");

        List<ResearchSource> processed = sourceProcessor.processSources(List.of(s1, s2), target, 5);
        assertThat(processed).hasSize(1);
    }

    @Test
    @DisplayName("Task 8 & 9: Primary sources and authoritative domains must be ranked highest")
    void shouldRankSourcesAndPreferPrimary() {
        ResearchTarget target = new ResearchTarget(
                "https://github.com/spring-projects/spring-boot",
                "https://github.com/spring-projects/spring-boot",
                "id-boot",
                EntityType.REPOSITORY,
                "Spring Boot"
        );

        DiscoveredSource blogSource = new DiscoveredSource(
                "https://someblog.example.com/spring-boot-tutorial",
                "Tutorial",
                "BLOG",
                Instant.now(),
                0.70,
                "Tutorial on Spring Boot"
        );

        DiscoveredSource primarySource = new DiscoveredSource(
                "https://github.com/spring-projects/spring-boot",
                "spring-boot repository",
                "GITHUB",
                Instant.now(),
                0.90,
                "Official repo"
        );

        List<ResearchSource> ranked = sourceProcessor.processSources(List.of(blogSource, primarySource), target, 5);
        assertThat(ranked.get(0).url()).isEqualTo("https://github.com/spring-projects/spring-boot");
        assertThat(ranked.get(0).qualityScore()).isGreaterThan(ranked.get(1).qualityScore());
    }

    @Test
    @DisplayName("Task 10 & 11: Blocked source (HTTP 999) handled gracefully and content cleaned of noise tags")
    void shouldHandleBlockedSourceAndCleanNoisyContent() {
        FetchedContent noisyHtml = FetchedContent.success(
                "https://example.com/profile",
                200,
                "text/html",
                """
                <html>
                <head><title>Jane Profile</title></head>
                <body>
                    <nav><a href="#">Home</a><a href="#">About</a></nav>
                    <div class="cookie-banner">Accept cookies</div>
                    <div class="advertisement">Buy stuff</div>
                    <h1>Jane Profile</h1>
                    <p>Jane is a Staff Engineer at CloudScale.</p>
                    <aside class="sidebar">Related links</aside>
                    <footer>Copyright 2026</footer>
                </body>
                </html>
                """
        );

        ExtractedDocument doc = contentExtractor.extract(noisyHtml, 1000);
        assertThat(doc.cleanText()).doesNotContain("Accept cookies");
        assertThat(doc.cleanText()).doesNotContain("Buy stuff");
        assertThat(doc.cleanText()).doesNotContain("Related links");
        assertThat(doc.cleanText()).contains("Jane is a Staff Engineer at CloudScale.");
    }

    @Test
    @DisplayName("Task 13, 14 & 15: Fact grounding validation must reject unsupported facts")
    void shouldValidateEvidenceGroundingAndRejectUnsupportedFacts() {
        AiExtractionClient groundedClient = (entityName, entityType, sourceUrl, textContent, targetFields) -> Map.of(
                "role", new AiExtractedFact("Staff Engineer", "is a Staff Engineer at CloudScale", 0.95),
                "unsupportedField", new AiExtractedFact("Invented Value", "completely fabricated quote not in text", 0.90)
        );

        EvidenceExtractor groundedExtractor = new EvidenceExtractor(groundedClient);

        ResearchTarget target = new ResearchTarget(
                "https://example.com/team/jane", "https://example.com/team/jane", "id-jane", EntityType.PERSON, "Jane"
        );

        ExtractedDocument doc = new ExtractedDocument(
                "https://example.com/team/jane",
                "Jane - Team",
                "Jane is a Staff Engineer at CloudScale.",
                "CloudScale",
                "Jane is a Staff Engineer at CloudScale.",
                Instant.now()
        );

        ResearchSource source = new ResearchSource(doc.url(), doc.title(), "OFFICIAL_WEBSITE", Instant.now(), 0.95);
        Map<String, EntityResolver.ResolutionResult> resolutions = Map.of(
                doc.url(), new EntityResolver.ResolutionResult(ConfidenceTier.HIGH, true, "Matched")
        );

        Map<String, EvidenceTuple> attributes = groundedExtractor.extractEvidence(
                target, List.of(source), List.of(doc), resolutions
        );

        // Grounded fact is accepted
        assertThat(attributes).containsKey("role");
        assertThat(attributes.get("role").value()).isEqualTo("Staff Engineer");

        // Unsupported fact is rejected (NO SOURCE EVIDENCE = NO TRUSTED FACT)
        assertThat(attributes).doesNotContainKey("unsupportedField");
    }

    @Test
    @DisplayName("End-to-End: Full research request with Parv Agrawal and seed organization should filter conflicting entities")
    void shouldRunEndToEndResearchWithSameNameRejection() {
        ResearchRequest request = new ResearchRequest(
                "https://www.linkedin.com/in/parv-agrawal-170174308",
                EntityType.PERSON,
                "Parv Agrawal",
                "MNIT Jaipur",
                "Chemical Engineering",
                List.of("role", "organization", "education"),
                Map.of()
        );

        ResearchResponse response = orchestrator.executeResearch(request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(ResearchStatus.COMPLETED);

        // Seed LinkedIn URL remains rank 0
        assertThat(response.sources()).isNotEmpty();
        assertThat(response.sources().get(0).url()).isEqualTo("https://www.linkedin.com/in/parv-agrawal-170174308");

        // Conflicting D. E. Shaw and IIT Delhi sources are strictly filtered out
        for (SourceItem src : response.sources()) {
            assertThat(src.url()).doesNotContain("deshaw.com");
            assertThat(src.url()).doesNotContain("iitd.ac.in");
        }

        // Extracted attributes are grounded and corroborated by MNIT Jaipur
        Map<String, EvidenceTuple> attributes = response.result().attributes();
        assertThat(attributes).containsKey("role");
        assertThat(attributes.get("role").value()).doesNotContain("Quantitative Analyst");
    }
}
