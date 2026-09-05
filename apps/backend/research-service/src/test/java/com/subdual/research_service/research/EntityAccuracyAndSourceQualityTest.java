package com.subdual.research_service.research;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.api.dto.ResearchRequest;
import com.subdual.research_service.api.dto.ResearchResponse;
import com.subdual.research_service.api.dto.SourceItem;
import com.subdual.research_service.common.validation.ResearchRequestValidator;
import com.subdual.research_service.config.ResearchDiscoveryProperties;
import com.subdual.research_service.config.ResearchPipelineProperties;
import com.subdual.research_service.config.WebFetchProperties;
import com.subdual.research_service.discovery.QueryBuilder;
import com.subdual.research_service.discovery.provider.MockSearchProvider;
import com.subdual.research_service.discovery.service.DefaultResearchDiscoveryService;
import com.subdual.research_service.discovery.service.ResearchDiscoveryService;
import com.subdual.research_service.extraction.DefaultSourceEvidenceService;
import com.subdual.research_service.extraction.EvidenceExtractor;
import com.subdual.research_service.extraction.SourceEvidenceService;
import com.subdual.research_service.extraction.ai.document.ContentExtractor;
import com.subdual.research_service.extraction.ai.document.ExtractedDocument;
import com.subdual.research_service.extraction.support.EntityResolver;
import com.subdual.research_service.integration.ai.NoOpAiExtractionClient;
import com.subdual.research_service.integration.persistence.DefaultResearchSnapshotPersister;
import com.subdual.research_service.integration.persistence.NoOpDatasetPersistenceClient;
import com.subdual.research_service.integration.web.DefaultWebContentFetcher;
import com.subdual.research_service.research.model.ConfidenceTier;
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

class EntityAccuracyAndSourceQualityTest {

    private QueryBuilder queryBuilder;
    private EntityResolver entityResolver;
    private EvidenceExtractor evidenceExtractor;
    private ResearchOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        queryBuilder = new QueryBuilder();
        entityResolver = new EntityResolver();
        evidenceExtractor = new EvidenceExtractor(new NoOpAiExtractionClient());

