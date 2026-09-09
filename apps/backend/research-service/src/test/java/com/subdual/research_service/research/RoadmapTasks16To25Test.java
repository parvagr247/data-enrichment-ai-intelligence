package com.subdual.research_service.research;

import com.subdual.research_service.research.api.EvidenceTuple;
import com.subdual.research_service.research.api.ResearchRequest;
import com.subdual.research_service.research.api.ResearchResponse;
import com.subdual.research_service.common.validation.ResearchRequestValidator;
import com.subdual.research_service.config.ResearchDiscoveryProperties;
import com.subdual.research_service.config.ResearchPipelineProperties;
import com.subdual.research_service.config.WebFetchProperties;
import com.subdual.research_service.discovery.QueryBuilder;
import com.subdual.research_service.discovery.provider.MockSearchProvider;
import com.subdual.research_service.discovery.DefaultResearchDiscoveryService;
import com.subdual.research_service.extraction.DefaultSourceEvidenceService;
import com.subdual.research_service.extraction.extractor.EvidenceExtractor;
import com.subdual.research_service.extraction.document.ContentExtractor;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.integration.ai.NoOpAiExtractionClient;
import com.subdual.research_service.integration.ai.AiExtractedFact;
import com.subdual.research_service.research.pipeline.ResearchOrchestrator;
import com.subdual.research_service.integration.persistence.DefaultResearchSnapshotPersister;
import com.subdual.research_service.integration.persistence.NoOpDatasetPersistenceClient;
import com.subdual.research_service.integration.web.DefaultWebContentFetcher;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchDepth;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoadmapTasks16To25Test {

    private DefaultEntityNormalizer normalizer;
    private QueryBuilder queryBuilder;
    private SourceProcessor sourceProcessor;
    private EvidenceExtractor evidenceExtractor;
    private ResearchOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        normalizer = new DefaultEntityNormalizer();
        queryBuilder = new QueryBuilder();
        sourceProcessor = new SourceProcessor(new DeterministicSourceClassifier(), new DeterministicRelevanceEvaluator());
        ContentExtractor contentExtractor = new ContentExtractor();
        EntityResolver entityResolver = new EntityResolver();
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
        DefaultResearchSnapshotPersister persister = new DefaultResearchSnapshotPersister(
                new NoOpDatasetPersistenceClient()
        );
        ResearchResponseFactory responseFactory = new ResearchResponseFactory();

        orchestrator = new ResearchOrchestrator(
                new ResearchRequestValidator(),
                normalizer,
                discoveryService,
                sourceProcessor,
                evidenceService,
                persister,
                responseFactory,
                discoveryProperties,
                pipelineProperties
        );
    }

    @Test
    @DisplayName("Task 16: UNKNOWN Handling - missing requested attribute represented as UNKNOWN")
    void shouldHandleUnknownAttributeWhenEvidenceIsMissing() {
        ResearchTarget target = new ResearchTarget(
                "https://example.com/alice",
                "https://example.com/alice",
                "id-alice",
                EntityType.PERSON,
                "Alice",
                Map.of("targetFields", List.of("hobbies", "patents"))
        );

        Map<String, EvidenceTuple> attributes = new LinkedHashMap<>();
        attributes.put("name", new EvidenceTuple("Alice", "https://example.com/alice", "Display name", ConfidenceTier.HIGH));

        evidenceExtractor.applyTargetFields(target, attributes);

        assertThat(attributes).containsKey("hobbies");
        EvidenceTuple hobbies = attributes.get("hobbies");
        assertThat(hobbies.value()).isEqualTo("UNKNOWN");
        assertThat(hobbies.confidence()).isEqualTo(ConfidenceTier.UNKNOWN);
        assertThat(hobbies.sourceUrl()).isNull();
        assertThat(hobbies.evidenceSnippet()).contains("No reliable evidence found");

        assertThat(attributes).containsKey("patents");
        EvidenceTuple patents = attributes.get("patents");
        assertThat(patents.value()).isEqualTo("UNKNOWN");
        assertThat(patents.confidence()).isEqualTo(ConfidenceTier.UNKNOWN);
    }

    @Test
    @DisplayName("Task 16: Zero Hallucination - unsupported AI fact must be rejected")
    void shouldRejectUnsupportedAiFact() {
        String realSourceText = "Jane Doe is a software engineer at Acme Corporation in Seattle.";
        AiExtractedFact hallucinatedFact = new AiExtractedFact(
                "Astronaut",
                "Jane Doe is an astronaut for NASA in Houston.",
                0.95
        );

        boolean isGrounded = evidenceExtractor.isFactGroundedInSource(hallucinatedFact, realSourceText);
        assertThat(isGrounded).isFalse();

        AiExtractedFact validFact = new AiExtractedFact(
                "software engineer",
                "Jane Doe is a software engineer",
                0.90
        );
        boolean isValidGrounded = evidenceExtractor.isFactGroundedInSource(validFact, realSourceText);
        assertThat(isValidGrounded).isTrue();
    }

    @Test
    @DisplayName("Task 17: Confidence Scoring - reflects evidence quality and directness")
    void shouldCalculateEvidenceConfidenceScores() {
        Map<String, EvidenceTuple> attributes = new LinkedHashMap<>();

        evidenceExtractor.mergeAttribute(
                attributes,
                "role",
                "Principal Infrastructure Engineer",
                "https://cloudscale.example.com/team/jane-doe",
                "Official employee profile: Role: Principal Infrastructure Engineer",
                ConfidenceTier.HIGH
        );

        assertThat(attributes.get("role").confidence()).isEqualTo(ConfidenceTier.HIGH);

        evidenceExtractor.mergeAttribute(
                attributes,
                "location",
                "San Francisco, CA",
                "https://secondary-blog.com/snippet",
                "Mentioned based in San Francisco, CA",
                ConfidenceTier.MEDIUM
        );

        assertThat(attributes.get("location").confidence()).isEqualTo(ConfidenceTier.MEDIUM);
    }

    @Test
    @DisplayName("Task 18: Cross-Source Verification - corroborating sources boost confidence")
    void shouldBoostConfidenceWithCorroboratingSources() {
        Map<String, EvidenceTuple> attributes = new LinkedHashMap<>();

        // Source 1 asserts role with MEDIUM confidence
        evidenceExtractor.mergeAttribute(
                attributes,
                "role",
                "Principal Infrastructure Engineer",
                "https://source-a.com/profile",
                "Role: Principal Infrastructure Engineer",
                ConfidenceTier.MEDIUM
        );

        assertThat(attributes.get("role").confidence()).isEqualTo(ConfidenceTier.MEDIUM);
        assertThat(attributes.get("role").corroboratingSources()).contains("https://source-a.com/profile");

        // Source 2 confirms identical role
        evidenceExtractor.mergeAttribute(
                attributes,
                "role",
                "Principal Infrastructure Engineer",
                "https://source-b.com/profile",
                "Principal Infrastructure Engineer at CloudScale",
                ConfidenceTier.MEDIUM
        );

        EvidenceTuple updated = attributes.get("role");
        assertThat(updated.confidence()).isEqualTo(ConfidenceTier.HIGH);
        assertThat(updated.corroboratingSources()).containsExactlyInAnyOrder(
                "https://source-a.com/profile",
                "https://source-b.com/profile"
        );
        assertThat(updated.conflictDetected()).isFalse();
    }

    @Test
    @DisplayName("Task 19: Conflict Detection - contradictory information is preserved without silent overwrite")
    void shouldDetectAndPreserveConflictingInformation() {
        Map<String, EvidenceTuple> attributes = new LinkedHashMap<>();

        evidenceExtractor.mergeAttribute(
                attributes,
                "role",
                "Software Engineer",
                "https://source-a.com/profile",
                "Works as Software Engineer",
                ConfidenceTier.HIGH
        );

        // Source B contradicts with Product Manager
        evidenceExtractor.mergeAttribute(
                attributes,
                "role",
                "Product Manager",
                "https://source-b.com/profile",
                "Works as Product Manager",
                ConfidenceTier.HIGH
        );

        EvidenceTuple conflict = attributes.get("role");
        assertThat(conflict.conflictDetected()).isTrue();
        assertThat(conflict.confidence()).isEqualTo(ConfidenceTier.MEDIUM); // reduced tier
        assertThat(conflict.evidenceSnippet()).contains("Conflict");
        assertThat(conflict.evidenceSnippet()).contains("Product Manager");
        assertThat(conflict.corroboratingSources()).contains("https://source-a.com/profile", "https://source-b.com/profile");
    }

    @Test
    @DisplayName("Task 20: Dynamic Target Fields - research filters to requested target fields")
    void shouldFocusOutputToDynamicTargetFields() {
        ResearchRequest request = new ResearchRequest(
                "https://cloudscale.example.com/team/jane-doe",
                EntityType.PERSON,
                "Jane Doe",
                null,
                null,
                List.of("currentRole", "location"),
                ResearchDepth.NORMAL,
                null
        );

        ResearchResponse response = orchestrator.executeResearch(request);

        assertThat(response.status()).isEqualTo(ResearchStatus.COMPLETED);
        Map<String, EvidenceTuple> attrs = response.result().attributes();

        assertThat(attrs).containsKey("currentRole");
        assertThat(attrs).containsKey("location");
        assertThat(attrs).doesNotContainKey("education");
        assertThat(attrs).doesNotContainKey("technologies");
    }

    @Test
    @DisplayName("Task 20: Dynamic Target Fields - missing target field honestly represented as UNKNOWN")
    void shouldReturnUnknownForMissingTargetFieldWithoutHallucinating() {
        ResearchRequest request = new ResearchRequest(
                "https://cloudscale.example.com/team/jane-doe",
                EntityType.PERSON,
                "Jane Doe",
                null,
                null,
                List.of("currentRole", "cryptocurrencyWallet"),
                ResearchDepth.NORMAL,
                null
        );

        ResearchResponse response = orchestrator.executeResearch(request);

        Map<String, EvidenceTuple> attrs = response.result().attributes();
        assertThat(attrs).containsKey("cryptocurrencyWallet");
        EvidenceTuple crypto = attrs.get("cryptocurrencyWallet");
        assertThat(crypto.value()).isEqualTo("UNKNOWN");
        assertThat(crypto.confidence()).isEqualTo(ConfidenceTier.UNKNOWN);
        assertThat(crypto.sourceUrl()).isNull();
    }

    @Test
    @DisplayName("Task 21: Person Enrichment - extracts rich attributes while rejecting same-name conflicts")
    void shouldEnrichPersonEntityAndFilterConflictingHomonym() {
        ResearchRequest request = new ResearchRequest(
                "https://www.linkedin.com/in/parv-agrawal-170174308",
                EntityType.PERSON,
                "Parv Agrawal",
                "MNIT Jaipur",
                "Undergraduate Student",
                List.of("role", "organization", "location"),
                ResearchDepth.NORMAL,
                null
        );

        ResearchResponse response = orchestrator.executeResearch(request);

        assertThat(response.status()).isEqualTo(ResearchStatus.COMPLETED);
        Map<String, EvidenceTuple> attrs = response.result().attributes();

        assertThat(attrs).containsKey("organization");
        assertThat(attrs.get("organization").value()).containsAnyOf("MNIT Jaipur", "Malaviya National Institute of Technology");
        assertThat(attrs).containsKey("location");
        assertThat(attrs.get("location").value()).contains("Jaipur");

        // Verify that D.E. Shaw and IIT Delhi sources were filtered out
        assertThat(response.sources()).noneMatch(s -> s.url().contains("deshaw.com") || s.url().contains("iitd.ac.in"));
    }

    @Test
    @DisplayName("Task 22: Organization Enrichment - extracts headquarters, website, and industry")
    void shouldEnrichOrganizationEntity() {
        ResearchRequest request = new ResearchRequest(
                "https://acme.org",
                EntityType.ORGANIZATION,
                "Acme Corporation"
        );

        ResearchResponse response = orchestrator.executeResearch(request);

        assertThat(response.status()).isEqualTo(ResearchStatus.COMPLETED);
        Map<String, EvidenceTuple> attrs = response.result().attributes();

        assertThat(attrs).containsKey("website");
        assertThat(attrs.get("website").value()).isEqualTo("https://acme.org/");
        assertThat(attrs).containsKey("description");
    }

    @Test
    @DisplayName("Task 23: Repository & Product Enrichment - uses identical core pipeline")
    void shouldEnrichProductRepositoryAndWebsiteEntitiesUsingSamePipeline() {
        ResearchRequest request = new ResearchRequest(
                "https://github.com/spring-projects/spring-boot",
                EntityType.REPOSITORY,
                "Spring Boot"
        );

        ResearchResponse response = orchestrator.executeResearch(request);

        assertThat(response.status()).isEqualTo(ResearchStatus.COMPLETED);
        Map<String, EvidenceTuple> attrs = response.result().attributes();

        assertThat(attrs).containsKey("repository");
        assertThat(attrs.get("repository").value()).isEqualTo("spring-projects/spring-boot");
        assertThat(attrs).containsKey("description");
    }

    @Test
    @DisplayName("Task 24: Adaptive Research - triggers targeted follow-up query when target field is missing")
    void shouldPerformAdaptiveFollowUpSearchWhenTargetFieldIsMissing() {
        ResearchRequest request = new ResearchRequest(
                null,
                EntityType.PERSON,
                "Parv Agrawal",
                "MNIT Jaipur",
                null,
                List.of("education"),
                ResearchDepth.NORMAL,
                null
        );

        ResearchResponse response = orchestrator.executeResearch(request);

        assertThat(response.status()).isEqualTo(ResearchStatus.COMPLETED);
        Map<String, EvidenceTuple> attrs = response.result().attributes();

        assertThat(attrs).containsKey("education");
        EvidenceTuple edu = attrs.get("education");
        assertThat(edu.value()).isNotEqualTo("UNKNOWN");
        assertThat(edu.value().toLowerCase()).contains("mnit");
    }

    @Test
    @DisplayName("Task 25: Research Depth Limits - SHALLOW depth disables adaptive follow-up")
    void shouldEnforceResearchDepthLimits() {
        assertThat(ResearchDepth.SHALLOW.maxAdaptiveQueries()).isEqualTo(0);
        assertThat(ResearchDepth.SHALLOW.maxSources()).isEqualTo(3);

        assertThat(ResearchDepth.NORMAL.maxAdaptiveQueries()).isEqualTo(1);
        assertThat(ResearchDepth.NORMAL.maxSources()).isEqualTo(5);

        assertThat(ResearchDepth.DEEP.maxAdaptiveQueries()).isEqualTo(2);
        assertThat(ResearchDepth.DEEP.maxSources()).isEqualTo(8);

        // In SHALLOW mode, if a field is not found initially, 0 adaptive queries are made
        ResearchRequest request = new ResearchRequest(
                null,
                EntityType.PERSON,
                "Obscure Entity With Missing Info",
                null,
                null,
                List.of("nonExistentField"),
                ResearchDepth.SHALLOW,
                null
        );

        ResearchResponse response = orchestrator.executeResearch(request);
        assertThat(response.status()).isEqualTo(ResearchStatus.COMPLETED);
        assertThat(response.result().attributes().get("nonExistentField").value()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("Task 25: Stopping Logic - early stop when all requested fields are satisfied")
    void shouldStopEarlyWhenAllTargetFieldsAreCovered() {
        ResearchRequest request = new ResearchRequest(
                "https://cloudscale.example.com/team/jane-doe",
                EntityType.PERSON,
                "Jane Doe",
                null,
                null,
                List.of("currentRole", "location"),
                ResearchDepth.DEEP,
                null
        );

        ResearchResponse response = orchestrator.executeResearch(request);

        assertThat(response.status()).isEqualTo(ResearchStatus.COMPLETED);
        Map<String, EvidenceTuple> attrs = response.result().attributes();
        assertThat(attrs.get("currentRole").value()).isNotEqualTo("UNKNOWN");
        assertThat(attrs.get("location").value()).isNotEqualTo("UNKNOWN");
    }
}
