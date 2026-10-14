package com.subdual.dataset_service.dataset.service.impl;

import com.subdual.dataset_service.dataset.api.dto.response.DatasetProfileReport;
import com.subdual.dataset_service.dataset.model.RawDataset;
import com.subdual.dataset_service.dataset.service.DatasetIngestionService;
import com.subdual.dataset_service.dataset.service.factory.DatasetParser;
import com.subdual.dataset_service.dataset.service.factory.DatasetParserFactory;
import com.subdual.dataset_service.dataset.service.helper.CsvDatasetParser;
import com.subdual.dataset_service.dataset.service.helper.DatasetProfiler;
import com.subdual.dataset_service.dataset.service.helper.DatasetValidator;
import com.subdual.dataset_service.dataset.service.helper.ExcelDatasetParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class DefaultDatasetIngestionService implements DatasetIngestionService {

    private final DatasetParserFactory parserFactory;
    private final DatasetValidator datasetValidator;
    private final DatasetProfiler datasetProfiler;

    @Autowired
    public DefaultDatasetIngestionService(
            DatasetParserFactory parserFactory,
            DatasetValidator datasetValidator,
            DatasetProfiler datasetProfiler
    ) {
        this.parserFactory = parserFactory;
        this.datasetValidator = datasetValidator;
        this.datasetProfiler = datasetProfiler;
    }

    /**
     * Backward-compatible constructor for testing and direct instantiation.
     */
    public DefaultDatasetIngestionService(
            CsvDatasetParser csvParser,
            ExcelDatasetParser excelParser,
            DatasetValidator datasetValidator,
            DatasetProfiler datasetProfiler
    ) {
        this(new DatasetParserFactory(List.of(csvParser, excelParser)), datasetValidator, datasetProfiler);
    }

    @Override // Parses, validates, and returns the uploaded dataset using Factory Pattern.
    public RawDataset parseFile(InputStream inputStream, String fileName) {
        datasetValidator.validateFileName(fileName);

        try {
            DatasetParser parser = parserFactory.getParser(fileName);
            RawDataset dataset = parser.parse(inputStream, fileName);

            datasetValidator.validateParsedDataset(dataset);
            log.info("Parsed dataset '{}': {} valid rows, {} malformed, {} duplicates",
                    fileName, dataset.validRowCount(), dataset.malformedRows().size(), dataset.duplicateRowIndices().size());

            return dataset;
        } catch (IOException e) {
            log.error("Failed reading dataset file '{}': {}", fileName, e.getMessage());
            throw new IllegalArgumentException("Could not read or parse dataset file: " + e.getMessage(), e);
        }
    }

    @Override // Generates statistical profile and column semantic mapping report.
    public DatasetProfileReport profileDataset(RawDataset rawDataset) {
        return datasetProfiler.profile(rawDataset);
    }

    @Override // Ingests raw file stream and immediately executes profiling.
    public DatasetProfileReport ingestAndProfile(InputStream inputStream, String fileName) {
        RawDataset rawDataset = parseFile(inputStream, fileName);
        return profileDataset(rawDataset);
    }

    @Override // Profiles in-memory row data with reconstructed header schemas.
    public DatasetProfileReport profileRawRows(String datasetName, List<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("Dataset rows list must not be empty");
        }

        List<String> headers = extractHeadersFromRows(rows);
        RawDataset raw = new RawDataset(
                datasetName != null ? datasetName : "dataset.csv",
                headers,
                rows,
                List.of(),
                Set.of()
        );

        return profileDataset(raw);
    }

    private List<String> extractHeadersFromRows(List<Map<String, String>> rows) {
        Set<String> headerSet = new LinkedHashSet<>(); // Collects distinct headers in order.
        for (Map<String, String> row : rows) {
            if (row != null) {
                headerSet.addAll(row.keySet());
            }
        }
        return new ArrayList<>(headerSet);
    }
}
