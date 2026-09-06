package com.subdual.research_service.extraction.extractor.field;

import com.subdual.research_service.api.dto.EvidenceTuple;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FieldExtractorTest {

    private final RoleFieldExtractor roleExtractor = new RoleFieldExtractor();
    private final OrganizationFieldExtractor orgExtractor = new OrganizationFieldExtractor();
    private final EducationFieldExtractor eduExtractor = new EducationFieldExtractor();
    private final LocationFieldExtractor locExtractor = new LocationFieldExtractor();
    private final TechFieldExtractor techExtractor = new TechFieldExtractor();

    @Test
    @DisplayName("Should extract role from document text with verbatim snippet")
    void shouldExtractRole() {
        ResearchTarget target = new ResearchTarget("https://linkedin.com/in/jane", "https://linkedin.com/in/jane", "id-1", EntityType.PERSON, "Jane");
        ExtractedDocument doc = new ExtractedDocument(
                "https://cloudscale.example.com/team/jane",
                "Jane Doe Team Page",
                null,
                null,
                "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems.",
                Instant.now()
        );

        EvidenceTuple result = roleExtractor.extract(doc, target);

        assertThat(result).isNotNull();
        assertThat(result.value()).contains("Principal Infrastructure Engineer");
        assertThat(result.evidenceSnippet()).contains("Jane Doe is a Principal Infrastructure Engineer");
        assertThat(result.extractionMethod()).isEqualTo("FIELD_SPECIFIC_ROLE_EXTRACTOR");
    }

    @Test
    @DisplayName("Should extract organization from document text")
    void shouldExtractOrganization() {
        ResearchTarget target = new ResearchTarget("https://linkedin.com/in/jane", "https://linkedin.com/in/jane", "id-1", EntityType.PERSON, "Jane");
        ExtractedDocument doc = new ExtractedDocument(
                "https://blog.example.com/interview",
                "Interview with Jane",
                null,
                null,
                "Jane works at CloudScale Technologies in San Francisco.",
                Instant.now()
        );

        EvidenceTuple result = orgExtractor.extract(doc, target);

        assertThat(result).isNotNull();
        assertThat(result.value()).contains("CloudScale Technologies");
        assertThat(result.extractionMethod()).isEqualTo("FIELD_SPECIFIC_ORG_EXTRACTOR");
    }

    @Test
    @DisplayName("Should extract education from document text")
    void shouldExtractEducation() {
        ResearchTarget target = new ResearchTarget("https://linkedin.com/in/jane", "https://linkedin.com/in/jane", "id-1", EntityType.PERSON, "Jane");
        ExtractedDocument doc = new ExtractedDocument(
                "https://alumni.mit.edu/jane",
                "Alumni Jane",
                null,
                null,
                "Jane graduated from MIT with a degree in Computer Science.",
                Instant.now()
        );

        EvidenceTuple result = eduExtractor.extract(doc, target);

        assertThat(result).isNotNull();
        assertThat(result.value()).contains("MIT");
        assertThat(result.extractionMethod()).isEqualTo("FIELD_SPECIFIC_EDU_EXTRACTOR");
    }

    @Test
    @DisplayName("Should extract location and technologies from document text")
    void shouldExtractLocationAndTech() {
        ResearchTarget target = new ResearchTarget("https://linkedin.com/in/jane", "https://linkedin.com/in/jane", "id-1", EntityType.PERSON, "Jane");
        ExtractedDocument doc = new ExtractedDocument(
                "https://example.com/jane",
                "Jane Profile",
                null,
                null,
                "Jane is based in San Francisco, California. Her core tech stack: Java, Spring Boot, Docker, and Kubernetes.",
                Instant.now()
        );

        EvidenceTuple loc = locExtractor.extract(doc, target);
        assertThat(loc).isNotNull();
        assertThat(loc.value()).contains("San Francisco");

        EvidenceTuple tech = techExtractor.extract(doc, target);
        assertThat(tech).isNotNull();
        assertThat(tech.value()).contains("Java");
    }
}
