package com.gagroup.ca.model;

import java.time.Instant;

public record RawConfirmationEvent(
        String messageId,
        String messageType,
        String rawPayload,
        Instant receivedAt
) {}
