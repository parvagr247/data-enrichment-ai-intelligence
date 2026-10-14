package com.subdual.dataset_service.dataset.service.factory;

import com.subdual.dataset_service.dataset.model.RawDataset;

import java.io.IOException;
import java.io.InputStream;

/**
 * Common interface for dataset file parsers (CSV, Excel, etc.).
 */
public interface DatasetParser {

    /**
     * Checks if this parser supports the given file extension or name.
     */
    boolean supports(String fileName);

    /**
     * Parses the incoming stream into a RawDataset.
     */
    RawDataset parse(InputStream inputStream, String fileName) throws IOException;
}
