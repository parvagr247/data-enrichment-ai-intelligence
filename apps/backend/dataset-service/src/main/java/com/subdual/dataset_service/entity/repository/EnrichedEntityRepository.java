package com.subdual.dataset_service.entity.repository;

import com.subdual.dataset_service.entity.model.EnrichedEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrichedEntityRepository extends JpaRepository<EnrichedEntity, String> {

    List<EnrichedEntity> findByUserId(String userId, Sort sort);

    Page<EnrichedEntity> findByUserId(String userId, Pageable pageable);

    Optional<EnrichedEntity> findByEntityIdAndUserId(String entityId, String userId);
}
