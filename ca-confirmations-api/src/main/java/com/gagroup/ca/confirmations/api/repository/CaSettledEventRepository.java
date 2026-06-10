package com.gagroup.ca.confirmations.api.repository;

import com.gagroup.ca.confirmations.api.entity.CaSettledEventReadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CaSettledEventRepository extends JpaRepository<CaSettledEventReadEntity, String> {

    List<CaSettledEventReadEntity> findByIsin(String isin);

    List<CaSettledEventReadEntity> findByEventType(String eventType);

    List<CaSettledEventReadEntity> findByAccountId(String accountId);

    List<CaSettledEventReadEntity> findByIsinAndEventType(String isin, String eventType);

    @Query("SELECT e FROM CaSettledEventReadEntity e " +
           "WHERE e.settlementDate BETWEEN :from AND :to ORDER BY e.settlementDate ASC")
    List<CaSettledEventReadEntity> findBySettlementDateBetween(
            @Param("from") String from,
            @Param("to")   String to);
}
