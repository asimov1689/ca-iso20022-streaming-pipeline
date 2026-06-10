package com.gagroup.ca.formatter.parser;

import com.gagroup.ca.model.RawConfirmationEvent;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MxConfirmationParserTest {

    private final MxConfirmationParser parser = new MxConfirmationParser();

    private static final String VALID_SEEV036 = """
            <Document>
              <CorpActnConf>
                <ConfRef>CONF-001</ConfRef>
                <FinInstrm><ISIN>CH0012221716</ISIN></FinInstrm>
                <EvtTp>DVCA</EvtTp>
                <SttlmDt>20261231</SttlmDt>
                <NetCshAmt><Amt Ccy="CHF">2500.00</Amt></NetCshAmt>
                <AcctId>ACC-001</AcctId>
                <Qty>1000</Qty>
                <Sts>SETT</Sts>
              </CorpActnConf>
            </Document>
            """;

    private RawConfirmationEvent raw(String payload) {
        return new RawConfirmationEvent("MSG-001", "seev.036", payload, Instant.now());
    }

    @Test
    void parseValidSeev036ShouldReturnAllFieldsCorrectly() {
        var event = parser.parse(raw(VALID_SEEV036));

        assertThat(event.confirmationRef()).isEqualTo("CONF-001");
        assertThat(event.isin()).isEqualTo("CH0012221716");
        assertThat(event.eventType()).isEqualTo("DVCA");
        assertThat(event.settlementDate()).isEqualTo("20261231");
        assertThat(event.netCashAmount()).isEqualByComparingTo(new BigDecimal("2500.00"));
        assertThat(event.currency()).isEqualTo("CHF");
        assertThat(event.accountId()).isEqualTo("ACC-001");
        assertThat(event.quantity()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(event.status()).isEqualTo("SETT");
        assertThat(event.sourceFormat()).isEqualTo("seev.036");
    }

    @Test
    void parseMalformedXmlShouldThrowIllegalArgument() {
        assertThatThrownBy(() -> parser.parse(raw("<not-valid-xml")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seev.036");
    }

    @Test
    void parseMessageIdIsPreserved() {
        var raw = new RawConfirmationEvent("PRESERVE-ME", "seev.036", VALID_SEEV036, Instant.now());

        var event = parser.parse(raw);

        assertThat(event.messageId()).isEqualTo("PRESERVE-ME");
    }
}
