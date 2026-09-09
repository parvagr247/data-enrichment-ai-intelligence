package com.subdual.dataset_service.dataset.service.helper;

import com.subdual.dataset_service.dataset.model.RawDataset;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Dataset validation rules.
 */
@Component
public class DatasetValidator {

    public void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Dataset file name must not be empty");
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".csv") && !lower.endsWith(".xlsx") && !lower.endsWith(".tsv") && !lower.endsWith(".txt")) {
            throw new IllegalArgumentException("Unsupported dataset file format. Allowed formats: .csv, .xlsx, .tsv");
        }
    }

    public void validateParsedDataset(RawDataset rawDataset) {
        if (rawDataset.headers().isEmpty()) {
            throw new IllegalArgumentException("Dataset has no headers or is completely empty");
        }
        if (rawDataset.validRowCount() == 0) {
            throw new IllegalArgumentException("Dataset contains 0 valid data rows (empty dataset)");
        }
    }
}
