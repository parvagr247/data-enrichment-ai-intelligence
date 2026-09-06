package com.subdual.research_service.discovery;

import com.subdual.research_service.discovery.model.QueryIntent;
import com.subdual.research_service.discovery.model.QueryStrategy;
import com.subdual.research_service.discovery.model.ResearchQuery;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryBuilderTest {

    private QueryBuilder queryBuilder;

    @BeforeEach
    void setUp() {
        queryBuilder = new QueryBuilder();
    }

    @Test
    @DisplayName("Should generate requirement-aware queries with multiple strategies and intents")
    void shouldGenerateRequirementQueries() {
        ResearchTarget target = new ResearchTarget(
                "https://www.linkedin.com/in/johndoe",
                "https://www.linkedin.com/in/johndoe",
                "id-10",
                EntityType.PERSON,
                "John Doe",
                java.util.Map.of(
                        "organization", "Acme Corp",
                        "role", "Staff Engineer",
                        "targetFields", List.of("role", "education", "skills")
                )
        );

        List<ResearchQuery> queries = queryBuilder.buildRequirementQueries(target);

        assertThat(queries).isNotEmpty();
        // Strategy A: EXACT_NAME_AND_ORG
        assertThat(queries.stream().anyMatch(q -> q.strategy() == QueryStrategy.EXACT_NAME_AND_ORG)).isTrue();
        // Strategy B: NAME_AND_FIELD for education
        assertThat(queries.stream().anyMatch(q -> q.intent() == QueryIntent.EDUCATION)).isTrue();
        // Strategy B: NAME_AND_FIELD for skills/tech
        assertThat(queries.stream().anyMatch(q -> q.intent() == QueryIntent.TECHNOLOGY)).isTrue();
    }

    @Test
    @DisplayName("Should build adaptive queries for missing fields")
    void shouldBuildAdaptiveQueries() {
        ResearchTarget target = new ResearchTarget(
                "https://example.com/jane",
                "https://example.com/jane",
                "id-11",
                EntityType.PERSON,
                "Jane Doe",
                java.util.Map.of("organization", "Google")
        );

        List<ResearchQuery> adaptive = queryBuilder.buildAdaptiveResearchQueries(target, List.of("education", "role"));

        assertThat(adaptive).hasSize(2);
        assertThat(adaptive.get(0).intent()).isEqualTo(QueryIntent.EDUCATION);
        assertThat(adaptive.get(0).queryText()).contains("Jane Doe").contains("education");
    }

    @Test
    @DisplayName("Should map field names to appropriate QueryIntent")
    void shouldMapFieldToIntent() {
        assertThat(QueryBuilder.mapFieldToIntent("role")).isEqualTo(QueryIntent.ROLE);
        assertThat(QueryBuilder.mapFieldToIntent("employer")).isEqualTo(QueryIntent.ORGANIZATION);
        assertThat(QueryBuilder.mapFieldToIntent("university")).isEqualTo(QueryIntent.EDUCATION);
        assertThat(QueryBuilder.mapFieldToIntent("city")).isEqualTo(QueryIntent.LOCATION);
        assertThat(QueryBuilder.mapFieldToIntent("skills")).isEqualTo(QueryIntent.TECHNOLOGY);
        assertThat(QueryBuilder.mapFieldToIntent("unknown_field")).isEqualTo(QueryIntent.GENERAL_PROFILE);
    }
}
