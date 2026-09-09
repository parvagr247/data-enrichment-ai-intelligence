package com.subdual.ai_intelligent_service.extraction.service.helper;

import com.subdual.ai_intelligent_service.extraction.api.dto.request.ExtractionRequest;
import com.subdual.ai_intelligent_service.extraction.model.ExtractedFact;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic rule-based extraction helper utilizing regex heuristics.
 */
@Component
public class DeterministicExtractionHelper {

    private static final Pattern ROLE_PATTERN = Pattern.compile(
            "(?i)\\b(?:is|as)\\s+(?:a|an)?\\s+([A-Z][a-zA-Z\\s]{2,40}\\b(?:Engineer|Architect|Director|Developer|Manager|Scientist|Lead|Founder|CEO|CTO))"
    );
    private static final Pattern ORG_PATTERN = Pattern.compile(
            "(?i)\\b(?:at|for)\\s+([A-Z][a-zA-Z0-9&\\s]{2,30})\\b"
    );
    private static final Pattern LICENSE_PATTERN = Pattern.compile(
            "(?i)\\b(Apache[-\\s]?2\\.0|MIT|GPL|BSD|Mozilla Public License)\\b"
    );
    private static final Pattern EDUCATION_PATTERN = Pattern.compile(
            "(?i)\\b(?:graduated from|degree from|studied at)\\s+([A-Z][a-zA-Z0-9&\\s]{2,35})\\b"
    );
    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "(?i)\\b(?:based in|located in|headquartered in)\\s+([A-Z][a-zA-Z0-9,\\s]{2,35})\\b"
    );
    private static final Pattern SKILLS_PATTERN = Pattern.compile(
            "(?i)\\b(?:skills:|expertise in|proficient in)\\s+([a-zA-Z0-9,\\s/+-]{2,50})\\b"
    );

    public Map<String, ExtractedFact> extractDeterministically(ExtractionRequest request) {
        Map<String, ExtractedFact> facts = new LinkedHashMap<>();
        String text = request != null ? request.textContent() : null;
        if (text == null || text.isBlank()) {
            return facts;
        }

        String lowerName = request.entityName() != null ? request.entityName().trim().toLowerCase(Locale.ROOT) : "";
        String[] sentences = text.split("(?<=[.!?])\\s+");

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.length() < 10) {
                continue;
            }
            extractSentenceFacts(trimmed, lowerName, facts);
        }

        return facts;
    }

    private void extractSentenceFacts(String sentence, String lowerName, Map<String, ExtractedFact> facts) {
        String lowerSentence = sentence.toLowerCase(Locale.ROOT);

        tryExtractDescription(sentence, lowerSentence, lowerName, facts);
        tryExtractRole(sentence, lowerSentence, lowerName, facts);
        tryExtractOrganization(sentence, lowerSentence, lowerName, facts);
        tryExtractEducation(sentence, lowerSentence, lowerName, facts);
        tryExtractLocation(sentence, lowerSentence, lowerName, facts);
        tryExtractSkills(sentence, lowerSentence, facts);
        tryExtractLicense(sentence, lowerSentence, facts);
    }

    private void tryExtractDescription(String sentence, String lowerSentence, String lowerName, Map<String, ExtractedFact> facts) {
        if (facts.containsKey("description") || !lowerSentence.contains(lowerName)) {
            return;
        }
        if (lowerSentence.contains("is a") || lowerSentence.contains("is an")
                || lowerSentence.contains("makes it easy") || lowerSentence.contains("provides")
                || lowerSentence.contains("platform") || lowerSentence.contains("helps you")) {
            facts.put("description", new ExtractedFact(sentence, sentence, 0.92));
        }
    }

    private void tryExtractRole(String sentence, String lowerSentence, String lowerName, Map<String, ExtractedFact> facts) {
        if (facts.containsKey("currentRole") || !lowerSentence.contains(lowerName)) {
            return;
        }
        Matcher m = ROLE_PATTERN.matcher(sentence);
        if (m.find()) {
            String role = m.group(1).trim();
            facts.put("currentRole", new ExtractedFact(role, sentence, 0.90));
        }
    }

    private void tryExtractOrganization(String sentence, String lowerSentence, String lowerName, Map<String, ExtractedFact> facts) {
        if (facts.containsKey("organization") || !lowerSentence.contains(lowerName)) {
            return;
        }
        Matcher m = ORG_PATTERN.matcher(sentence);
        if (m.find()) {
            String org = m.group(1).trim();
            facts.put("organization", new ExtractedFact(org, sentence, 0.85));
        }
    }

    private void tryExtractEducation(String sentence, String lowerSentence, String lowerName, Map<String, ExtractedFact> facts) {
        if (facts.containsKey("education")) {
            return;
        }
        Matcher m = EDUCATION_PATTERN.matcher(sentence);
        if (m.find()) {
            String edu = m.group(1).trim();
            facts.put("education", new ExtractedFact(edu, sentence, 0.88));
        }
    }

    private void tryExtractLocation(String sentence, String lowerSentence, String lowerName, Map<String, ExtractedFact> facts) {
        if (facts.containsKey("location")) {
            return;
        }
        Matcher m = LOCATION_PATTERN.matcher(sentence);
        if (m.find()) {
            String loc = m.group(1).trim();
            facts.put("location", new ExtractedFact(loc, sentence, 0.85));
        }
    }

    private void tryExtractSkills(String sentence, String lowerSentence, Map<String, ExtractedFact> facts) {
        if (facts.containsKey("skills")) {
            return;
        }
        Matcher m = SKILLS_PATTERN.matcher(sentence);
        if (m.find()) {
            String skills = m.group(1).trim();
            facts.put("skills", new ExtractedFact(skills, sentence, 0.85));
        }
    }

    private void tryExtractLicense(String sentence, String lowerSentence, Map<String, ExtractedFact> facts) {
        if (facts.containsKey("license") || !lowerSentence.contains("license")) {
            return;
        }
        Matcher m = LICENSE_PATTERN.matcher(sentence);
        if (m.find()) {
            facts.put("license", new ExtractedFact(m.group(1), sentence, 0.95));
        }
    }
}
