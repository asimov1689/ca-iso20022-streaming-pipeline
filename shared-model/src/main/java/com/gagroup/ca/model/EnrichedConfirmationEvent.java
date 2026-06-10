package com.gagroup.ca.model;

import java.time.Instant;

public record EnrichedConfirmationEvent(
        CaConfirmationEvent base,
        String securityName,
        String issuerLei,
        String marketOfListing,
        String settleCcy,
        Instant enrichedAt
) {}
