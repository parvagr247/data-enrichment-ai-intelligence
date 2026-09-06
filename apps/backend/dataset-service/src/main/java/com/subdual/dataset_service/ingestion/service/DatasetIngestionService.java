package com.subdual.dataset_service.ingestion.service;

import com.subdual.dataset_service.ingestion.model.DatasetProfileReport;
import com.subdual.dataset_service.ingestion.model.RawDataset;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Service interface for dataset ingestion, parsing, and intelligence profiling (Phases 2 & 3).
 */
public interface DatasetIngestionService {

    RawDataset parseFile(InputStream inputStream, String fileName);

    DatasetProfileReport profileDataset(RawDataset rawDataset);

    DatasetProfileReport ingestAndProfile(InputStream inputStream, String fileName);

    DatasetProfileReport profileRawRows(String datasetName, List<Map<String, String>> rows);
}
