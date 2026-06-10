package com.gagroup.ca.confirmations.api.controller;

import com.gagroup.ca.confirmations.api.entity.CaSettledEventReadEntity;
import com.gagroup.ca.confirmations.api.repository.CaSettledEventRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settled-confirmations")
public class CaApiController {

    private final CaSettledEventRepository repo;

    public CaApiController(CaSettledEventRepository repo) {
        this.repo = repo;
    }

    // @Cacheable with 30s TTL — frequent same-ISIN queries hit cache, not DB.
    @GetMapping
    @Cacheable(value = "settled-events",
               key = "#isin + ':' + #eventType + ':' + #accountId")
    public List<CaSettledEventReadEntity> list(
            @RequestParam(required = false) String isin,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String accountId) {
        if (isin != null && eventType != null) {
            return repo.findByIsinAndEventType(isin, eventType);
        }
        if (isin != null) {
            return repo.findByIsin(isin);
        }
        if (eventType != null) {
            return repo.findByEventType(eventType);
        }
        if (accountId != null) {
            return repo.findByAccountId(accountId);
        }
        return repo.findAll();
    }

    @GetMapping("/{messageId}")
    @Cacheable(value = "settled-event-by-id", key = "#messageId")
    public ResponseEntity<CaSettledEventReadEntity> get(@PathVariable String messageId) {
        return repo.findById(messageId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/settlement-range")
    @Cacheable(value = "settled-events-range", key = "#from + ':' + #to")
    public List<CaSettledEventReadEntity> settlementRange(
            @RequestParam String from,
            @RequestParam String to) {
        return repo.findBySettlementDateBetween(from, to);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status",             "UP",
                "service",            "ca-confirmations-api",
                "master-table-count", String.valueOf(repo.count()));
    }
}
