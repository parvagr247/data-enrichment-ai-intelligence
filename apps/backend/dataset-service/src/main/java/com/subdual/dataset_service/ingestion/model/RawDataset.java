package com.subdual.dataset_service.ingestion.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Raw parsed dataset output preserving all columns and row states (Tasks 17, 20).
 */
public record RawDataset(
        String fileName,
        List<String> headers,
        List<Map<String, String>> rows,
        List<MalformedRow> malformedRows,
        Set<Integer> duplicateRowIndices
) {
    public int totalRows() {
        return rows.size() + malformedRows.size();
    }

    public int validRowCount() {
        return rows.size();
    }
}
