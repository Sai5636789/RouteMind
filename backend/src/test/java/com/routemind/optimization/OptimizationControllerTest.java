package com.routemind.optimization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.routemind.config.SecurityConfig;
import com.routemind.auth.JwtService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OptimizationController.class)
@Import(SecurityConfig.class)
// Authentication is exercised by HealthControllerTest (protected
// paths answer 401); these tests run as an authenticated caller
// so they verify the controller behaviour itself.
@WithMockUser
class OptimizationControllerTest {

    // SecurityConfig builds its JWT filter from JwtService; the mock
    // keeps the slice focused on the controller, not on token parsing.
    @MockitoBean
    private JwtService jwtService;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OptimizationService optimization;

    private static final String BODY = """
            {"depotLatitude":29.6516,"depotLongitude":-82.3248,
             "vehicles":[{"id":"%s","plateNumber":"FL-001","capacityKg":100}],
             "orders":[{"id":"%s","latitude":29.6516,"longitude":-82.3048,"weightKg":20}]}""";

    private OptimizationResult result() {
        UUID vehicleId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        return new OptimizationResult(
                "STRAIGHT_LINE", "OPTIMAL_OR_IMPROVED", 120,
                List.of(new OptimizationResult.OptimizedRoute(
                        vehicleId, "FL-001", List.of(orderId), 4200.0, 378.0, 20.0)),
                List.of(), 4200.0, 378.0, 20.0);
    }

    @Test
    void optimizeReturnsRoutes() throws Exception {
        OptimizationResult expected = result();
        when(optimization.optimize(any())).thenReturn(expected);
        String body = BODY.formatted(UUID.randomUUID(), UUID.randomUUID());

        mvc.perform(post("/api/v1/optimization/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matrixSource").value("STRAIGHT_LINE"))
                .andExpect(jsonPath("$.routes[0].plateNumber").value("FL-001"))
                .andExpect(jsonPath("$.routes[0].totalDistanceMetres").value(4200.0))
                .andExpect(jsonPath("$.totalDurationSeconds").value(378.0));
    }

    @Test
    void optimizeCurrentDelegates() throws Exception {
        when(optimization.optimizeCurrent()).thenReturn(result());

        mvc.perform(post("/api/v1/optimization/optimize-current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matrixSource").value("STRAIGHT_LINE"));
    }

    @Test
    void compareReturnsOptimizedAndBaseline() throws Exception {
        OptimizationResult expected = result();
        when(optimization.compare(any()))
                .thenReturn(new OptimizationService.ComparisonResult(expected, expected, 500.0, 45.0));
        String body = BODY.formatted(UUID.randomUUID(), UUID.randomUUID());

        mvc.perform(post("/api/v1/optimization/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedDistanceMetres").value(500.0))
                .andExpect(jsonPath("$.optimized.matrixSource").value("STRAIGHT_LINE"))
                .andExpect(jsonPath("$.baseline.matrixSource").value("STRAIGHT_LINE"));
    }

    @Test
    void compareCurrentDelegates() throws Exception {
        OptimizationResult expected = result();
        when(optimization.compareCurrent())
                .thenReturn(new OptimizationService.ComparisonResult(expected, expected, 0.0, 0.0));

        mvc.perform(post("/api/v1/optimization/compare-current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedDistanceMetres").value(0.0));
    }

    @Test
    void optimizeWithNoVehiclesIsBadRequest() throws Exception {
        String body = """
                {"depotLatitude":29.6516,"depotLongitude":-82.3248,"vehicles":[],
                 "orders":[{"id":"%s","latitude":29.6516,"longitude":-82.3048,"weightKg":20}]}"""
                .formatted(UUID.randomUUID());

        mvc.perform(post("/api/v1/optimization/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void optimizeWithOutOfRangeCoordinateIsBadRequest() throws Exception {
        String body = BODY.formatted(UUID.randomUUID(), UUID.randomUUID())
                .replace("29.6516", "91.0");

        mvc.perform(post("/api/v1/optimization/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
