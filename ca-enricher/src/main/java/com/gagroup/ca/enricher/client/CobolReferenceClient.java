package com.gagroup.ca.enricher.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Component
public class CobolReferenceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CobolReferenceClient.class);

    private final RestTemplate restTemplate;

    @Value("${cobol.stub.url}")
    private String cobolStubUrl;

    public CobolReferenceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Cacheable(value = "cobol-ref-data", key = "#isin")
    @SuppressWarnings("unchecked")
    public Map<String, String> fetchRefData(String isin) {
        try {
            var result = restTemplate.getForObject(
                    cobolStubUrl + "/cobol/reference/" + isin, Map.class);
            return result != null ? result : fallback();
        } catch (Exception e) {
            LOGGER.warn("COBOL stub unavailable isin={}: {}. Using fallback.", isin, e.getMessage());
            return fallback();
        }
    }

    private Map<String, String> fallback() {
        return Map.of(
                "securityName",    "LOOKUP_FAILED",
                "issuerLei",       "N/A",
                "marketOfListing", "N/A",
                "settleCcy",       "CHF");
    }
}
