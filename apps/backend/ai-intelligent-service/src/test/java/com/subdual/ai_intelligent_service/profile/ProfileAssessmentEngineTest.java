package com.subdual.ai_intelligent_service.profile;

import com.subdual.ai_intelligent_service.enrichment.api.dto.common.FactEvidenceDto;
import com.subdual.ai_intelligent_service.profile.api.dto.request.ProfileAssessmentRequest;
import com.subdual.ai_intelligent_service.profile.api.dto.response.ProfileAssessmentResponse;
import com.subdual.ai_intelligent_service.profile.model.ObjectiveAssessment.PriorityTier;
import com.subdual.ai_intelligent_service.profile.model.RecommendedApproach.ApproachType;
import com.subdual.ai_intelligent_service.profile.model.ResearchFinding.FindingType;
import com.subdual.ai_intelligent_service.profile.model.ResearchObjective;
import com.subdual.ai_intelligent_service.profile.service.helper.ProfileExtractionHelper;
import com.subdual.ai_intelligent_service.profile.service.impl.DeterministicProfileAssessmentEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileAssessmentEngineTest {

    private DeterministicProfileAssessmentEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DeterministicProfileAssessmentEngine(new ProfileExtractionHelper());
    }

    @Test
    @DisplayName("Should handle blank objective without fabricating relevance scores")
    void shouldHandleBlankObjectiveNeutrally() {
        ProfileAssessmentRequest request = new ProfileAssessmentRequest(
                Map.of("Name", "Sarah Jenkins"),
                "Sarah Jenkins",
                "https://example.com/sarah",
                "PERSON",
                ResearchObjective.blank(),
                Map.of(
                        "currentRole", new FactEvidenceDto("currentRole", "Software Engineer", "https://example.com/sarah", "Sarah is a Software Engineer", "HIGH", List.of(), false),
                        "currentOrganization", new FactEvidenceDto("currentOrganization", "TechCorp", "https://example.com/sarah", "works at TechCorp", "HIGH", List.of(), false)
                ),
                List.of("https://example.com/sarah"),
                List.of("Sarah is a Software Engineer at TechCorp")
        );

        ProfileAssessmentResponse response = engine.assessProfile(request);

        assertThat(response).isNotNull();
        assertThat(response.profile().currentRole()).isEqualTo("Software Engineer");
        assertThat(response.profile().currentOrganization()).isEqualTo("TechCorp");
        assertThat(response.profile().professionalSummary()).contains("Software Engineer at TechCorp");

        // Blank objective must produce neutral assessment
        assertThat(response.assessment().overallScore()).isEqualTo(0);
        assertThat(response.assessment().priorityTier()).isEqualTo(PriorityTier.NONE);
        assertThat(response.assessment().whyRelevant()).contains("No specific research objective was provided");
        assertThat(response.recommendation().approachType()).isEqualTo(ApproachType.NETWORKING_CONVERSATION);
    }

    @Test
    @DisplayName("Should score and recommend recruiter for Java/Spring internship objective")
    void shouldAssessRecruiterForInternshipObjective() {
        String objectiveText = "Identify and enrich profiles of people most likely to help with a Java/Spring Boot internship, especially recruiters, hiring managers, and Java engineers";
        ResearchObjective objective = ResearchObjective.from(objectiveText);

        ProfileAssessmentRequest request = new ProfileAssessmentRequest(
                Map.of("Name", "Alex Rivera"),
                "Alex Rivera",
                "https://linkedin.com/in/alex-rivera",
                "PERSON",
                objective,
                Map.of(
                        "currentRole", new FactEvidenceDto("currentRole", "Senior Technical Recruiter", "https://linkedin.com/in/alex-rivera", "Senior Technical Recruiter at CloudScale", "HIGH", List.of(), false),
                        "currentOrganization", new FactEvidenceDto("currentOrganization", "CloudScale Systems", "https://linkedin.com/in/alex-rivera", "CloudScale Systems", "HIGH", List.of(), false),
                        "activity", new FactEvidenceDto("activity", "[AUTHORED] We are actively hiring backend interns and junior engineers for our Spring Boot platform!", "https://linkedin.com/in/alex-rivera", "We are hiring interns", "HIGH", List.of(), false)
                ),
                List.of("https://linkedin.com/in/alex-rivera"),
                List.of("We are actively hiring backend interns for our Spring Boot platform!")
        );

        ProfileAssessmentResponse response = engine.assessProfile(request);

        assertThat(response).isNotNull();
        assertThat(response.profile().currentRole()).isEqualTo("Senior Technical Recruiter");
        assertThat(response.profile().currentOrganization()).isEqualTo("CloudScale Systems");
        assertThat(response.profile().publicActivity()).isNotEmpty();
        assertThat(response.profile().publicActivity().get(0).classification()).isIn("HIRING_ANNOUNCEMENT", "INTERNSHIP_POST");

        // Objective assessment
        assertThat(response.assessment().overallScore()).isGreaterThanOrEqualTo(80);
        assertThat(response.assessment().priorityTier()).isEqualTo(PriorityTier.HIGH);
        assertThat(response.assessment().whyRelevant()).contains("talent acquisition");
        assertThat(response.assessment().dimensions()).containsKey("roleRelevance");
        assertThat(response.assessment().dimensions().get("roleRelevance").score()).isGreaterThanOrEqualTo(90);

        // Recommended approach
        assertThat(response.recommendation().approachType()).isEqualTo(ApproachType.RECRUITER_OUTREACH);
        assertThat(response.recommendation().suggestedTalkingPoints()).isNotEmpty();

        // Grounded findings
        assertThat(response.findings()).isNotEmpty();
        assertThat(response.findings().stream().anyMatch(f -> f.findingType() == FindingType.FACT_SOURCE_DERIVED)).isTrue();
        assertThat(response.findings().stream().anyMatch(f -> f.findingType() == FindingType.INFERRED_ASSESSMENT)).isTrue();
    }

    @Test
    @DisplayName("Should score and recommend staff engineer for technical guidance & Java mentoring")
    void shouldAssessStaffEngineerForTechnicalGuidance() {
        String objectiveText = "Looking for experienced backend mentors and engineering leads with deep Java and Spring Boot experience";
        ResearchObjective objective = ResearchObjective.from(objectiveText);

        ProfileAssessmentRequest request = new ProfileAssessmentRequest(
                Map.of("Name", "Dr. Sarah Chen"),
                "Dr. Sarah Chen",
                "https://linkedin.com/in/sarah-chen",
                "PERSON",
                objective,
                Map.of(
                        "currentRole", new FactEvidenceDto("currentRole", "Staff Backend Architect", "https://linkedin.com/in/sarah-chen", "Staff Backend Architect at FinTech Global", "HIGH", List.of(), false),
                        "currentOrganization", new FactEvidenceDto("currentOrganization", "FinTech Global", "https://linkedin.com/in/sarah-chen", "FinTech Global", "HIGH", List.of(), false),
                        "skills", new FactEvidenceDto("skills", "[\"Java\", \"Spring Boot\", \"Kubernetes\", \"Microservices\"]", "https://linkedin.com/in/sarah-chen", "Expert in Java and Spring Boot", "HIGH", List.of(), false),
                        "education", new FactEvidenceDto("education", "Ph.D. in Computer Science from MIT", "https://linkedin.com/in/sarah-chen", "PhD from MIT", "HIGH", List.of(), false),
                        "activity", new FactEvidenceDto("activity", "[AUTHORED] Designing High-Throughput Spring Boot 3 Microservices", "https://linkedin.com/in/sarah-chen", "Spring Boot 3 article", "HIGH", List.of(), false)
                ),
                List.of("https://linkedin.com/in/sarah-chen"),
                List.of("Designing High-Throughput Spring Boot 3 Microservices")
        );

        ProfileAssessmentResponse response = engine.assessProfile(request);

        assertThat(response).isNotNull();
        assertThat(response.profile().technicalExpertise()).contains("Java", "Spring Boot");
        assertThat(response.assessment().overallScore()).isGreaterThanOrEqualTo(85);
        assertThat(response.assessment().priorityTier()).isEqualTo(PriorityTier.HIGH);
        assertThat(response.assessment().dimensions().get("technicalRelevance").score()).isGreaterThanOrEqualTo(80);
        assertThat(response.recommendation().approachType()).isIn(ApproachType.TECHNICAL_GUIDANCE, ApproachType.HIRING_CONVERSATION);
    }
}
