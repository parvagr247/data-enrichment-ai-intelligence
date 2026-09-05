package com.subdual.research_service.discovery;

import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryBuilderTest {

    private QueryBuilder queryBuilder;

    @BeforeEach
    void setUp() {
        queryBuilder = new QueryBuilder();
    }

    @Test
    @DisplayName("Should build anchored query with domain and path when both URL and name are present")
    void shouldBuildAnchoredQueryForUrlAndName() {
        ResearchTarget target = new ResearchTarget(
                "https://www.linkedin.com/in/jane-doe",
                "https://www.linkedin.com/in/jane-doe",
                "id-1",
                EntityType.PERSON,
                "Jane Doe"
        );

        String query = queryBuilder.buildDiscoveryQuery(target);
        assertThat(query).isEqualTo("\"Jane Doe\" linkedin.com/in/jane-doe");
    }

    @Test
    @DisplayName("Should build anchored query for repository with URL and display name")
    void shouldBuildAnchoredQueryForRepositoryWithUrl() {
        ResearchTarget target = new ResearchTarget(
                "https://github.com/spring-projects/spring-boot",
                "https://github.com/spring-projects/spring-boot",
                "id-2",
                EntityType.REPOSITORY,
                "Spring Boot"
        );

        String query = queryBuilder.buildDiscoveryQuery(target);
        assertThat(query).isEqualTo("\"Spring Boot\" github.com/spring-projects/spring-boot");
    }

    @Test
    @DisplayName("Should build anchored query for organization with URL and display name")
    void shouldBuildAnchoredQueryForOrganizationWithUrl() {
        ResearchTarget target = new ResearchTarget(
                "https://openai.com",
                "https://openai.com/",
                "id-3",
                EntityType.ORGANIZATION,
                "OpenAI"
        );

        String query = queryBuilder.buildDiscoveryQuery(target);
        assertThat(query).isEqualTo("\"OpenAI\" openai.com company");
    }

    @Test
    @DisplayName("Should build broad discovery query when only name is provided (no URL)")
    void shouldBuildBroadQueryForNameOnly() {
        ResearchTarget orgTarget = new ResearchTarget(
                null, null, "id-4", EntityType.ORGANIZATION, "OpenAI"
        );
        assertThat(queryBuilder.buildDiscoveryQuery(orgTarget)).isEqualTo("OpenAI company official");

        ResearchTarget repoTarget = new ResearchTarget(
                null, null, "id-5", EntityType.REPOSITORY, "Spring Boot"
        );
        assertThat(queryBuilder.buildDiscoveryQuery(repoTarget)).isEqualTo("Spring Boot repository source code");

        ResearchTarget personTarget = new ResearchTarget(
                null, null, "id-6", EntityType.PERSON, "Jane Doe"
        );
        assertThat(queryBuilder.buildDiscoveryQuery(personTarget)).isEqualTo("Jane Doe profile biography");
    }

    @Test
    @DisplayName("Should handle null entityType gracefully without NullPointerException")
    void shouldHandleNullEntityTypeGracefully() {
        ResearchTarget target = new ResearchTarget(
                null, null, "id-7", null, "Something"
        );

        String query = queryBuilder.buildDiscoveryQuery(target);
        assertThat(query).isEqualTo("Something overview");
    }

    @Test
    @DisplayName("Should fallback to domain and path when display name is omitted")
    void shouldFallbackToDomainAndPathWhenDisplayNameOmitted() {
        ResearchTarget target = new ResearchTarget(
                "https://github.com/spring-projects/spring-boot",
                "https://github.com/spring-projects/spring-boot",
                "id-8",
                EntityType.REPOSITORY,
                "https://github.com/spring-projects/spring-boot"
        );

        String query = queryBuilder.buildDiscoveryQuery(target);
        assertThat(query).isEqualTo("spring-projects spring-boot repository");
    }

    @Test
    @DisplayName("Should return empty string for null target")
    void shouldReturnEmptyStringForNullTarget() {
        assertThat(queryBuilder.buildDiscoveryQuery(null)).isEmpty();
    }

    @Test
    @DisplayName("Should resolve type keywords correctly")
    void shouldResolveTypeKeywords() {
        assertThat(queryBuilder.resolveTypeKeyword(EntityType.ORGANIZATION)).isEqualTo("company");
        assertThat(queryBuilder.resolveTypeKeyword(EntityType.REPOSITORY)).isEqualTo("repository");
        assertThat(queryBuilder.resolveTypeKeyword(EntityType.PERSON)).isEqualTo("profile");
        assertThat(queryBuilder.resolveTypeKeyword(EntityType.PRODUCT)).isEqualTo("product");
        assertThat(queryBuilder.resolveTypeKeyword(EntityType.WEBSITE)).isEqualTo("official");
        assertThat(queryBuilder.resolveTypeKeyword(null)).isEqualTo("overview");
    }
}
