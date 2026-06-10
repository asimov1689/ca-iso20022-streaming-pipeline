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
                    "securityName",    "Nestle SA",
                    "issuerLei",       "PBLD0EJDB5FWOLXP3B76",
                    "marketOfListing", "GA Exchange",
                    "settleCcy",       "CHF"),
            "CH0012255580", Map.of(
                    "securityName",    "ABB Ltd",
                    "issuerLei",       "529900WOYRUK9N8J1T75",
                    "marketOfListing", "GA Exchange",
                    "settleCcy",       "CHF"),
            "CH0038863350", Map.of(
                    "securityName",    "Roche Holding AG",
                    "issuerLei",       "HMTNLVUNV8VXAIZQ9B87",
                    "marketOfListing", "GA Exchange",
                    "settleCcy",       "CHF"),
            "CH0012032048", Map.of(
                    "securityName",    "WRTHLY Group AG",
                    "issuerLei",       "BFM8T61CT2L1QCEMIK50",
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
