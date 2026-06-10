package com.gagroup.ca.formatter.parser;

import com.gagroup.ca.model.RawConfirmationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mt566ConfirmationParserTest {

    private final Mt566ConfirmationParser parser = new Mt566ConfirmationParser();

    private RawConfirmationEvent raw(String payload) {
        return new RawConfirmationEvent("MSG-001", "MT566", payload, Instant.now());
    }

    @Test
    void parseValidDvcaShouldReturnAllFieldsCorrectly() {
        String mt566 = "CONF-001|CH0012221716|DVCA|20261231|2500.00|CHF|ACC-001|1000|SETT";

        var event = parser.parse(raw(mt566));

        assertThat(event.confirmationRef()).isEqualTo("CONF-001");
        assertThat(event.isin()).isEqualTo("CH0012221716");
        assertThat(event.eventType()).isEqualTo("DVCA");
        assertThat(event.settlementDate()).isEqualTo("20261231");
        assertThat(event.netCashAmount()).isEqualByComparingTo(new BigDecimal("2500.00"));
        assertThat(event.currency()).isEqualTo("CHF");
        assertThat(event.accountId()).isEqualTo("ACC-001");
        assertThat(event.quantity()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(event.status()).isEqualTo("SETT");
        assertThat(event.sourceFormat()).isEqualTo("MT566");
    }

    @Test
    void parseTooFewFieldsShouldThrowIllegalArgument() {
        String incomplete = "CONF-001|CH0012221716|DVCA";

        assertThatThrownBy(() -> parser.parse(raw(incomplete)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected 9");
    }

    @Test
    void parseInvalidNetCashAmountShouldThrowNumberFormat() {
        String badAmount = "CONF-001|CH0012221716|DVCA|20261231|NOT_A_NUM|CHF|ACC-001|1000|SETT";

        assertThatThrownBy(() -> parser.parse(raw(badAmount)))
                .isInstanceOf(NumberFormatException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DVCA", "SPLF", "RHTS", "MRGR"})
    void parseAllSupportedEventTypesShouldParseSuccessfully(String eventType) {
        String mt566 = "CONF-001|CH0012221716|" + eventType + "|20261231|2500.00|CHF|ACC-001|1000|SETT";

        var event = parser.parse(raw(mt566));

        assertThat(event.eventType()).isEqualTo(eventType);
    }
}
