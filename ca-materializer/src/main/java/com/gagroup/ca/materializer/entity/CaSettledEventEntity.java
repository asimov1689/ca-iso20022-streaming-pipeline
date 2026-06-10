package com.gagroup.ca.materializer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "ca_settled_events")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CaSettledEventEntity {

    @Id @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    @Column(name = "confirmation_ref", nullable = false, length = 64)
    private String confirmationRef;

    @Column(name = "isin",             nullable = false, length = 12) private String    isin;
    @Column(name = "event_type",       nullable = false, length = 4)  private String    eventType;
    @Column(name = "settlement_date",  length = 8)    private String    settlementDate;
    @Column(name = "net_cash_amount",  precision=18, scale=6) private BigDecimal netCashAmount;
    @Column(name = "currency",         length = 3)    private String    currency;
    @Column(name = "account_id",       length = 64)   private String    accountId;
    @Column(name = "quantity",         precision=18, scale=6) private BigDecimal quantity;
    @Column(name = "status",           length = 8)    private String    status;
    @Column(name = "source_format",    length = 8)    private String    sourceFormat;
    @Column(name = "security_name",    length = 256)  private String    securityName;
    @Column(name = "issuer_lei",       length = 64)   private String    issuerLei;
    @Column(name = "market_of_listing",length = 128)  private String    marketOfListing;
    @Column(name = "settle_ccy",       length = 3)    private String    settleCcy;
    @Column(name = "received_at")  private Instant receivedAt;
    @Column(name = "enriched_at")  private Instant enrichedAt;
}
