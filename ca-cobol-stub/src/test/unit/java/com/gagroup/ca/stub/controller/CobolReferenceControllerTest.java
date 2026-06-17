package com.gagroup.ca.stub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CobolReferenceController.class)
class CobolReferenceControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void lookupKnownIsinArthurDentReturnsCorrectRefData() throws Exception {
        // Arrange
        String isin = "CH0012221716";

        // Act & Assert
        mvc.perform(get("/cobol/reference/{isin}", isin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityName").value("Arthur Dent Holdings"))
                .andExpect(jsonPath("$.issuerLei").value("ARTHURDENTLEI000001"))
                .andExpect(jsonPath("$.marketOfListing").value("GA Exchange"))
                .andExpect(jsonPath("$.settleCcy").value("CHF"));
    }

    @Test
    void lookupKnownIsinFordPrefectReturnsCorrectRefData() throws Exception {
        // Arrange
        String isin = "CH0012255580";

        // Act & Assert
        mvc.perform(get("/cobol/reference/{isin}", isin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityName").value("Ford Prefect Ltd"))
                .andExpect(jsonPath("$.issuerLei").value("FORDPREFECTLEI00001"))
                .andExpect(jsonPath("$.marketOfListing").value("GA Exchange"))
                .andExpect(jsonPath("$.settleCcy").value("CHF"));
    }

    @Test
    void lookupKnownIsinTrillianReturnsCorrectRefData() throws Exception {
        // Arrange
        String isin = "CH0038863350";

        // Act & Assert
        mvc.perform(get("/cobol/reference/{isin}", isin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityName").value("Trillian Astra PLC"))
                .andExpect(jsonPath("$.issuerLei").value("TRILLIANASTRALEI001"))
                .andExpect(jsonPath("$.marketOfListing").value("GA Exchange"))
                .andExpect(jsonPath("$.settleCcy").value("CHF"));
    }

    @Test
    void lookupKnownIsinMarvinReturnsCorrectRefData() throws Exception {
        // Arrange
        String isin = "CH0012032048";

        // Act & Assert
        mvc.perform(get("/cobol/reference/{isin}", isin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityName").value("Marvin Android Group"))
                .andExpect(jsonPath("$.issuerLei").value("MARVINANDROIDLEI001"))
                .andExpect(jsonPath("$.marketOfListing").value("GA Exchange"))
                .andExpect(jsonPath("$.settleCcy").value("CHF"));
    }

    @Test
    void lookupUnknownIsinReturnsUnknownDefaults() throws Exception {
        // Arrange
        String unknownIsin = "XX0000000000";

        // Act & Assert
        mvc.perform(get("/cobol/reference/{isin}", unknownIsin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityName").value("UNKNOWN"))
                .andExpect(jsonPath("$.issuerLei").value("N/A"))
                .andExpect(jsonPath("$.marketOfListing").value("UNKNOWN"))
                .andExpect(jsonPath("$.settleCcy").value("CHF"));
    }
}
