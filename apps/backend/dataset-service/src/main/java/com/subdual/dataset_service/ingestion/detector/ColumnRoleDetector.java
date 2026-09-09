package com.subdual.dataset_service.ingestion.detector;

import com.subdual.dataset_service.ingestion.model.ColumnRole;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Intelligent column role detector.
 * Analyzes both header names and sample cell values to infer semantic column roles.
 */
@Component
public class ColumnRoleDetector {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^(https?://|www\\.)\\S+$", Pattern.CASE_INSENSITIVE);

    public ColumnRole detectRole(String headerName, List<String> sampleValues) {
        if (headerName == null) return ColumnRole.UNKNOWN;
        String cleanHeader = headerName.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");

        // 1. Specific specialized roles by header
        if (cleanHeader.contains("linkedin")) {
            return ColumnRole.LINKEDIN_URL;
        }
        if (cleanHeader.contains("repo") || cleanHeader.contains("github")) {
            return ColumnRole.REPOSITORY_URL;
        }
        if (cleanHeader.contains("email") || cleanHeader.contains("mail")) {
            return ColumnRole.EMAIL;
        }
        if (cleanHeader.contains("company") || cleanHeader.contains("organization") || cleanHeader.contains("employer")
                || cleanHeader.contains("org") || cleanHeader.contains("firm")) {
            return ColumnRole.COMPANY;
        }
        if (cleanHeader.contains("jobtitle") || cleanHeader.contains("role") || cleanHeader.contains("position")
                || cleanHeader.contains("headline") || cleanHeader.contains("occupation")) {
            return ColumnRole.ROLE;
        }
        if (cleanHeader.contains("location") || cleanHeader.contains("city") || cleanHeader.contains("country")
                || cleanHeader.contains("state") || cleanHeader.contains("address")) {
            return ColumnRole.LOCATION;
        }
        if (cleanHeader.contains("education") || cleanHeader.contains("university") || cleanHeader.contains("college")
                || cleanHeader.contains("degree") || cleanHeader.contains("school")) {
            return ColumnRole.EDUCATION;
        }
        if (cleanHeader.contains("skill") || cleanHeader.contains("techstack") || cleanHeader.contains("technolog")) {
            return ColumnRole.SKILLS;
        }
        if (cleanHeader.contains("firstname") || cleanHeader.equals("first") || cleanHeader.contains("givenname") || cleanHeader.contains("forename")) {
            return ColumnRole.FIRST_NAME;
        }
        if (cleanHeader.contains("lastname") || cleanHeader.equals("last") || cleanHeader.contains("surname") || cleanHeader.contains("familyname")) {
            return ColumnRole.LAST_NAME;
        }
        if (cleanHeader.equals("fullname") || cleanHeader.equals("personname") || cleanHeader.equals("entityname")) {
            return ColumnRole.FULL_NAME;
        }
        if (cleanHeader.equals("name") || cleanHeader.equals("person") || cleanHeader.equals("author") || cleanHeader.equals("founder")) {
            return ColumnRole.NAME;
        }
        if (cleanHeader.contains("url") || cleanHeader.contains("website") || cleanHeader.contains("link")
                || cleanHeader.contains("domain") || cleanHeader.contains("webpage") || cleanHeader.contains("homepage")) {
            return ColumnRole.URL;
        }

        // 2. Inspect sample values if header was ambiguous
        if (sampleValues != null && !sampleValues.isEmpty()) {
            int urlMatches = 0;
            int linkedinMatches = 0;
            int emailMatches = 0;
            int totalSamples = 0;

            for (String val : sampleValues) {
                if (val == null || val.isBlank()) continue;
                totalSamples++;
                String v = val.trim();
                if (v.toLowerCase(Locale.ROOT).contains("linkedin.com/")) {
                    linkedinMatches++;
                }
                if (URL_PATTERN.matcher(v).matches()) {
                    urlMatches++;
                }
                if (EMAIL_PATTERN.matcher(v).matches()) {
                    emailMatches++;
                }
            }

            if (totalSamples > 0) {
                if ((double) linkedinMatches / totalSamples >= 0.5) return ColumnRole.LINKEDIN_URL;
                if ((double) emailMatches / totalSamples >= 0.5) return ColumnRole.EMAIL;
                if ((double) urlMatches / totalSamples >= 0.5) return ColumnRole.URL;
            }
        }

        return ColumnRole.UNKNOWN;
    }
}
