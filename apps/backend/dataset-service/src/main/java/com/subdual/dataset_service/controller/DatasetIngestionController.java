package com.subdual.dataset_service.controller;

import com.subdual.dataset_service.dto.request.DatasetProfileRequest;
import com.subdual.dataset_service.ingestion.model.DatasetProfileReport;
import com.subdual.dataset_service.ingestion.service.DatasetIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * V2 Controller for dataset ingestion, parsing, and intelligence profiling.
 */
@RestController
@RequestMapping("/api/v2/datasets")
@RequiredArgsConstructor
@Slf4j
public class DatasetIngestionController {

    private final DatasetIngestionService ingestionService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DatasetProfileReport> uploadAndProfile(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded_dataset.csv";
        log.info("Received dataset upload: '{}' ({} bytes)", originalFilename, file.getSize());

        DatasetProfileReport report = ingestionService.ingestAndProfile(file.getInputStream(), originalFilename);
        return ResponseEntity.ok(report);
    }

    @PostMapping(value = "/profile", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DatasetProfileReport> profileRows(
            @RequestBody DatasetProfileRequest request
    ) {
        if (request == null || request.rows() == null || request.rows().isEmpty()) {
            throw new IllegalArgumentException("Dataset rows must be provided");
        }

        DatasetProfileReport report = ingestionService.profileRawRows(request.datasetName(), request.rows());
        return ResponseEntity.ok(report);
    }
}
