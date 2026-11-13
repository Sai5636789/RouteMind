package com.routemind.tracking;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.routemind.config.SecurityConfig;
import com.routemind.auth.JwtService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TrackingController.class)
@Import(SecurityConfig.class)
// Authentication is exercised by HealthControllerTest (protected
// paths answer 401); these tests run as an authenticated caller
// so they verify the controller behaviour itself.
@WithMockUser
class TrackingControllerTest {

    // SecurityConfig builds its JWT filter from JwtService; the mock
    // keeps the slice focused on the controller, not on token parsing.
    @MockitoBean
    private JwtService jwtService;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private VehiclePositionStore positions;

    private static final UUID VEHICLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private VehiclePosition position() {
        return new VehiclePosition(VEHICLE_ID, 29.66, -82.31, 40.0, 45.0, null,
                Instant.parse("2026-11-13T12:00:00Z"));
    }

    @Test
    void latestReturnsAllTrackedVehicles() throws Exception {
        when(positions.findAll()).thenReturn(List.of(position()));

        mvc.perform(get("/api/v1/tracking/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleId").value(VEHICLE_ID.toString()))
                .andExpect(jsonPath("$[0].latitude").value(29.66))
                .andExpect(jsonPath("$[0].speedKmh").value(40.0));
    }

    @Test
    void latestForUnknownVehicleIs404() throws Exception {
        when(positions.findByVehicleId(VEHICLE_ID)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/tracking/vehicles/" + VEHICLE_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void nearbyRejectsOutOfRangeCoordinates() throws Exception {
        mvc.perform(get("/api/v1/tracking/vehicles/nearby")
                        .param("latitude", "91")
                        .param("longitude", "-82.3"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nearbyReturnsPositionsWithinRadius() throws Exception {
        when(positions.findNearby(29.6516, -82.3248, 5)).thenReturn(List.of(position()));

        mvc.perform(get("/api/v1/tracking/vehicles/nearby")
                        .param("latitude", "29.6516")
                        .param("longitude", "-82.3248")
                        .param("radiusKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vehicleId").value(VEHICLE_ID.toString()));
    }
}
