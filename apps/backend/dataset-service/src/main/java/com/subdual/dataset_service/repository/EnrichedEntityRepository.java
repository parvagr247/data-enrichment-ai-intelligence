package com.subdual.dataset_service.repository;

import com.subdual.dataset_service.domain.EnrichedEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EnrichedEntityRepository extends JpaRepository<EnrichedEntity, String> {
}
