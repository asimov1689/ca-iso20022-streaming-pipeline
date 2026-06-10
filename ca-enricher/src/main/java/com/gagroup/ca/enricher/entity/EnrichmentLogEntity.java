package com.gagroup.ca.enricher.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "ca_enrichment_log")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class EnrichmentLogEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "enrichment_id")
    private Long enrichmentId;

    @Column(name = "message_id",     nullable = false, length = 64) private String  messageId;
    @Column(name = "isin",           nullable = false, length = 12) private String  isin;
    @Column(name = "security_name",  length = 256)  private String  securityName;
    @Column(name = "issuer_lei",     length = 64)   private String  issuerLei;
    @Column(name = "market_of_listing", length = 128) private String marketOfListing;
    @Column(name = "settle_ccy",     length = 3)    private String  settleCcy;
    @Column(name = "cached",         nullable = false) private boolean cached;
    @Column(name = "cobol_response_ms") private Integer cobolResponseMs;
    @Column(name = "enriched_at")    private Instant enrichedAt;
}
