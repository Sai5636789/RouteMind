package com.routemind.depot;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.routemind.config.SecurityConfig;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// No @WithMockUser on purpose: GET /depots is temporarily public for the
// local demo, and this test pins that behaviour.
@WebMvcTest(DepotController.class)
@Import(SecurityConfig.class)
class DepotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepotRepository depots;

    private static final Depot GAINESVILLE = new Depot(UUID.randomUUID(), "Gainesville Depot",
            "Gainesville, FL", 29.6516, -82.3248, Instant.now(), Instant.now());

    @Test
    void listReturnsDepotsWithoutAuthentication() throws Exception {
        when(depots.findAll()).thenReturn(List.of(GAINESVILLE));

        mockMvc.perform(get("/api/v1/depots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Gainesville Depot"))
                .andExpect(jsonPath("$[0].latitude").value(29.6516))
                .andExpect(jsonPath("$[0].longitude").value(-82.3248));
    }

    @Test
    void getReturnsOneDepot() throws Exception {
        when(depots.findById(GAINESVILLE.id())).thenReturn(Optional.of(GAINESVILLE));

        mockMvc.perform(get("/api/v1/depots/" + GAINESVILLE.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gainesville Depot"));
    }

    @Test
    void getMissingDepotReturns404() throws Exception {
        when(depots.findById(GAINESVILLE.id())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/depots/" + GAINESVILLE.id()))
                .andExpect(status().isNotFound());
    }
}
