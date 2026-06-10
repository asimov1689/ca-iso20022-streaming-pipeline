package com.gagroup.ca.formatter.parser;

import com.gagroup.ca.model.CaConfirmationEvent;
import com.gagroup.ca.model.RawConfirmationEvent;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Parses seev.036.001.14 ISO 20022 CA Confirmation XML.
 * Expected structure: Document/CorpActnConf with ConfRef, ISIN, EvtTp,
 * SttlmDt, Amt (with Ccy attr), AcctId, Qty, Sts elements.
 */
@Component
public class MxConfirmationParser {

    public CaConfirmationEvent parse(RawConfirmationEvent raw) {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(
                            raw.rawPayload().getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();

            return new CaConfirmationEvent(
                    raw.messageId(),
                    text(doc, "ConfRef"),
                    text(doc, "ISIN"),
                    text(doc, "EvtTp"),
                    text(doc, "SttlmDt"),
                    new BigDecimal(text(doc, "Amt")),
                    attr(doc, "Amt", "Ccy"),
                    text(doc, "AcctId"),
                    new BigDecimal(text(doc, "Qty")),
                    text(doc, "Sts"),
                    "seev.036",
                    Instant.now()
            );
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to parse seev.036 messageId=" + raw.messageId() + ": " + e.getMessage(), e);
        }
    }

    private String text(Document doc, String tag) {
        return doc.getElementsByTagName(tag).item(0).getTextContent().trim();
    }

    private String attr(Document doc, String tag, String attribute) {
        return ((Element) doc.getElementsByTagName(tag).item(0)).getAttribute(attribute);
    }
}
