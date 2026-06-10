package com.gagroup.ca.model;

import java.math.BigDecimal;
import java.time.Instant;

public record CaConfirmationEvent(
        String messageId,
        String confirmationRef,
        String isin,
        String eventType,
        String settlementDate,
        BigDecimal netCashAmount,
        String currency,
        String accountId,
        BigDecimal quantity,
        String status,
        String sourceFormat,
        Instant processedAt
) {}
