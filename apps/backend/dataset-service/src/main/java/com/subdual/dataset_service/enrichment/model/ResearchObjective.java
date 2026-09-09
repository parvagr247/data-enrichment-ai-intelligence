package com.subdual.dataset_service.enrichment.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record ResearchObjective(
        String rawObjective,
        List<String> targetPersonCharacteristics,
        List<String> desiredRelationships,
        List<String> relevantTopics,
        List<String> constraints,
        List<String> outputPreferences,
        boolean isBlank
) {
    public ResearchObjective {
        if (targetPersonCharacteristics == null) targetPersonCharacteristics = List.of();
        if (desiredRelationships == null) desiredRelationships = List.of();
        if (relevantTopics == null) relevantTopics = List.of();
        if (constraints == null) constraints = List.of();
        if (outputPreferences == null) outputPreferences = List.of();
    }

    public static ResearchObjective blank() {
        return new ResearchObjective("", List.of(), List.of(), List.of(), List.of(), List.of(), true);
    }

    public static ResearchObjective from(String raw) {
        if (raw == null || raw.trim().isBlank()) {
            return blank();
        }

        String text = raw.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        List<String> characteristics = new ArrayList<>();
        if (lower.contains("recruiter") || lower.contains("talent acquisition") || lower.contains("sourcer") || lower.contains("staffing")) {
            characteristics.add("Recruiters / Talent Acquisition");
        }
        if (lower.contains("hiring manager") || lower.contains("engineering manager") || lower.contains("em")) {
            characteristics.add("Hiring / Engineering Managers");
        }
        if (lower.contains("engineer") || lower.contains("developer") || lower.contains("architect") || lower.contains("tech lead")) {
            characteristics.add("Engineers / Technical Leads");
        }
        if (lower.contains("founder") || lower.contains("ceo") || lower.contains("cto") || lower.contains("executive") || lower.contains("leader")) {
            characteristics.add("Founders / Engineering Leadership");
        }
        if (lower.contains("researcher") || lower.contains("scientist") || lower.contains("phd")) {
            characteristics.add("Researchers / Academics");
        }

        List<String> relationships = new ArrayList<>();
        if (lower.contains("internship") || lower.contains("intern") || lower.contains("hire") || lower.contains("hiring") || lower.contains("job")) {
            relationships.add("Internship / Hiring");
        }
        if (lower.contains("referral") || lower.contains("refer")) {
            relationships.add("Referral");
        }
        if (lower.contains("mentor") || lower.contains("guidance") || lower.contains("advice")) {
            relationships.add("Mentorship / Guidance");
        }
        if (lower.contains("prospect") || lower.contains("sales") || lower.contains("b2b") || lower.contains("customer")) {
            relationships.add("Business Development / Sales");
        }
        if (relationships.isEmpty()) {
            relationships.add("Professional Networking");
        }

        List<String> topics = new ArrayList<>();
        String[] candidateTopics = {
                "Java", "Spring Boot", "Spring", "Backend", "Python", "Go", "Rust", "C++",
                "Cloud", "AWS", "Kubernetes", "Docker", "DevOps", "Microservices", "Distributed Systems",
                "AI", "Machine Learning", "LLM", "Data Engineering", "Full Stack", "Frontend", "React"
        };
        for (String topic : candidateTopics) {
            if (lower.contains(topic.toLowerCase(Locale.ROOT))) {
                topics.add(topic);
            }
        }

        return new ResearchObjective(text, characteristics, relationships, topics, List.of(), List.of(), false);
    }
}
