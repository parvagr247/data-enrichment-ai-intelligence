package com.subdual.dataset_service.dto;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for profiling rows directly without file upload (Tasks 21, 30).
 */
public record DatasetProfileRequest(
        String datasetName,
        List<Map<String, String>> rows
) {
}
