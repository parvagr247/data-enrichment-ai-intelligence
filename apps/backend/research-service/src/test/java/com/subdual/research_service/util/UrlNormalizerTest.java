package com.subdual.research_service.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlNormalizerTest {

    @Test
    @DisplayName("Should normalize protocol from http to https and prepend https if missing")
    void shouldNormalizeProtocol() {
        assertThat(UrlNormalizer.normalize("http://example.com/test"))
                .isEqualTo("https://example.com/test");
        assertThat(UrlNormalizer.normalize("linkedin.com/in/user"))
                .isEqualTo("https://linkedin.com/in/user");
    }

    @Test
    @DisplayName("Should strip www prefix")
    void shouldStripWww() {
        assertThat(UrlNormalizer.normalize("https://www.github.com/spring-projects"))
                .isEqualTo("https://github.com/spring-projects");
    }

    @Test
    @DisplayName("Should normalize international LinkedIn country subdomains to linkedin.com")
    void shouldNormalizeInternationalLinkedInSubdomains() {
        assertThat(UrlNormalizer.normalize("https://in.linkedin.com/in/johndoe/"))
                .isEqualTo("https://linkedin.com/in/johndoe");
        assertThat(UrlNormalizer.normalize("https://uk.linkedin.com/company/acme-corp/"))
                .isEqualTo("https://linkedin.com/company/acme-corp");
        assertThat(UrlNormalizer.normalize("https://www.in.linkedin.com/in/johndoe"))
                .isEqualTo("https://linkedin.com/in/johndoe");
    }

    @Test
    @DisplayName("Should strip trailing slash for subpaths but preserve root slash")
    void shouldNormalizeTrailingSlash() {
        assertThat(UrlNormalizer.normalize("https://example.com/in/user/"))
                .isEqualTo("https://example.com/in/user");
        assertThat(UrlNormalizer.normalize("https://example.com/"))
                .isEqualTo("https://example.com/");
    }

    @Test
    @DisplayName("Should strip tracking query parameters but preserve legitimate params")
    void shouldStripTrackingParameters() {
        String url = "https://example.com/profile?tab=repos&utm_source=twitter&utm_medium=cpc&ref_src=twsrc&fbclid=123";
        assertThat(UrlNormalizer.normalize(url))
                .isEqualTo("https://example.com/profile?tab=repos");
    }

    @Test
    @DisplayName("Should handle URL spaces and remove fragments")
    void shouldHandleSpacesAndFragments() {
        assertThat(UrlNormalizer.normalize("https://example.com/search query#section2"))
                .isEqualTo("https://example.com/search%20query");
    }

    @Test
    @DisplayName("Should accurately compare equivalent URLs across protocol, subdomains, and trailing slashes")
    void shouldMatchSameUrlAcrossVariants() {
        String u1 = "http://in.linkedin.com/in/jane-doe/?utm_source=newsletter";
        String u2 = "https://www.linkedin.com/in/jane-doe";
        String u3 = "https://linkedin.com/in/jane-doe/";

        assertThat(UrlNormalizer.isSameUrl(u1, u2)).isTrue();
        assertThat(UrlNormalizer.isSameUrl(u2, u3)).isTrue();
        assertThat(UrlNormalizer.isSameUrl(u1, u3)).isTrue();
        assertThat(UrlNormalizer.isSameUrl("https://example.com/user1", "https://example.com/user2")).isFalse();
    }
}
