package com.subdual.dataset_service.dataset.service.helper;

import com.subdual.dataset_service.dataset.api.dto.response.DatasetProfileReport;
import com.subdual.dataset_service.dataset.model.ColumnProfile;
import com.subdual.dataset_service.dataset.model.ColumnRole;
import com.subdual.dataset_service.dataset.model.RawDataset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Dataset profiling engine.
 * Calculates column completeness, detects entity anchors, finds conflicting rows,
 * produces explainable quality scores, and maps recommended columns.
 */
@Component
@RequiredArgsConstructor
public class DatasetProfiler {

    private final ColumnRoleDetector columnRoleDetector;

    public DatasetProfileReport profile(RawDataset rawDataset) {
        List<String> headers = rawDataset.headers();
        List<Map<String, String>> rows = rawDataset.rows();
        int totalRows = rows.size();

        List<ColumnProfile> columnProfiles = new ArrayList<>();
        List<String> detectedEntityCols = new ArrayList<>();
        List<String> detectedUrlCols = new ArrayList<>();
        List<String> detectedOrgCols = new ArrayList<>();
        List<String> existingEnrichedCols = new ArrayList<>();
        Map<String, String> recommendedMapping = new LinkedHashMap<>();

        // 1. Profile each column
        for (String col : headers) {
            int populatedCount = 0;
            List<String> samples = new ArrayList<>();

            for (Map<String, String> row : rows) {
                String val = row.get(col);
                if (val != null && !val.isBlank()) {
                    populatedCount++;
                    if (samples.size() < 5) {
                        samples.add(val.trim());
                    }
                }
            }

            double completeness = totalRows > 0
                    ? Math.round(((double) populatedCount / totalRows) * 1000.0) / 10.0
                    : 0.0;

            ColumnRole role = columnRoleDetector.detectRole(col, samples);
            columnProfiles.add(new ColumnProfile(col, role, completeness, populatedCount, totalRows, samples));

            switch (role) {
                case NAME -> {
                    detectedEntityCols.add(col);
                    recommendedMapping.putIfAbsent("nameColumn", col);
                }
                case FULL_NAME -> {
                    detectedEntityCols.add(col);
                    recommendedMapping.putIfAbsent("fullNameColumn", col);
                    recommendedMapping.putIfAbsent("nameColumn", col);
                }
                case FIRST_NAME -> {
                    detectedEntityCols.add(col);
                    recommendedMapping.putIfAbsent("firstNameColumn", col);
                }
                case LAST_NAME -> {
                    detectedEntityCols.add(col);
                    recommendedMapping.putIfAbsent("lastNameColumn", col);
                }
                case URL, LINKEDIN_URL -> {
                    detectedUrlCols.add(col);
                    recommendedMapping.putIfAbsent("urlColumn", col);
                }
                case COMPANY -> {
                    detectedOrgCols.add(col);
                    recommendedMapping.putIfAbsent("organizationColumn", col);
                }
                case ROLE -> {
                    existingEnrichedCols.add(col);
                    recommendedMapping.putIfAbsent("roleColumn", col);
                }
                case EMAIL -> {
                    recommendedMapping.putIfAbsent("emailColumn", col);
                }
                case LOCATION -> {
                    existingEnrichedCols.add(col);
                    recommendedMapping.putIfAbsent("locationColumn", col);
                }
                case REPOSITORY_URL -> {
                    detectedUrlCols.add(col);
                    recommendedMapping.putIfAbsent("urlColumn", col);
                }
                case EDUCATION, SKILLS -> existingEnrichedCols.add(col);
                default -> {}
            }
        }

        // 2. Detect lightweight field conflicts across rows with identical name
        List<String> conflicts = detectLightweightConflicts(rows, recommendedMapping);

        // 3. Calculate explainable quality score (0 to 100)
        QualityAssessment quality = calculateQualityScore(totalRows, columnProfiles, recommendedMapping, rawDataset.malformedRows().size(), rawDataset.duplicateRowIndices().size(), conflicts.size());

        // 4. Extract top 10 preview rows
        List<Map<String, String>> preview = rows.subList(0, Math.min(10, rows.size()));

        return new DatasetProfileReport(
                rawDataset.fileName(),
                rawDataset.totalRows(),
                rawDataset.validRowCount(),
                rawDataset.malformedRows().size(),
                rawDataset.duplicateRowIndices().size(),
                columnProfiles,
                detectedEntityCols,
                detectedUrlCols,
                detectedOrgCols,
                existingEnrichedCols,
                conflicts,
                quality.score(),
                quality.explanation(),
                recommendedMapping,
                preview
        );
    }

