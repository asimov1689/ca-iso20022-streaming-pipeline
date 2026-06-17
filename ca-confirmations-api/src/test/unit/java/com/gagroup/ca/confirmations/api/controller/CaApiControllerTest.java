package com.gagroup.ca.confirmations.api.controller;

import com.gagroup.ca.confirmations.api.entity.CaSettledEventReadEntity;
import com.gagroup.ca.confirmations.api.repository.CaSettledEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaApiController.class)
class CaApiControllerTest {

    @Autowired MockMvc mvc;
    @MockBean  CaSettledEventRepository repo;

    private CaSettledEventReadEntity sampleEntity() {
        var e = new CaSettledEventReadEntity();
        e.setMessageId("MSG-001");
        e.setIsin("CH0012221716");
        e.setEventType("DVCA");
        e.setConfirmationRef("CONF-001");
        e.setSecurityName("Arthur Dent Holdings");
        e.setMarketOfListing("GA Exchange");
        return e;
    }

    @Test
    void listNoParamsShouldReturn200WithAllSettledEvents() throws Exception {
        // Arrange
        when(repo.findAll()).thenReturn(List.of(sampleEntity()));

        // Act & Assert
        mvc.perform(get("/api/v1/settled-confirmations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isin").value("CH0012221716"))
                .andExpect(jsonPath("$[0].eventType").value("DVCA"))
                .andExpect(jsonPath("$[0].confirmationRef").value("CONF-001"));
    }

    @Test
    void getExistingMessageIdShouldReturn200WithBody() throws Exception {
        // Arrange
        when(repo.findById("MSG-001")).thenReturn(Optional.of(sampleEntity()));

        // Act & Assert
        mvc.perform(get("/api/v1/settled-confirmations/MSG-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isin").value("CH0012221716"))
                .andExpect(jsonPath("$.securityName").value("Arthur Dent Holdings"));
    }

    @Test
    void getUnknownMessageIdShouldReturn404() throws Exception {
        // Arrange
        when(repo.findById("NO-SUCH")).thenReturn(Optional.empty());

        // Act & Assert
        mvc.perform(get("/api/v1/settled-confirmations/NO-SUCH"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listByIsinShouldDelegateToRepositoryFindByIsin() throws Exception {
        // Arrange
        when(repo.findByIsin("CH0012221716")).thenReturn(List.of(sampleEntity()));

        // Act & Assert
        mvc.perform(get("/api/v1/settled-confirmations").param("isin", "CH0012221716"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].isin").value("CH0012221716"));

        verify(repo).findByIsin("CH0012221716");
        verify(repo, never()).findAll();
    }

    @Test
    void listByIsinAndEventTypeShouldDelegateToFindByIsinAndEventType() throws Exception {
        // Arrange
        when(repo.findByIsinAndEventType("CH0012221716", "DVCA")).thenReturn(List.of(sampleEntity()));

        // Act & Assert
        mvc.perform(get("/api/v1/settled-confirmations")
                        .param("isin", "CH0012221716")
                        .param("eventType", "DVCA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("DVCA"));

        verify(repo).findByIsinAndEventType("CH0012221716", "DVCA");
        verify(repo, never()).findByIsin(any());
    }

    @Test
    void listByEventTypeShouldDelegateToFindByEventType() throws Exception {
        // Arrange
        when(repo.findByEventType("DVCA")).thenReturn(List.of(sampleEntity()));

        // Act & Assert
        mvc.perform(get("/api/v1/settled-confirmations").param("eventType", "DVCA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("DVCA"));

        verify(repo).findByEventType("DVCA");
    }

    @Test
    void listByAccountIdShouldDelegateToFindByAccountId() throws Exception {
        // Arrange
        when(repo.findByAccountId("ACC-001")).thenReturn(List.of(sampleEntity()));

        // Act & Assert
        mvc.perform(get("/api/v1/settled-confirmations").param("accountId", "ACC-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(repo).findByAccountId("ACC-001");
    }

    @Test
    void settlementRangeShouldDelegateToFindBySettlementDateBetween() throws Exception {
        // Arrange
        when(repo.findBySettlementDateBetween("20261201", "20261231"))
                .thenReturn(List.of(sampleEntity()));

        // Act & Assert
        mvc.perform(get("/api/v1/settled-confirmations/settlement-range")
                        .param("from", "20261201")
                        .param("to", "20261231"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(repo).findBySettlementDateBetween("20261201", "20261231");
    }

    @Test
    void healthShouldReturnUpStatusAndServiceName() throws Exception {
        // Arrange
        when(repo.count()).thenReturn(42L);

        // Act & Assert
        mvc.perform(get("/api/v1/settled-confirmations/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("ca-confirmations-api"))
                .andExpect(jsonPath("$['master-table-count']").value("42"));
    }
}
