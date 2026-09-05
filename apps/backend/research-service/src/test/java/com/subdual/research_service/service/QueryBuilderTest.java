package com.subdual.research_service.service;

import com.subdual.research_service.domain.EntityType;
import com.subdual.research_service.domain.ResearchTarget;
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
    @DisplayName("Should build targeted query for organization with display name")
    void shouldBuildOrganizationQuery() {
        ResearchTarget target = new ResearchTarget(
                "https://openai.com",
                "https://openai.com/",
                "id-1",
                EntityType.ORGANIZATION,
                "OpenAI"
        );

        String query = queryBuilder.buildDiscoveryQuery(target);
        assertThat(query).isEqualTo("OpenAI company official");
    }

    @Test
    @DisplayName("Should build targeted query for repository with display name")
    void shouldBuildRepositoryQuery() {
        ResearchTarget target = new ResearchTarget(
                "https://github.com/spring-projects/spring-boot",
                "https://github.com/spring-projects/spring-boot",
                "id-2",
                EntityType.REPOSITORY,
                "Spring Boot"
        );

        String query = queryBuilder.buildDiscoveryQuery(target);
        assertThat(query).isEqualTo("Spring Boot repository source code");
    }

    @Test
    @DisplayName("Should build targeted query for person with display name")
    void shouldBuildPersonQuery() {
        ResearchTarget target = new ResearchTarget(
                "https://example.com/jane-doe",
                "https://example.com/jane-doe",
                "id-3",
                EntityType.PERSON,
                "Jane Doe"
        );

        String query = queryBuilder.buildDiscoveryQuery(target);
        assertThat(query).isEqualTo("Jane Doe profile biography");
    }

    @Test
    @DisplayName("Should handle null entityType gracefully without NullPointerException")
    void shouldHandleNullEntityTypeGracefully() {
        ResearchTarget target = new ResearchTarget(
                "https://example.com/something",
                "https://example.com/something",
                "id-4",
                null,
                "Something"
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
                "id-5",
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
}