    private List<String> detectLightweightConflicts(List<Map<String, String>> rows, Map<String, String> mapping) {
        if (mapping == null) return List.of();
        String nameCol = mapping.get("nameColumn");
        String fullCol = mapping.get("fullNameColumn");
        String firstCol = mapping.get("firstNameColumn");
        String lastCol = mapping.get("lastNameColumn");
        String orgCol = mapping.get("organizationColumn");

        if (orgCol == null) return List.of();
        if (nameCol == null && fullCol == null && (firstCol == null && lastCol == null)) return List.of();

        List<String> conflicts = new ArrayList<>();
        Map<String, Set<String>> nameToOrgs = new HashMap<>();
        Map<String, String> normToOriginal = new HashMap<>();

        for (Map<String, String> row : rows) {
            String name = null;
            if (fullCol != null && row.get(fullCol) != null && !row.get(fullCol).isBlank()) {
                name = row.get(fullCol).trim();
            } else if (firstCol != null && lastCol != null && row.get(firstCol) != null && row.get(lastCol) != null) {
                name = (row.get(firstCol).trim() + " " + row.get(lastCol).trim()).trim();
            } else if (nameCol != null && row.get(nameCol) != null && !row.get(nameCol).isBlank()) {
                name = row.get(nameCol).trim();
            }

            String org = row.get(orgCol);
            if (name != null && !name.isBlank() && org != null && !org.isBlank()) {
                String normName = name.trim().toLowerCase(Locale.ROOT);
                nameToOrgs.computeIfAbsent(normName, k -> new HashSet<>()).add(org.trim());
                normToOriginal.putIfAbsent(normName, name.trim());
            }
        }

        for (Map.Entry<String, Set<String>> entry : nameToOrgs.entrySet()) {
            if (entry.getValue().size() > 1) {
                String displayName = normToOriginal.getOrDefault(entry.getKey(), entry.getKey());
                conflicts.add("Entity '" + displayName + "' associated with multiple organizations: " + entry.getValue());
                if (conflicts.size() >= 5) break; // Cap to top 5
            }
        }

        return conflicts;
    }

    private record QualityAssessment(double score, String explanation) {}

    private QualityAssessment calculateQualityScore(
            int totalRows,
            List<ColumnProfile> columns,
            Map<String, String> mapping,
            int malformedCount,
            int duplicateCount,
            int conflictCount
    ) {
        if (totalRows == 0) {
            return new QualityAssessment(0.0, "Dataset is empty.");
        }

        double score = 0.0;
        List<String> factors = new ArrayList<>();

        // Factor 1: Identifier Presence (Up to 50 pts)
        boolean hasName = mapping.containsKey("nameColumn") || mapping.containsKey("fullNameColumn")
                || (mapping.containsKey("firstNameColumn") && mapping.containsKey("lastNameColumn"));
        boolean hasUrl = mapping.containsKey("urlColumn");
        if (hasName && hasUrl) {
            score += 50.0;
            factors.add("Both Name and URL identifier columns detected (+50)");
        } else if (hasName || hasUrl) {
            score += 35.0;
            factors.add("Identity anchor columns detected (+35)");
        } else {
            factors.add("No primary identifier detected (0)");
        }

        // Factor 2: Average Column Completeness (Up to 30 pts)
        double avgCompleteness = columns.stream()
                .mapToDouble(ColumnProfile::completenessPercentage)
                .average()
                .orElse(0.0);
        double completenessPts = (avgCompleteness / 100.0) * 30.0;
        score += completenessPts;
        factors.add(String.format(Locale.US, "Average column completeness of %.1f%% (+%.1f)", avgCompleteness, completenessPts));

        // Factor 3: Cleanliness & Absence of Malformed/Duplicates/Conflicts (Up to 20 pts)
        double penalty = 0.0;
        if (malformedCount > 0) {
            penalty += Math.min(10.0, (malformedCount / (double) totalRows) * 20.0);
        }
        if (duplicateCount > 0) {
            penalty += Math.min(5.0, (duplicateCount / (double) totalRows) * 10.0);
        }
        if (conflictCount > 0) {
            penalty += 5.0;
        }
        double cleanlinessPts = Math.max(0.0, 20.0 - penalty);
        score += cleanlinessPts;
        factors.add(String.format(Locale.US, "Cleanliness & structural integrity (+%.1f)", cleanlinessPts));

        double finalScore = Math.min(100.0, Math.max(0.0, Math.round(score * 10.0) / 10.0));
        String explanation = "Quality Score: " + finalScore + "/100. " + String.join("; ", factors);

        return new QualityAssessment(finalScore, explanation);
    }
}
