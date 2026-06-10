package com.gagroup.ca.producer.controller;

import com.gagroup.ca.producer.service.CaProducerService;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingest")
public class CaIngestController {

    private final CaProducerService service;

    public CaIngestController(CaProducerService service) {
        this.service = service;
    }

    /**
     * POST /api/v1/ingest/mt566
     * Body: pipe-delimited MT566 CA Confirmation from COBOL batch file
     * Format: CONF_REF|ISIN|EVENT_TYPE|SETTLE_DATE|NET_CASH|CCY|ACCOUNT|QTY|STATUS
     * Example: CONF-20261231-001|CH0012221716|DVCA|20261231|2500.00|CHF|ACC-001|1000|SETT
     */
    @PostMapping(value = "/mt566", consumes = "text/plain")
    public ResponseEntity<Map<String, String>> ingestMt566(@RequestBody String payload) {
        String id = service.publish(payload, "MT566");
        return ResponseEntity.accepted()
                .body(Map.of("messageId", id, "type", "MT566", "status", "ACCEPTED"));
    }

    /**
     * POST /api/v1/ingest/seev036
     * Body: seev.036.001.14 ISO 20022 CA Confirmation XML
     */
    @PostMapping(value = "/seev036", consumes = {"application/xml", "text/xml"})
    public ResponseEntity<Map<String, String>> ingestSeev036(@RequestBody String payload) {
        String id = service.publish(payload, "seev.036");
        return ResponseEntity.accepted()
                .body(Map.of("messageId", id, "type", "seev.036", "status", "ACCEPTED"));
    }
}
