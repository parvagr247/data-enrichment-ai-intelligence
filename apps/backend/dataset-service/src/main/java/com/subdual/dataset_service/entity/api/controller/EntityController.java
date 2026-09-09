package com.subdual.dataset_service.entity.api.controller;

import com.subdual.dataset_service.entity.api.dto.request.PersistEntityRequest;
import com.subdual.dataset_service.entity.api.dto.response.EntityDetailResponse;
import com.subdual.dataset_service.entity.api.dto.response.EntitySummaryResponse;
import com.subdual.dataset_service.entity.service.EntityPersistenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/entities")
@RequiredArgsConstructor
public class EntityController {

    private final EntityPersistenceService persistenceService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EntityDetailResponse> persistEntity(
            @Valid @RequestBody PersistEntityRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        EntityDetailResponse response = (userId != null && !userId.isBlank())
            ? persistenceService.persistOrUpdate(request, userId)
            : persistenceService.persistOrUpdate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping(value = "/{entityId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EntityDetailResponse> getEntity(
            @PathVariable String entityId,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return persistenceService.findById(entityId, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<EntitySummaryResponse>> listEntities(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        if (page != null || size != null) {
            int p = page != null ? page : 0;
            int s = size != null ? size : 20;
            return ResponseEntity.ok(persistenceService.list(p, s, userId));
        }
        return ResponseEntity.ok(persistenceService.listAll(userId));
    }
}
