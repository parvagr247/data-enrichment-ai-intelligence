package com.subdual.dataset_service.service;

import com.subdual.dataset_service.dto.EntityDetailResponse;
import com.subdual.dataset_service.dto.EntitySummaryResponse;
import com.subdual.dataset_service.dto.PersistEntityRequest;

import java.util.List;
import java.util.Optional;

// This is primary service interface for storing, querying, and managing the lifecyle of canonical enriched entities in relational database MYSQL
public interface EntityPersistenceService {
    EntityDetailResponse persistOrUpdate(PersistEntityRequest request);
    Optional<EntityDetailResponse> findById(String entityId);
    List<EntitySummaryResponse> listAll();
    List<EntitySummaryResponse> list(int page, int size);
}
