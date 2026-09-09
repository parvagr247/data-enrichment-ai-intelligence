package com.subdual.dataset_service.enrichment.service.helper;

import com.subdual.dataset_service.enrichment.api.dto.response.RowEnrichmentResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RowIdentityResolver {

    public RowEnrichmentResult buildInitialRowResult(
            String jobId,
            int rowIndex,
            Map<String, String> row,
            Map<String, String> mapping,
            String defaultEntityType
    ) {
        String firstName = extractMappedValue(row, mapping, "firstNameColumn");
        String lastName = extractMappedValue(row, mapping, "lastNameColumn");
        String fullName = extractMappedValue(row, mapping, "fullNameColumn");
        String name = extractMappedValue(row, mapping, "nameColumn");
        String compositeName = buildCompositeName(firstName, lastName, fullName, name);
        String url = extractMappedValue(row, mapping, "urlColumn");
        String displayName = (compositeName != null && !compositeName.isBlank()) ? compositeName : (url != null && !url.isBlank() ? url : "Row " + (rowIndex + 1));

        return new RowEnrichmentResult(
                jobId + "-row-" + rowIndex,
                rowIndex,
                row,
                "QUEUED",
                displayName,
                url != null ? url : "",
                defaultEntityType != null ? defaultEntityType : "PERSON",
                Map.of(),
                List.of(),
                List.of(),
                0.0,
                List.of(),
                null,
                "QUEUED",
                null,
                "Queued for execution",
                null,
                null
        );
    }

    public String extractMappedValue(Map<String, String> row, Map<String, String> mapping, String columnKey) {
        if (row == null || mapping == null) return null;
        String mappedCol = mapping.get(columnKey);
        if (mappedCol != null && row.containsKey(mappedCol)) {
            String val = row.get(mappedCol);
            if (val != null && !val.isBlank()) {
                val = val.trim();
                if ("urlColumn".equals(columnKey)) {
                    val = unwrapLink(val);
                }
                return (val != null && !val.isBlank()) ? val : null;
            }
        }
        return null;
    }

    public String unwrapLink(String url) {
        if (url == null || url.isBlank()) return url;
        String s = url.trim();
        if (s.startsWith("[") && s.contains("](") && s.endsWith(")")) {
            int openParen = s.indexOf("](");
            s = s.substring(openParen + 2, s.length() - 1).trim();
        } else if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1).trim();
        } else if (s.startsWith("<") && s.endsWith(">")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    public static String buildCompositeName(String firstName, String lastName, String fullName, String legacyName) {
        String cleanFull = sanitizeNameToken(fullName);
        if (cleanFull != null && !cleanFull.isBlank()) {
            return cleanFull;
        }

        String cleanFirst = sanitizeNameToken(firstName);
        String cleanLast = sanitizeNameToken(lastName);

        if (cleanFirst != null && !cleanFirst.isBlank() && cleanLast != null && !cleanLast.isBlank()) {
            return cleanFirst + " " + cleanLast;
        }
        if (cleanFirst != null && !cleanFirst.isBlank()) {
            return cleanFirst;
        }
        if (cleanLast != null && !cleanLast.isBlank()) {
            return cleanLast;
        }

        String cleanLegacy = sanitizeNameToken(legacyName);
        if (cleanLegacy != null && !cleanLegacy.isBlank()) {
            return cleanLegacy;
        }

        return null;
    }

    public static String sanitizeNameToken(String token) {
        if (token == null) return null;
        String s = token.trim();
        if (s.equalsIgnoreCase("null") || s.equalsIgnoreCase("undefined")) {
            return null;
        }
        s = s.replaceAll("\\s+", " ").trim();
        return s.isBlank() ? null : s;
    }
}
