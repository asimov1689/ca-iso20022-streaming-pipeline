package com.gagroup.ca.enricher.repository;

import com.gagroup.ca.enricher.entity.EnrichmentLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EnrichmentLogRepository extends JpaRepository<EnrichmentLogEntity, Long> {
    Optional<EnrichmentLogEntity> findByMessageId(String messageId);
}
