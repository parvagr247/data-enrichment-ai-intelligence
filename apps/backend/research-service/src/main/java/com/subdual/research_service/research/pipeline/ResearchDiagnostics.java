package com.subdual.research_service.research.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ResearchDiagnostics {

    private final List<String> warnings = new ArrayList<>();

    public synchronized void addWarning(String warning) {
        if (warning != null && !warning.isBlank()) {
            warnings.add(warning.trim());
        }
    }

    public void recordSourceSkipped(String domain, String reason) {
        addWarning("Source skipped (" + (domain != null ? domain : "unknown") + "): "
                + (reason != null ? reason : "Inaccessible"));
    }

    public void recordPrimaryInaccessible(String domain, String statusDetail) {
        addWarning("Primary source (" + (domain != null ? domain : "unknown")
                + ") could not be directly fetched (" + (statusDetail != null ? statusDetail : "Inaccessible")
                + "); continuing research using corroborating public sources.");
    }

    public void recordPersistenceFailure(String message) {
        addWarning("Failed to persist entity to dataset-service: "
                + (message != null ? message : "Unknown error"));
    }

    public synchronized boolean hasDegradedSources() {
        return warnings.stream().anyMatch(w -> w.startsWith("Source skipped"));
    }

    public synchronized List<String> warnings() {
        return Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public synchronized int warningCount() {
        return warnings.size();
    }
}
