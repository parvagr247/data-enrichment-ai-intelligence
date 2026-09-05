package com.subdual.research_service.research.pipeline;

import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeterministicRelevanceEvaluatorTest {

    private DeterministicRelevanceEvaluator evaluator;
    private ResearchTarget target;

    @BeforeEach
    void setUp() {
        evaluator = new DeterministicRelevanceEvaluator();
        target = new ResearchTarget(
                "https://spring.io",
                "https://spring.io/",
                "id-123",
                EntityType.ORGANIZATION,
                "Spring"
        );
    }

    @Test
    @DisplayName("Should assign highest score to OFFICIAL_WEBSITE")
    void shouldScoreOfficialWebsiteHighest() {
        double scoreOfficial = evaluator.evaluateRelevance(0.90, "OFFICIAL_WEBSITE", "https://spring.io/about", "Spring About", target);
        double scoreBlog = evaluator.evaluateRelevance(0.90, "BLOG", "https://other.com/blog", "Other Blog", target);

        assertThat(scoreOfficial).isGreaterThan(scoreBlog);
        assertThat(scoreOfficial).isEqualTo(0.95);
    }

    @Test
    @DisplayName("Should give GITHUB higher weight when entityType is REPOSITORY")
    void shouldGiveRepositoryWeightToGithub() {
        ResearchTarget repoTarget = new ResearchTarget(
                "https://github.com/spring-projects/spring-boot",
                "https://github.com/spring-projects/spring-boot",
                "id-456",
                EntityType.REPOSITORY,
                "Spring Boot"
        );

        double repoScore = evaluator.evaluateRelevance(1.0, "GITHUB", "https://github.com/spring-projects/spring-boot", "Repo", repoTarget);
        double orgScore = evaluator.evaluateRelevance(1.0, "GITHUB", "https://github.com/spring-projects/spring-boot", "Repo", target);

        assertThat(repoScore).isGreaterThan(orgScore);
    }

    @Test
    @DisplayName("Should bound score within 0.00 and 1.00")
    void shouldBoundScore() {
        double maxScore = evaluator.evaluateRelevance(1.5, "OFFICIAL_WEBSITE", "https://spring.io/test", "Test", target);
        assertThat(maxScore).isLessThanOrEqualTo(1.00);

        double minScore = evaluator.evaluateRelevance(-0.5, "UNKNOWN", "https://unknown.com", "Test", target);
        assertThat(minScore).isGreaterThanOrEqualTo(0.00);
    }
}
