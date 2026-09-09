package com.subdual.dataset_service.dataset.api.dto.request;

import java.util.List;
import java.util.Map;

public record DatasetProfileRequest(
        String datasetName,
        List<Map<String, String>> rows
) {
}
