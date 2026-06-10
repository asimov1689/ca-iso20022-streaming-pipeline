package com.gagroup.ca.confirmations.api.entity;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Read-only view of the master table for the CQRS read side.
@Entity
@Table(name = "ca_settled_events")
@Getter
@Setter
@NoArgsConstructor
public class CaSettledEventReadEntity {

    @Id
    @Column(name = "message_id")
    private String messageId;

    @Column(name = "confirmation_ref")  private String     confirmationRef;
    @Column(name = "isin")              private String     isin;
    @Column(name = "event_type")        private String     eventType;
    @Column(name = "settlement_date")   private String     settlementDate;
    @Column(name = "net_cash_amount")   private BigDecimal netCashAmount;
    @Column(name = "currency")          private String     currency;
    @Column(name = "account_id")        private String     accountId;
    @Column(name = "quantity")          private BigDecimal quantity;
    @Column(name = "status")            private String     status;
    @Column(name = "source_format")     private String     sourceFormat;
    @Column(name = "security_name")     private String     securityName;
    @Column(name = "issuer_lei")        private String     issuerLei;
    @Column(name = "market_of_listing") private String     marketOfListing;
    @Column(name = "settle_ccy")        private String     settleCcy;
    @Column(name = "enriched_at")       private Instant    enrichedAt;
}
