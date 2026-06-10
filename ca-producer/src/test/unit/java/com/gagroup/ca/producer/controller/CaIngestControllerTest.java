package com.gagroup.ca.producer.controller;

import com.gagroup.ca.producer.service.CaProducerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaIngestController.class)
class CaIngestControllerTest {

    @Autowired MockMvc mvc;
    @MockBean CaProducerService service;

    @Test
    void postMt566ValidPayloadReturns202WithMessageId() throws Exception {
        when(service.publish(anyString(), eq("MT566"))).thenReturn("MSG-001");

        mvc.perform(post("/api/v1/ingest/mt566")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("CONF-001|CH0012221716|DVCA|20261231|2500.00|CHF|ACC-001|1000|SETT"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.messageId").value("MSG-001"))
                .andExpect(jsonPath("$.type").value("MT566"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void postSeev036ValidXmlReturns202WithMessageId() throws Exception {
        when(service.publish(anyString(), eq("seev.036"))).thenReturn("MSG-002");

        mvc.perform(post("/api/v1/ingest/seev036")
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<Document/>"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.messageId").value("MSG-002"))
                .andExpect(jsonPath("$.type").value("seev.036"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void postMt566ShouldDelegatePayloadToService() throws Exception {
        when(service.publish(anyString(), anyString())).thenReturn("MSG-003");
        String payload = "CONF-001|CH0012221716|DVCA|20261231|2500.00|CHF|ACC-001|1000|SETT";

        mvc.perform(post("/api/v1/ingest/mt566")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(payload))
                .andExpect(status().isAccepted());

        verify(service).publish(payload, "MT566");
    }
}
