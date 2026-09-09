package com.subdual.research_service.extraction.extractor.field;

import com.subdual.research_service.research.api.EvidenceTuple;
import com.subdual.research_service.discovery.ranking.SourceTypeClassifier;
import com.subdual.research_service.extraction.document.ExtractedDocument;
import com.subdual.research_service.research.model.ConfidenceTier;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import com.subdual.research_service.research.model.SourceReliability;
import com.subdual.research_service.research.model.SourceType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Field extractor for public professional activity, posts, and discussed themes.
 * Strictly distinguishes AUTHORED, MENTIONED, LIKED, and ACTIVITY to avoid false attribution.
 */
@Component
public class ActivityFieldExtractor implements FieldExtractor {

    // Matches: "Posted: ...", "Published article: ...", "Author: ..."
    private static final Pattern AUTHORED_POST_PATTERN = Pattern.compile(
            "(?i)(?:Posted|Published article|Shared an update|Wrote):?\\s*\"?([^\"\\n\\r]{10,120})\"?"
    );

    // Matches: "Liked by ...", "liked this", "Reacted to ..."
    private static final Pattern LIKED_POST_PATTERN = Pattern.compile(
            "(?i)(?:Liked|Reacted to|Clapped for):?\\s*\"?([^\"\\n\\r]{10,120})\"?"
    );

    // Matches: "Mentioned in ...", "Tagged in ..."
    private static final Pattern MENTIONED_POST_PATTERN = Pattern.compile(
            "(?i)(?:Mentioned in|Tagged in|Co-authored with):?\\s*\"?([^\"\\n\\r]{10,120})\"?"
    );

    @Override
    public String fieldKey() {
        return "activity";
    }

    @Override
    public boolean supports(String requestedField, EntityType entityType) {
        if (requestedField == null) return false;
        String lower = requestedField.toLowerCase(Locale.ROOT).trim();
        return lower.equals("activity") || lower.equals("posts") || lower.equals("articles")
                || lower.equals("themes") || lower.equals("publications") || lower.equals("social_activity");
    }

    @Override
    public EvidenceTuple extract(ExtractedDocument doc, ResearchTarget target) {
        if (doc == null || doc.cleanText() == null || doc.cleanText().isBlank()) {
            return null;
        }

        String text = doc.cleanText();
        List<Map<String, Object>> recentPosts = new ArrayList<>();
        Set<String> themes = new LinkedHashSet<>();
        Set<String> mentionedTech = new LinkedHashSet<>();
        String primaryQuote = null;

        // 1. Scan for AUTHORED posts
        Matcher mAuth = AUTHORED_POST_PATTERN.matcher(text);
        while (mAuth.find() && recentPosts.size() < 4) {
            String title = mAuth.group(1).trim();
            String snippet = RoleFieldExtractor.extractSentence(text, mAuth.start(), mAuth.end());
            if (primaryQuote == null) primaryQuote = snippet;

            Map<String, Object> post = new LinkedHashMap<>();
            post.put("title", title);
            post.put("type", "AUTHORED");
            post.put("date", null);
            post.put("summary", snippet);
            post.put("sourceUrl", doc.url());
            recentPosts.add(post);
            extractThemesAndTech(title, themes, mentionedTech);
        }

        // 2. Scan for LIKED activity
        Matcher mLiked = LIKED_POST_PATTERN.matcher(text);
        while (mLiked.find() && recentPosts.size() < 6) {
            String title = mLiked.group(1).trim();
            String snippet = RoleFieldExtractor.extractSentence(text, mLiked.start(), mLiked.end());

            Map<String, Object> post = new LinkedHashMap<>();
            post.put("title", title);
            post.put("type", "LIKED");
            post.put("date", null);
            post.put("summary", snippet);
            post.put("sourceUrl", doc.url());
            recentPosts.add(post);
            extractThemesAndTech(title, themes, mentionedTech);
        }

        // 3. Scan for MENTIONED activity
        Matcher mMention = MENTIONED_POST_PATTERN.matcher(text);
        while (mMention.find() && recentPosts.size() < 6) {
            String title = mMention.group(1).trim();
            String snippet = RoleFieldExtractor.extractSentence(text, mMention.start(), mMention.end());

            Map<String, Object> post = new LinkedHashMap<>();
            post.put("title", title);
            post.put("type", "MENTIONED");
            post.put("date", null);
            post.put("summary", snippet);
            post.put("sourceUrl", doc.url());
            recentPosts.add(post);
            extractThemesAndTech(title, themes, mentionedTech);
        }

        if (recentPosts.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < recentPosts.size(); i++) {
            Map<String, Object> post = recentPosts.get(i);
            if (i > 0) sb.append(" | ");
            sb.append("[").append(post.get("type")).append("] ").append(post.get("title"));
        }
        String formattedValue = sb.toString();

        SourceType type = SourceTypeClassifier.classify(doc.url(), target != null ? target.canonicalUrl() : null);
        SourceReliability rel = SourceTypeClassifier.determineReliability(type);
        ConfidenceTier tier = (rel == SourceReliability.HIGH) ? ConfidenceTier.HIGH : ConfidenceTier.MEDIUM;

        return new EvidenceTuple(
                formattedValue,
                doc.url(),
                primaryQuote != null ? primaryQuote : "Public professional activity extracted",
                tier,
                List.of(doc.url()),
                false,
                null,
                type.name(),
                "ACTIVITY_FIELD_EXTRACTOR"
        );
    }

    private void extractThemesAndTech(String text, Set<String> themes, Set<String> tech) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("ai") || lower.contains("machine learning") || lower.contains("llm")) {
            themes.add("Artificial Intelligence");
        }
        if (lower.contains("cloud") || lower.contains("devops") || lower.contains("kubernetes")) {
            themes.add("Cloud Infrastructure");
        }
        if (lower.contains("open source") || lower.contains("kernel") || lower.contains("linux")) {
            themes.add("Open Source Development");
        }
        if (lower.contains("architecture") || lower.contains("distributed") || lower.contains("microservices")) {
            themes.add("System Architecture");
        }

        String[] keywords = {"Java", "Python", "Rust", "Go", "Docker", "Kubernetes", "Linux", "TypeScript"};
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase(Locale.ROOT))) {
                tech.add(kw);
            }
        }
    }
}