        ResearchDiscoveryProperties discoveryProperties = new ResearchDiscoveryProperties(
                "mock", "https://api.tavily.com", "mock-key", 5, 5000
        );
        ResearchPipelineProperties pipelineProperties = new ResearchPipelineProperties(
                5, 50000
        );
        WebFetchProperties webFetchProperties = new WebFetchProperties(
                3000, 5000, 5, "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        );

        MockSearchProvider searchProvider = new MockSearchProvider();
        ResearchDiscoveryService discoveryService = new DefaultResearchDiscoveryService(
                searchProvider, queryBuilder, discoveryProperties
        );
        DefaultWebContentFetcher webContentFetcher = new DefaultWebContentFetcher(
                webFetchProperties, discoveryProperties
        );
        ContentExtractor contentExtractor = new ContentExtractor();
        SourceEvidenceService evidenceService = new DefaultSourceEvidenceService(
                webContentFetcher, contentExtractor, entityResolver, evidenceExtractor, pipelineProperties
        );
        SourceProcessor sourceProcessor = new SourceProcessor(
                new DeterministicSourceClassifier(), new DeterministicRelevanceEvaluator()
        );

        orchestrator = new ResearchOrchestrator(
                new ResearchRequestValidator(),
                new DefaultEntityNormalizer(),
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
    @DisplayName("Goal 4: QueryBuilder should build domain and profile-anchored query for URL + name")
    void shouldBuildAnchoredQueryForUrlAndName() {
        ResearchTarget target = new ResearchTarget(
                "https://www.linkedin.com/in/jane-doe",
                "https://www.linkedin.com/in/jane-doe",
                "id-linkedin-1",
                EntityType.PERSON,
                "Jane Doe"
        );

        String query = queryBuilder.buildDiscoveryQuery(target);
        assertThat(query).isEqualTo("\"Jane Doe\" linkedin.com/in/jane-doe");
    }

    @Test
    @DisplayName("Goal 2: EntityResolver should reject name-only match without corroborating signals for anchored person")
    void shouldRejectNameOnlyMatchWithoutCorroboratingSignals() {
        ResearchTarget target = new ResearchTarget(
                "https://www.linkedin.com/in/jane-doe",
                "https://www.linkedin.com/in/jane-doe",
                "id-linkedin-1",
                EntityType.PERSON,
                "Jane Doe"
        );

        ExtractedDocument nameOnlyDoc = new ExtractedDocument(
                "https://generic-blog.example.com/posts/gardening-tips",
                "Gardening Tips by Jane Doe",
                "Simple gardening tips for home growing.",
                "Gardening Blog",
                "Jane Doe writes about growing tomatoes, soil composition, and seasonal planting.",
                Instant.now()
        );

        EntityResolver.ResolutionResult result = entityResolver.resolve(target, nameOnlyDoc);

        assertThat(result.matched()).isFalse();
        assertThat(result.confidence()).isEqualTo(ConfidenceTier.LOW);
        assertThat(result.reason()).contains("Name-only match without corroborating");
    }

    @Test
    @DisplayName("Goal 2 & 6: EntityResolver should explicitly reject conflicting same-name profession profiles")
    void shouldRejectConflictingEntityProfessions() {
        ResearchTarget target = new ResearchTarget(
                "https://www.linkedin.com/in/jane-doe",
                "https://www.linkedin.com/in/jane-doe",
                "id-linkedin-1",
                EntityType.PERSON,
                "Jane Doe"
        );

        ExtractedDocument dentistDoc = new ExtractedDocument(
                "https://dentistry.example.com/dr-jane-doe",
                "Dr. Jane Doe, DDS - Family Dentistry & Dental Clinic",
                "Dr. Jane Doe provides dental care, teeth whitening, and pediatric dentistry.",
                "Dental Clinic",
                "Dr. Jane Doe, DDS is a family dentist at Downtown Dental Clinic in Chicago, IL specializing in pediatric dentistry.",
                Instant.now()
        );

        ExtractedDocument actressDoc = new ExtractedDocument(
                "https://imdb.example.com/name/jane-doe",
                "Jane Doe - Actress Filmography and Credits",
                "Jane Doe is a theater and film actress known for dramatic roles in independent movies.",
                "IMDb",
                "Jane Doe is an actress. Filmography includes award-winning short films and stage plays in Los Angeles, CA.",
                Instant.now()
        );

        EntityResolver.ResolutionResult dentistResult = entityResolver.resolve(target, dentistDoc);
        assertThat(dentistResult.matched()).isFalse();
        assertThat(dentistResult.confidence()).isEqualTo(ConfidenceTier.LOW);
        assertThat(dentistResult.reason()).contains("Conflicting entity identity signals detected");

        EntityResolver.ResolutionResult actressResult = entityResolver.resolve(target, actressDoc);
        assertThat(actressResult.matched()).isFalse();
        assertThat(actressResult.confidence()).isEqualTo(ConfidenceTier.LOW);
        assertThat(actressResult.reason()).contains("Conflicting entity identity signals detected");
    }

    @Test
    @DisplayName("Goal 2: EntityResolver should corroborate identity on profile slug / handle match")
    void shouldCorroborateIdentityOnProfileSlugMatch() {
        ResearchTarget target = new ResearchTarget(
                "https://www.linkedin.com/in/jane-doe",
                "https://www.linkedin.com/in/jane-doe",
                "id-linkedin-1",
                EntityType.PERSON,
                "Jane Doe"
        );

        ExtractedDocument companyDoc = new ExtractedDocument(
                "https://cloudscale.example.com/team/jane-doe",
                "Jane Doe - Engineering Team - CloudScale Systems",
                "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems based in San Francisco, CA.",
                "CloudScale Systems",
                "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems, based in San Francisco, CA. She graduated from MIT. Connect with Jane Doe on LinkedIn at linkedin.com/in/jane-doe.",
                Instant.now()
        );

        EntityResolver.ResolutionResult result = entityResolver.resolve(target, companyDoc);

        assertThat(result.matched()).isTrue();
        assertThat(result.confidence()).isEqualTo(ConfidenceTier.HIGH);
    }

    @Test
    @DisplayName("Goal 7: EvidenceExtractor should extract rich deterministic attributes for PERSON")
    void shouldExtractRichPersonAttributes() {
        ResearchTarget target = new ResearchTarget(
                "https://www.linkedin.com/in/jane-doe",
                "https://www.linkedin.com/in/jane-doe",
                "id-linkedin-1",
                EntityType.PERSON,
                "Jane Doe"
        );

        ExtractedDocument doc = new ExtractedDocument(
                "https://cloudscale.example.com/team/jane-doe",
                "Jane Doe - Engineering Team - CloudScale Systems",
                "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems based in San Francisco, CA.",
                "CloudScale Systems",
                "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems, based in San Francisco, CA. She graduated from MIT with honors.",
                Instant.now()
        );

        ResearchSource source = new ResearchSource(
                doc.url(), doc.title(), "OFFICIAL_WEBSITE", Instant.now(), 0.95, 0.95, "Engineering profile"
        );

        Map<String, EntityResolver.ResolutionResult> resolutions = Map.of(
                doc.url(), new EntityResolver.ResolutionResult(ConfidenceTier.HIGH, true, "Profile handle matched")
        );

        Map<String, EvidenceTuple> attributes = evidenceExtractor.extractEvidence(
                target, List.of(source), List.of(doc), resolutions
        );

        assertThat(attributes).containsKey("name");
        assertThat(attributes.get("name").value()).isEqualTo("Jane Doe");

        assertThat(attributes).containsKey("role");
        assertThat(attributes.get("role").value()).isEqualTo("Principal Infrastructure Engineer");

        assertThat(attributes).containsKey("current_organization");
        assertThat(attributes.get("current_organization").value()).isEqualTo("CloudScale Systems");

        assertThat(attributes).containsKey("location");
        assertThat(attributes.get("location").value()).isEqualTo("San Francisco, CA");

        assertThat(attributes).containsKey("education");
        assertThat(attributes.get("education").value()).isEqualTo("MIT");
    }

    @Test
    @DisplayName("Goal 5: Multiple agreeing sources should boost confidence tier and record corroborating sources")
    void shouldBoostConfidenceOnCorroboratingSources() {
        ResearchTarget target = new ResearchTarget(
                "https://www.linkedin.com/in/jane-doe",
                "https://www.linkedin.com/in/jane-doe",
                "id-linkedin-1",
                EntityType.PERSON,
                "Jane Doe"
        );

        ExtractedDocument doc1 = new ExtractedDocument(
                "https://cloudscale.example.com/team/jane-doe",
                "Jane Doe - Team",
                "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems.",
                "CloudScale",
                "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems.",
                Instant.now()
        );

        ExtractedDocument doc2 = new ExtractedDocument(
                "https://tech-conference.example.com/speakers/jane-doe",
                "Jane Doe - Speaker",
                "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems.",
                "TechConf",
                "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems.",
                Instant.now()
        );

        ResearchSource src1 = new ResearchSource(doc1.url(), doc1.title(), "OFFICIAL_WEBSITE", Instant.now(), 0.95);
        ResearchSource src2 = new ResearchSource(doc2.url(), doc2.title(), "NEWS", Instant.now(), 0.85);

        Map<String, EntityResolver.ResolutionResult> resolutions = Map.of(
                doc1.url(), new EntityResolver.ResolutionResult(ConfidenceTier.HIGH, true, "Slug match"),
                doc2.url(), new EntityResolver.ResolutionResult(ConfidenceTier.HIGH, true, "Slug match")
        );

        Map<String, EvidenceTuple> attributes = evidenceExtractor.extractEvidence(
                target, List.of(src1, src2), List.of(doc1, doc2), resolutions
        );

        EvidenceTuple roleTuple = attributes.get("role");
        assertThat(roleTuple).isNotNull();
        assertThat(roleTuple.value()).isEqualTo("Principal Infrastructure Engineer");
        assertThat(roleTuple.confidence()).isEqualTo(ConfidenceTier.HIGH);
        assertThat(roleTuple.corroboratingSources()).contains(doc1.url(), doc2.url());
    }

    @Test
    @DisplayName("Goal 6: Unmatched / rejected sources must not produce fabricated attributes")
    void shouldNotFabricateAttributesFromRejectedSources() {
        ResearchTarget target = new ResearchTarget(
                "https://www.linkedin.com/in/jane-doe",
                "https://www.linkedin.com/in/jane-doe",
                "id-linkedin-1",
                EntityType.PERSON,
                "Jane Doe"
        );

        ExtractedDocument dentistDoc = new ExtractedDocument(
                "https://dentistry.example.com/dr-jane-doe",
                "Dr. Jane Doe, DDS - Dentist",
                "Dental care in Chicago.",
                "Dentistry",
                "Role: Dentist\nCompany: Dental Clinic",
                Instant.now()
        );

        ResearchSource src = new ResearchSource(dentistDoc.url(), dentistDoc.title(), "OFFICIAL_WEBSITE", Instant.now(), 0.70);

        Map<String, EntityResolver.ResolutionResult> resolutions = Map.of(
                dentistDoc.url(), new EntityResolver.ResolutionResult(ConfidenceTier.LOW, false, "Rejected")
        );

        Map<String, EvidenceTuple> attributes = evidenceExtractor.extractEvidence(
                target, List.of(src), List.of(dentistDoc), resolutions
        );

        assertThat(attributes).doesNotContainKey("role");
        assertThat(attributes).doesNotContainKey("current_organization");
        assertThat(attributes).doesNotContainKey("location");
    }

    @Test
    @DisplayName("End-to-End: Inaccessible LinkedIn primary (HTTP 999) should preserve primary, extract from secondary, and return COMPLETED")
    void shouldHandleInaccessibleLinkedInWithSecondaryCorroborationEndToEnd() {
        ResearchRequest request = new ResearchRequest(
                "https://www.linkedin.com/in/jane-doe",
                EntityType.PERSON,
                "Jane Doe"
        );

        ResearchResponse response = orchestrator.executeResearch(request);

        assertThat(response).isNotNull();
        // Crucial requirement: status must be COMPLETED, not degraded to PARTIAL
        assertThat(response.status()).isEqualTo(ResearchStatus.COMPLETED);

        // Crucial requirement: primary URL must be preserved in sources list at rank 0
        assertThat(response.sources()).isNotEmpty();
        SourceItem primarySource = response.sources().get(0);
        assertThat(primarySource.url()).isEqualTo("https://www.linkedin.com/in/jane-doe");

        // Informative warning must be present explaining the HTTP 999 without treating it as fatal
        assertThat(response.warnings()).isNotEmpty();
        assertThat(response.warnings().stream().anyMatch(w -> w.contains("Primary source") && w.contains("999"))).isTrue();

        // Rich attributes extracted from corroborating secondary source (cloudscale.example.com/team/jane-doe)
        Map<String, EvidenceTuple> attributes = response.result().attributes();
        assertThat(attributes).containsKey("role");
        assertThat(attributes.get("role").value()).isEqualTo("Principal Infrastructure Engineer");

        assertThat(attributes).containsKey("current_organization");
        assertThat(attributes.get("current_organization").value()).isEqualTo("CloudScale Systems");

        assertThat(attributes).containsKey("location");
        assertThat(attributes.get("location").value()).isEqualTo("San Francisco, CA");

        assertThat(attributes).containsKey("education");
        assertThat(attributes.get("education").value()).isEqualTo("MIT");

        // Zero contamination from conflicting same-name sources (dentist and actress)
        assertThat(attributes.get("role").value()).doesNotContain("Dentist");
        assertThat(attributes.get("role").value()).doesNotContain("Actress");
        assertThat(attributes.get("current_organization").value()).doesNotContain("Dental");
    }
}
