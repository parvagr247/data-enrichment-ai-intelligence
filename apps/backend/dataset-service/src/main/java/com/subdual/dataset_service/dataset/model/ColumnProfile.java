package com.subdual.dataset_service.dataset.model;

import java.util.List;

public record ColumnProfile(
        String columnName,
        ColumnRole detectedRole,
        double completenessPercentage,
        int populatedCount,
        int totalCount,
        List<String> sampleValues
) {
}
