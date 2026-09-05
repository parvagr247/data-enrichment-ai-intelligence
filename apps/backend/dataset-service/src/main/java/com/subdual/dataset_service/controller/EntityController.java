package com.subdual.dataset_service.controller;

import com.subdual.dataset_service.dto.EntityDetailResponse;
import com.subdual.dataset_service.dto.EntitySummaryResponse;
import com.subdual.dataset_service.dto.PersistEntityRequest;
import com.subdual.dataset_service.service.EntityPersistenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/entities")
@RequiredArgsConstructor
public class EntityController {

    private final EntityPersistenceService persistenceService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EntityDetailResponse> persistEntity(@Valid @RequestBody PersistEntityRequest request) {
        EntityDetailResponse response = persistenceService.persistOrUpdate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping(value = "/{entityId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EntityDetailResponse> getEntity(@PathVariable String entityId) {
        return persistenceService.findById(entityId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<EntitySummaryResponse>> listEntities() {
        return ResponseEntity.ok(persistenceService.listAll());
    }
}
