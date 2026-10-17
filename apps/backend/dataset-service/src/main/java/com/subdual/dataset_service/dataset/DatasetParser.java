package com.subdual.dataset_service.dataset;

import com.subdual.dataset_service.dataset.model.RawDataset;

import java.io.IOException;
import java.io.InputStream;

public interface DatasetParser {

    boolean supports(String fileName);

    RawDataset parse(InputStream inputStream, String fileName) throws IOException;
}
