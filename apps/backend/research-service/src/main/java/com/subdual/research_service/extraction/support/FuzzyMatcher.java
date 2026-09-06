package com.subdual.research_service.extraction.support;

import java.util.*;

/**
 * Lightweight fuzzy matching utility for entity and organization disambiguation (Task 46).
 * Employs Token Jaccard overlap and normalized Levenshtein distance without heavy NLP dependencies.
 */
public final class FuzzyMatcher {

    private FuzzyMatcher() {}

    public static double jaccardTokenSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null || s1.isBlank() || s2.isBlank()) {
            return 0.0;
        }

        Set<String> tokens1 = new HashSet<>(Arrays.asList(s1.toLowerCase(Locale.ROOT).split("\\s+")));
        Set<String> tokens2 = new HashSet<>(Arrays.asList(s2.toLowerCase(Locale.ROOT).split("\\s+")));

        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);

        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);

        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    public static double levenshteinSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;
        if (s1.isEmpty() || s2.isEmpty()) return 0.0;

        String a = s1.toLowerCase(Locale.ROOT);
        String b = s2.toLowerCase(Locale.ROOT);

        int maxLen = Math.max(a.length(), b.length());
        int distance = computeLevenshteinDistance(a, b);

        return 1.0 - ((double) distance / maxLen);
    }

    public static boolean isFuzzyMatch(String s1, String s2, double threshold) {
        if (s1 == null || s2 == null) return false;
        double jaccard = jaccardTokenSimilarity(s1, s2);
        if (jaccard >= threshold) return true;

        double lev = levenshteinSimilarity(s1, s2);
        return lev >= threshold;
    }

    private static int computeLevenshteinDistance(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            costs[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]),
                        a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }

        return costs[b.length()];
    }
}
