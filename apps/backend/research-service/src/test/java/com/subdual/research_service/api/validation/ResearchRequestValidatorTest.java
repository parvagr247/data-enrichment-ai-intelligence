package com.subdual.research_service.api.validation;

import com.subdual.research_service.api.dto.request.ResearchRequest;
import com.subdual.research_service.common.exception.BusinessRuleException;
import com.subdual.research_service.research.model.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResearchRequestValidatorTest {

    private ResearchRequestValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ResearchRequestValidator();
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when request is null")
    void shouldThrowWhenRequestIsNull() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Request body must not be null");
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when both url and name are null")
    void shouldThrowWhenBothUrlAndNameAreNull() {
        ResearchRequest request = new ResearchRequest(null, EntityType.ORGANIZATION, null, null);
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Either 'url' or 'name' must be provided for research");
    }

    @Test
    @DisplayName("Should throw BusinessRuleException when url is blank")
    void shouldThrowWhenUrlIsBlank() {
        ResearchRequest request = new ResearchRequest("   ", EntityType.ORGANIZATION, "Acme Corp");
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Field 'url' must be a valid, well-formed HTTP/HTTPS URL");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/file",
            "file:///etc/passwd",
            "not-a-url",
            "http://",
            "://missing-scheme"
    })
    @DisplayName("Should reject non-HTTP/HTTPS or malformed URLs")
    void shouldRejectInvalidUrls(String invalidUrl) {
        ResearchRequest request = new ResearchRequest(invalidUrl, EntityType.ORGANIZATION, "Acme");
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Field 'url' must be a valid, well-formed HTTP/HTTPS URL");
    }

    @Test
    @DisplayName("Should accept valid HTTP URL without name")
    void shouldAcceptValidHttpUrl() {
        ResearchRequest request = new ResearchRequest("http://example.com/company", EntityType.ORGANIZATION, null);
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept valid HTTPS URL with name and metadata")
    void shouldAcceptValidHttpsUrl() {
        ResearchRequest request = new ResearchRequest("https://github.com/spring-projects/spring-boot", EntityType.REPOSITORY, "Spring Boot");
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept discovery-first request when url is omitted but name is present")
    void shouldAcceptDiscoveryFirstWhenUrlIsOmitted() {
        ResearchRequest request = new ResearchRequest(null, EntityType.PERSON, "Jane Doe");
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should accept markdown bracketed URL and unwrap it properly")
    void shouldAcceptMarkdownBracketedUrl() {
        ResearchRequest request = new ResearchRequest(
                "[https://www.linkedin.com/in/krati-mittal](https://www.linkedin.com/in/krati-mittal)",
                EntityType.PERSON,
                "Krati Mittal"
        );
        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThat(request.url())
                .isEqualTo("https://www.linkedin.com/in/krati-mittal");
    }
}
