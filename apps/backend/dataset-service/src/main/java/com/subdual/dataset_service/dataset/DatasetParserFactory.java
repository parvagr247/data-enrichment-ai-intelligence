package com.subdual.dataset_service.dataset;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DatasetParserFactory {

    private final List<DatasetParser> parsers;

    public DatasetParser getParser(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Dataset file name must not be blank");
        }
        return parsers.stream()
                .filter(p -> p.supports(fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported dataset file format for: " + fileName));
    }
}
