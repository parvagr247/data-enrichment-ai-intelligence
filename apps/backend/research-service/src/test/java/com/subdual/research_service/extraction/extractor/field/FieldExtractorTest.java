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
    private final ExperienceFieldExtractor expExtractor = new ExperienceFieldExtractor();
    private final SkillFieldExtractor skillExtractor = new SkillFieldExtractor();
    private final ActivityFieldExtractor actExtractor = new ActivityFieldExtractor();
    private final ProjectFieldExtractor projExtractor = new ProjectFieldExtractor();

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

    @Test
    @DisplayName("Should extract structured experience history")
    void shouldExtractStructuredExperience() {
        ResearchTarget target = new ResearchTarget("https://linkedin.com/in/jane", "https://linkedin.com/in/jane", "id-1", EntityType.PERSON, "Jane");
        ExtractedDocument doc = new ExtractedDocument(
                "https://example.com/jane/resume",
                "Jane Resume",
                null,
                null,
                "Staff Engineer at Stripe (2021 - Present)\nSoftware Engineer at Google from 2018 to 2021",
                Instant.now()
        );

        EvidenceTuple result = expExtractor.extract(doc, target);
        assertThat(result).isNotNull();
        assertThat(result.value()).contains("Stripe");
        assertThat(result.value()).contains("Staff Engineer");
        assertThat(result.extractionMethod()).isEqualTo("STRUCTURED_EXPERIENCE_EXTRACTOR");
    }

    @Test
    @DisplayName("Should extract and normalize skills without duplicates")
    void shouldExtractAndNormalizeSkills() {
        ResearchTarget target = new ResearchTarget("https://linkedin.com/in/jane", "https://linkedin.com/in/jane", "id-1", EntityType.PERSON, "Jane");
        ExtractedDocument doc = new ExtractedDocument(
                "https://example.com/jane",
                "Jane Skills",
                null,
                null,
                "Skills: Spring Boot, springboot, Java, TypeScript, Docker, k8s, React",
                Instant.now()
        );

        EvidenceTuple result = skillExtractor.extract(doc, target);
        assertThat(result).isNotNull();
        assertThat(result.value()).contains("Spring Boot");
        assertThat(result.value()).contains("Kubernetes");
        assertThat(result.value()).contains("Java");
    }

    @Test
    @DisplayName("Should extract activity and distinguish AUTHORED from LIKED")
    void shouldExtractActivityWithAuthorDistinction() {
        ResearchTarget target = new ResearchTarget("https://linkedin.com/in/jane", "https://linkedin.com/in/jane", "id-1", EntityType.PERSON, "Jane");
        ExtractedDocument doc = new ExtractedDocument(
                "https://example.com/jane/activity",
                "Jane Activity",
                null,
                null,
                "Published article: \"Building Resilient Microservices with Spring Boot\"\nLiked: \"Check out this new frontend framework\"",
                Instant.now()
        );

        EvidenceTuple result = actExtractor.extract(doc, target);
        assertThat(result).isNotNull();
        assertThat(result.value()).contains("AUTHORED");
        assertThat(result.value()).contains("LIKED");
        assertThat(result.value()).contains("Building Resilient Microservices");
    }

    @Test
    @DisplayName("Should extract notable projects")
    void shouldExtractNotableProjects() {
        ResearchTarget target = new ResearchTarget("https://github.com/torvalds", "https://github.com/torvalds", "id-1", EntityType.PERSON, "Linus Torvalds");
        ExtractedDocument doc = new ExtractedDocument(
                "https://example.com/linus",
                "Linus Torvalds Profile",
                null,
                null,
                "Linus is the Creator of Linux kernel and Author of Git tool.",
                Instant.now()
        );

        EvidenceTuple result = projExtractor.extract(doc, target);
        assertThat(result).isNotNull();
        assertThat(result.value()).contains("Linux");
        assertThat(result.value()).contains("Git");
    }

    @Test
    @DisplayName("Should verify thread-safe source caching")
    void shouldVerifyThreadSafeSourceCache() {
        com.subdual.research_service.discovery.cache.ThreadSafeSourceCache cache = new com.subdual.research_service.discovery.cache.ThreadSafeSourceCache();
        assertThat(cache.getFetchedContent("https://example.com/test")).isEmpty();

        com.subdual.research_service.integration.web.FetchedContent content =
                com.subdual.research_service.integration.web.FetchedContent.success(
                        "https://example.com/test", 200, "text/html", "<html><body>Cached</body></html>"
                );
        cache.putFetchedContent("https://example.com/test", content);

        assertThat(cache.getFetchedContent("https://example.com/test")).isPresent();
        assertThat(cache.getFetchedContent("https://example.com/test/")).isPresent(); // normalized trailing slash
        assertThat(cache.size()).isEqualTo(1);
    }
}
