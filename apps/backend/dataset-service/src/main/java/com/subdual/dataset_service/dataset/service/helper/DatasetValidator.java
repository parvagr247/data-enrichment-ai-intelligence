package com.subdual.dataset_service.dataset.service.helper;

import com.subdual.dataset_service.dataset.model.RawDataset;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Dataset validation rules.
 * Implements Singleton Pattern via static inner class holder with thread safety.
 */
@Component
public class DatasetValidator {

    private static class InstanceHolder {
        private static final DatasetValidator INSTANCE = new DatasetValidator();
    }

    public static DatasetValidator getInstance() {
        return InstanceHolder.INSTANCE;
    }

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
