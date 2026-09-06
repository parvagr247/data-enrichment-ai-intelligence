package com.subdual.dataset_service.repository;

import com.subdual.dataset_service.domain.EnrichedEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnrichedEntityRepository extends JpaRepository<EnrichedEntity, String> {

    List<EnrichedEntity> findByUserId(String userId, Sort sort);

    Page<EnrichedEntity> findByUserId(String userId, Pageable pageable);

    Optional<EnrichedEntity> findByEntityIdAndUserId(String entityId, String userId);
}
