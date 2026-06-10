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
    void lookupKnownIsinNestleReturnsCorrectRefData() throws Exception {
        // Arrange
        String isin = "CH0012221716";

        // Act & Assert
        mvc.perform(get("/cobol/reference/{isin}", isin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityName").value("Nestle SA"))
                .andExpect(jsonPath("$.issuerLei").value("PBLD0EJDB5FWOLXP3B76"))
                .andExpect(jsonPath("$.marketOfListing").value("GA Exchange"))
                .andExpect(jsonPath("$.settleCcy").value("CHF"));
    }

    @Test
    void lookupKnownIsinAbbReturnsCorrectRefData() throws Exception {
        // Arrange
        String isin = "CH0012255580";

        // Act & Assert
        mvc.perform(get("/cobol/reference/{isin}", isin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityName").value("ABB Ltd"))
                .andExpect(jsonPath("$.issuerLei").value("529900WOYRUK9N8J1T75"))
                .andExpect(jsonPath("$.marketOfListing").value("GA Exchange"))
                .andExpect(jsonPath("$.settleCcy").value("CHF"));
    }

    @Test
    void lookupKnownIsinRocheReturnsCorrectRefData() throws Exception {
        // Arrange
        String isin = "CH0038863350";

        // Act & Assert
        mvc.perform(get("/cobol/reference/{isin}", isin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityName").value("Roche Holding AG"))
                .andExpect(jsonPath("$.issuerLei").value("HMTNLVUNV8VXAIZQ9B87"))
                .andExpect(jsonPath("$.marketOfListing").value("GA Exchange"))
                .andExpect(jsonPath("$.settleCcy").value("CHF"));
    }

    @Test
    void lookupKnownIsinWrthlyReturnsCorrectRefData() throws Exception {
        // Arrange
        String isin = "CH0012032048";

        // Act & Assert
        mvc.perform(get("/cobol/reference/{isin}", isin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityName").value("WRTHLY Group AG"))
                .andExpect(jsonPath("$.issuerLei").value("BFM8T61CT2L1QCEMIK50"))
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
