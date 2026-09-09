package com.subdual.dataset_service.ingestion;

import com.subdual.dataset_service.dataset.api.dto.response.DatasetProfileReport;
import com.subdual.dataset_service.dataset.service.helper.ColumnRoleDetector;
import com.subdual.dataset_service.dataset.service.helper.CsvDatasetParser;
import com.subdual.dataset_service.dataset.service.helper.DatasetProfiler;
import com.subdual.dataset_service.dataset.service.helper.DatasetValidator;
import com.subdual.dataset_service.dataset.service.helper.ExcelDatasetParser;
import com.subdual.dataset_service.dataset.service.impl.DefaultDatasetIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DatasetIngestionServiceTest {

    private DefaultDatasetIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        CsvDatasetParser csvParser = new CsvDatasetParser();
        ExcelDatasetParser excelParser = new ExcelDatasetParser();
        DatasetValidator validator = new DatasetValidator();
        ColumnRoleDetector detector = new ColumnRoleDetector();
        DatasetProfiler profiler = new DatasetProfiler(detector);

        ingestionService = new DefaultDatasetIngestionService(csvParser, excelParser, validator, profiler);
    }

    @Test
    @DisplayName("Should successfully ingest and profile standard CSV dataset")
    void shouldIngestAndProfileStandardCsv() {
        String csv = """
                name,linkedin_url,company,role
                Alice Smith,https://www.linkedin.com/in/alicesmith,Acme Corp,Engineering Lead
                Bob Jones,https://www.linkedin.com/in/bobjones,Beta Inc,Product Manager
                Charlie Brown,,Acme Corp,Staff Designer
                """;

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        DatasetProfileReport report = ingestionService.ingestAndProfile(in, "team.csv");

        assertNotNull(report);
        assertEquals("team.csv", report.datasetName());
        assertEquals(3, report.totalRows());
        assertEquals(3, report.validRows());
        assertEquals(0, report.malformedRowCount());
        assertEquals(0, report.duplicateRowCount());

        // Check column roles and completeness
        assertTrue(report.detectedEntityColumns().contains("name"));
        assertTrue(report.detectedUrlColumns().contains("linkedin_url"));
        assertTrue(report.detectedOrgColumns().contains("company"));
        assertTrue(report.existingEnrichedColumns().contains("role"));

        assertEquals("name", report.recommendedMapping().get("nameColumn"));
        assertEquals("linkedin_url", report.recommendedMapping().get("urlColumn"));
        assertEquals("company", report.recommendedMapping().get("organizationColumn"));
        assertEquals("role", report.recommendedMapping().get("roleColumn"));

        assertTrue(report.qualityScore() > 70.0, "Quality score should be high for clean dataset");
    }

    @Test
    @DisplayName("Should isolate malformed rows without failing the entire dataset")
    void shouldIsolateMalformedRows() {
        String csv = """
                name,url,company
                Alice,https://example.com/alice,Acme
                Broken Row Missing Columns
                Bob,https://example.com/bob,Beta
                """;

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        DatasetProfileReport report = ingestionService.ingestAndProfile(in, "broken.csv");

        assertNotNull(report);
        assertEquals(3, report.totalRows());
        assertEquals(2, report.validRows());
        assertEquals(1, report.malformedRowCount());
        assertEquals(2, report.previewRows().size());
    }

    @Test
    @DisplayName("Should detect duplicate rows deterministically")
    void shouldDetectDuplicateRows() {
        String csv = """
                name,company
                Alice,Acme
                Bob,Beta
                Alice,Acme
                """;

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        DatasetProfileReport report = ingestionService.ingestAndProfile(in, "duplicates.csv");

        assertEquals(3, report.totalRows());
        assertEquals(3, report.validRows());
        assertEquals(1, report.duplicateRowCount());
    }

    @Test
    @DisplayName("Should reject empty datasets with clean validation error")
    void shouldRejectEmptyDataset() {
        String emptyCsv = "";
        ByteArrayInputStream in = new ByteArrayInputStream(emptyCsv.getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                ingestionService.ingestAndProfile(in, "empty.csv"));
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("Should reject unsupported file formats")
    void shouldRejectUnsupportedFormat() {
        String data = "some,data";
        ByteArrayInputStream in = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                ingestionService.ingestAndProfile(in, "malicious.exe"));
        assertTrue(ex.getMessage().contains("Unsupported dataset file format"));
    }

    @Test
    @DisplayName("Should detect lightweight entity conflicts across rows")
    void shouldDetectConflicts() {
        String csv = """
                name,company
                Alice Smith,Acme Corp
                Alice Smith,Different Global Ltd
                """;

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        DatasetProfileReport report = ingestionService.ingestAndProfile(in, "conflicts.csv");

        assertFalse(report.conflictingFields().isEmpty());
        assertTrue(report.conflictingFields().get(0).contains("Alice Smith"));
    }

    @Test
    @DisplayName("Should profile raw rows directly from JSON payload")
    void shouldProfileRawRows() {
        List<Map<String, String>> rows = List.of(
                Map.of("FullName", "Linus Torvalds", "Repo", "https://github.com/torvalds/linux"),
                Map.of("FullName", "Guido van Rossum", "Repo", "https://github.com/python/cpython")
        );

        DatasetProfileReport report = ingestionService.profileRawRows("repositories.json", rows);
        assertNotNull(report);
        assertEquals(2, report.totalRows());
        assertTrue(report.detectedEntityColumns().contains("FullName"));
        assertTrue(report.detectedUrlColumns().contains("Repo"));
    }

    @Test
    @DisplayName("Should detect composite name roles and recommend composite mapping")
    void shouldDetectCompositeNameRolesAndRecommendCompositeMapping() {
        String csv = """
                First Name,Last Name,URL Link,Company Name,Position
                Vardhan,Bhati,https://www.linkedin.com/in/vardhan-bhati-33b537326,Entrepreneurship Development Cell MNIT Jaipur,Lead
                Krati,Mittal,https://www.linkedin.com/in/krati-mittal,JRNI,Lead Engineer
                """;

        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
        DatasetProfileReport report = ingestionService.ingestAndProfile(in, "composite.csv");

        assertNotNull(report);
        assertEquals("First Name", report.recommendedMapping().get("firstNameColumn"));
        assertEquals("Last Name", report.recommendedMapping().get("lastNameColumn"));
        assertEquals("URL Link", report.recommendedMapping().get("urlColumn"));
        assertEquals("Company Name", report.recommendedMapping().get("organizationColumn"));
        assertEquals("Position", report.recommendedMapping().get("roleColumn"));
    }
}
