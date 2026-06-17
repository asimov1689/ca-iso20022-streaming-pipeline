package com.gagroup.ca.confirmations.api.controller;

import com.gagroup.ca.confirmations.api.entity.CaSettledEventReadEntity;
import com.gagroup.ca.confirmations.api.repository.CaSettledEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Settled Confirmations", description = "CQRS read model for settled corporate action confirmations")
public class CaApiController {

    private final CaSettledEventRepository repo;

    public CaApiController(CaSettledEventRepository repo) {
        this.repo = repo;
    }

    // @Cacheable with 30s TTL — frequent same-ISIN queries hit cache, not DB.
    @GetMapping
    @Operation(summary = "List settled confirmations",
            description = "Returns all settled confirmations or filters by ISIN, event type, and account.")
    @Cacheable(value = "settled-events",
               key = "#isin + ':' + #eventType + ':' + #accountId")
    public List<CaSettledEventReadEntity> list(
            @Parameter(description = "ISIN filter") @RequestParam(required = false) String isin,
            @Parameter(description = "Corporate action event type") @RequestParam(required = false) String eventType,
            @Parameter(description = "Account identifier") @RequestParam(required = false) String accountId) {
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
    @Operation(summary = "Get a settled confirmation by message ID")
    @Cacheable(value = "settled-event-by-id", key = "#messageId")
    public ResponseEntity<CaSettledEventReadEntity> get(
            @Parameter(description = "Pipeline message identifier") @PathVariable String messageId) {
        return repo.findById(messageId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/settlement-range")
    @Operation(summary = "List settled confirmations by settlement date range")
    @Cacheable(value = "settled-events-range", key = "#from + ':' + #to")
    public List<CaSettledEventReadEntity> settlementRange(
            @Parameter(description = "Inclusive start date in yyyyMMdd format") @RequestParam String from,
            @Parameter(description = "Inclusive end date in yyyyMMdd format") @RequestParam String to) {
        return repo.findBySettlementDateBetween(from, to);
    }

    @GetMapping("/health")
    @Operation(summary = "Return API health and master table count")
    public Map<String, String> health() {
        return Map.of(
                "status",             "UP",
                "service",            "ca-confirmations-api",
                "master-table-count", String.valueOf(repo.count()));
    }
}
