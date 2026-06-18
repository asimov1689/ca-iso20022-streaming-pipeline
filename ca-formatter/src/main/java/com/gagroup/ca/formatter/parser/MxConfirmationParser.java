package com.gagroup.ca.formatter.parser;

import com.gagroup.ca.model.CaConfirmationEvent;
import com.gagroup.ca.model.RawConfirmationEvent;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.XMLConstants;
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
            Document doc = secureFactory()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(
                            raw.rawPayload().getBytes(StandardCharsets.UTF_8)));
            doc.getDocumentElement().normalize();
            requireRoot(doc, "Document");
            requireElement(doc, "CorpActnConf");

            return new CaConfirmationEvent(
                    raw.messageId(),
                    requiredText(doc, "ConfRef"),
                    requiredText(doc, "ISIN"),
                    requiredText(doc, "EvtTp"),
                    requiredText(doc, "SttlmDt"),
                    new BigDecimal(requiredText(doc, "Amt")),
                    requiredAttr(doc, "Amt", "Ccy"),
                    requiredText(doc, "AcctId"),
                    new BigDecimal(requiredText(doc, "Qty")),
                    requiredText(doc, "Sts"),
                    "seev.036",
                    Instant.now()
            );
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to parse seev.036 messageId=" + raw.messageId() + ": " + e.getMessage(), e);
        }
    }

    private DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private void requireRoot(Document doc, String expectedRoot) {
        if (doc.getDocumentElement() == null
                || !expectedRoot.equals(doc.getDocumentElement().getTagName())) {
            throw new IllegalArgumentException("Missing required root element " + expectedRoot);
        }
    }

    private Element requireElement(Document doc, String tag) {
        if (doc.getElementsByTagName(tag).item(0) instanceof Element element) {
            return element;
        }
        throw new IllegalArgumentException("Missing required element " + tag);
    }

    private String requiredText(Document doc, String tag) {
        String value = requireElement(doc, tag).getTextContent().trim();
        if (!value.isEmpty()) {
            return value;
        }
        throw new IllegalArgumentException("Blank required element " + tag);
    }

    private String requiredAttr(Document doc, String tag, String attribute) {
        String value = requireElement(doc, tag).getAttribute(attribute).trim();
        if (!value.isEmpty()) {
            return value;
        }
        throw new IllegalArgumentException("Blank required attribute " + tag + "@" + attribute);
    }
}
