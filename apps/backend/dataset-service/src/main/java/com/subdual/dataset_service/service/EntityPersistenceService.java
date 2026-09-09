package com.subdual.dataset_service.service;

import com.subdual.dataset_service.dto.request.PersistEntityRequest;
import com.subdual.dataset_service.dto.response.EntityDetailResponse;
import com.subdual.dataset_service.dto.response.EntitySummaryResponse;

import java.util.List;
import java.util.Optional;

public interface EntityPersistenceService {

    default EntityDetailResponse persistOrUpdate(PersistEntityRequest request) {
        return persistOrUpdate(request, null);
    }

    EntityDetailResponse persistOrUpdate(PersistEntityRequest request, String userId);

    default Optional<EntityDetailResponse> findById(String entityId) {
        return findById(entityId, null);
    }

    Optional<EntityDetailResponse> findById(String entityId, String userId);

    default List<EntitySummaryResponse> listAll() {
        return listAll(null);
    }

    List<EntitySummaryResponse> listAll(String userId);

    default List<EntitySummaryResponse> list(int page, int size) {
        return list(page, size, null);
    }

    List<EntitySummaryResponse> list(int page, int size, String userId);
}
