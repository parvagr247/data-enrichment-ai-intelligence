package com.subdual.research_service.extraction.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationNormalizerTest {

    @Test
    @DisplayName("Should strip corporate suffixes like Inc, LLC, Ltd, Corp, GmbH")
    void shouldStripCorporateSuffixes() {
        assertThat(OrganizationNormalizer.normalize("Acme, Inc.")).isEqualTo("Acme");
        assertThat(OrganizationNormalizer.normalize("Google LLC")).isEqualTo("Google");
        assertThat(OrganizationNormalizer.normalize("Siemens AG")).isEqualTo("Siemens");
        assertThat(OrganizationNormalizer.normalize("SAP SE GmbH")).isEqualTo("SAP SE");
        assertThat(OrganizationNormalizer.normalize("Tata Consultancy Services Pvt. Ltd.")).isEqualTo("Tata Consultancy Services");
    }

    @Test
    @DisplayName("Should generate clean comparison key for organizations")
    void shouldGenerateComparisonKey() {
        assertThat(OrganizationNormalizer.toComparisonKey("Acme, Inc.")).isEqualTo("acme");
        assertThat(OrganizationNormalizer.toComparisonKey("Google, L.L.C.")).isEqualTo("google");
    }

    @Test
    @DisplayName("Should handle null and blank gracefully")
    void shouldHandleNullAndBlank() {
        assertThat(OrganizationNormalizer.normalize(null)).isEmpty();
        assertThat(OrganizationNormalizer.normalize("   ")).isEmpty();
        assertThat(OrganizationNormalizer.toComparisonKey(null)).isEmpty();
    }
}
