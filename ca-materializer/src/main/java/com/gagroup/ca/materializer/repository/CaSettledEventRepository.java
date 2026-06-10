package com.gagroup.ca.materializer.repository;

import com.gagroup.ca.materializer.entity.CaSettledEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaSettledEventRepository extends JpaRepository<CaSettledEventEntity, String> {
}
