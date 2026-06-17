package com.gagroup.ca.stub.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulates the GA legacy COBOL mainframe reference-data service.
 * Returns security master data (name, LEI, market, currency) keyed by ISIN.
 * In production: ca-enricher points cobol.stub.url to the real mainframe adapter.
 * Strangler Fig pattern: mainframe is never modified, only wrapped.
 */
@RestController
@RequestMapping("/cobol/reference")
public class CobolReferenceController {

    private static final Map<String, Map<String, String>> REF_DATA = Map.of(
            "CH0012221716", Map.of(
                    "securityName",    "Arthur Dent Holdings",
                    "issuerLei",       "ARTHURDENTLEI000001",
                    "marketOfListing", "GA Exchange",
                    "settleCcy",       "CHF"),
            "CH0012255580", Map.of(
                    "securityName",    "Ford Prefect Ltd",
                    "issuerLei",       "FORDPREFECTLEI00001",
                    "marketOfListing", "GA Exchange",
                    "settleCcy",       "CHF"),
            "CH0038863350", Map.of(
                    "securityName",    "Trillian Astra PLC",
                    "issuerLei",       "TRILLIANASTRALEI001",
                    "marketOfListing", "GA Exchange",
                    "settleCcy",       "CHF"),
            "CH0012032048", Map.of(
                    "securityName",    "Marvin Android Group",
                    "issuerLei",       "MARVINANDROIDLEI001",
                    "marketOfListing", "GA Exchange",
                    "settleCcy",       "CHF")
    );

    @GetMapping("/{isin}")
    public Map<String, String> lookup(@PathVariable String isin) {
        return REF_DATA.getOrDefault(isin, Map.of(
                "securityName",    "UNKNOWN",
                "issuerLei",       "N/A",
                "marketOfListing", "UNKNOWN",
                "settleCcy",       "CHF"));
    }
}
