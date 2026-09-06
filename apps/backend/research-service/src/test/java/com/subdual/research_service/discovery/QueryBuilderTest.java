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
import java.util.Map;

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

    @Test
    @DisplayName("Should anchor common or single-token names with organization in Strategy E")
    void shouldAnchorCommonNameWithOrg() {
        ResearchTarget target = new ResearchTarget(
                "",
                "",
                "id-krati",
                EntityType.PERSON,
                "Krati",
                java.util.Map.of(
                        "organization", "Google",
                        "targetFields", List.of("role", "education")
                )
        );

        List<ResearchQuery> queries = queryBuilder.buildRequirementQueries(target);
        List<String> queryTexts = queries.stream()
                .filter(q -> q.strategy() == QueryStrategy.NAME_AND_FIELD)
                .map(ResearchQuery::queryText)
                .toList();

        assertThat(queryTexts).isNotEmpty();
        for (String qText : queryTexts) {
            assertThat(qText)
                    .as("Strategy E query must anchor with organization")
                    .contains("\"Krati\"")
                    .contains("\"Google\"");
        }
    }

    @Test
    @DisplayName("Should anchor common or single-token names with canonical profile slug when org is missing")
    void shouldAnchorCommonNameWithProfileSlug() {
        ResearchTarget target = new ResearchTarget(
                "https://www.linkedin.com/in/krati-mittal",
                "https://linkedin.com/in/krati-mittal",
                "id-krati-slug",
                EntityType.PERSON,
                "Krati",
                java.util.Map.of(
                        "targetFields", List.of("role", "skills")
                )
        );

        List<ResearchQuery> queries = queryBuilder.buildRequirementQueries(target);
        List<String> queryTexts = queries.stream()
                .filter(q -> q.strategy() == QueryStrategy.NAME_AND_FIELD)
                .map(ResearchQuery::queryText)
                .toList();

        assertThat(queryTexts).isNotEmpty();
        for (String qText : queryTexts) {
            assertThat(qText)
                    .as("Strategy E query must anchor with profile slug")
                    .contains("\"Krati\"")
                    .contains("krati-mittal");
        }
    }

    @Test
    @DisplayName("Should generate progressive queries for multi-anchor target without null or empty strings")
    void shouldGenerateProgressiveQueriesWithoutNullOrEmptyStrings() {
        ResearchTarget target = new ResearchTarget(
                "https://www.linkedin.com/in/vardhan-bhati-33b537326",
                "https://linkedin.com/in/vardhan-bhati-33b537326",
                "id-vardhan",
                EntityType.PERSON,
                "Vardhan Bhati",
                java.util.Map.of(
                        "organization", "Entrepreneurship Development Cell MNIT Jaipur",
                        "role", "Lead",
                        "location", "Jaipur, India",
                        "targetFields", List.of("skills", "education")
                )
        );

        List<ResearchQuery> queries = queryBuilder.buildRequirementQueries(target);
        List<String> queryTexts = queries.stream().map(ResearchQuery::queryText).toList();

        assertThat(queryTexts).isNotEmpty();

        // Query 1: Exact Name + URL slug
        assertThat(queryTexts).anyMatch(q -> q.contains("\"Vardhan Bhati\"") && q.contains("vardhan-bhati-33b537326"));

        // Query 2: Exact Name + Organization
        assertThat(queryTexts).anyMatch(q -> q.contains("\"Vardhan Bhati\"") && q.contains("\"Entrepreneurship Development Cell MNIT Jaipur\""));

        // Query 3: Exact Name + Organization + Role
        assertThat(queryTexts).anyMatch(q -> q.contains("\"Vardhan Bhati\"") && q.contains("\"Entrepreneurship Development Cell MNIT Jaipur\"") && q.contains("\"Lead\""));

        // Null/Malformed Safety: Ensure no "null", "undefined", or empty quotes exist in any query
        for (String q : queryTexts) {
            assertThat(q).doesNotContain("null")
                    .doesNotContain("undefined")
                    .doesNotContain("\"\"")
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("Case G: Should cleanly generate queries when optional fields are missing")
    void shouldCleanlyGenerateQueriesWhenOptionalFieldsMissing() {
        ResearchTarget target = new ResearchTarget(
                "",
                "",
                "id-minimal",
                EntityType.PERSON,
                "Vardhan Bhati",
                Map.of()
        );

        List<ResearchQuery> queries = queryBuilder.buildRequirementQueries(target);
        List<String> queryTexts = queries.stream().map(ResearchQuery::queryText).toList();

        assertThat(queryTexts).isNotEmpty();
        for (String q : queryTexts) {
            assertThat(q).doesNotContain("null")
                    .doesNotContain("undefined")
                    .doesNotContain("\"\"")
                    .isNotBlank();
        }
    }
}
