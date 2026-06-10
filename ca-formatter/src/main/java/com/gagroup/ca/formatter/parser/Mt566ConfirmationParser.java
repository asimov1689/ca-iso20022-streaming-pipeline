package com.gagroup.ca.formatter.parser;

import com.gagroup.ca.model.CaConfirmationEvent;
import com.gagroup.ca.model.RawConfirmationEvent;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Parses pipe-delimited MT566 CA Confirmation output from COBOL batch.
 * Format: CONF_REF|ISIN|EVENT_TYPE|SETTLE_DATE|NET_CASH|CCY|ACCOUNT|QTY|STATUS
 * Example: CONF-20261231-001|CH0012221716|DVCA|20261231|2500.00|CHF|ACC-001|1000|SETT
 */
@Component
public class Mt566ConfirmationParser {

    private static final int FIELD_COUNT = 9;

    public CaConfirmationEvent parse(RawConfirmationEvent raw) {
        String[] f = raw.rawPayload().split("\\|");
        if (f.length < FIELD_COUNT) {
            throw new IllegalArgumentException(
                    "MT566 has " + f.length + " fields, expected " + FIELD_COUNT
                            + " | messageId=" + raw.messageId());
        }

        return new CaConfirmationEvent(
                raw.messageId(),
                f[0].trim(),                          // confirmationRef
                f[1].trim(),                          // isin
                f[2].trim(),                          // eventType
                f[3].trim(),                          // settlementDate YYYYMMDD
                new BigDecimal(f[4].trim()),          // netCashAmount
                f[5].trim(),                          // currency
                f[6].trim(),                          // accountId
                new BigDecimal(f[7].trim()),          // quantity
                f[8].trim(),                          // status
                "MT566",
                Instant.now()
        );
    }
}
