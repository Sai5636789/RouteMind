package com.routemind.routing;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.routemind.config.SecurityConfig;
import com.routemind.auth.JwtService;
import com.routemind.tracking.GeoPoint;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoutingController.class)
@Import(SecurityConfig.class)
// Authentication is exercised by HealthControllerTest (protected
// paths answer 401); these tests run as an authenticated caller
// so they verify the controller behaviour itself.
@WithMockUser
class RoutingControllerTest {

    // SecurityConfig builds its JWT filter from JwtService; the mock
    // keeps the slice focused on the controller, not on token parsing.
    @MockitoBean
    private JwtService jwtService;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private RoutingService routing;

    private static final String BODY = """
            {"points":[{"latitude":29.6516,"longitude":-82.3248},
            {"latitude":29.66,"longitude":-82.30}]}""";

    @Test
    void routeReturnsGeometryDistanceAndDuration() throws Exception {
        List<GeoPoint> geometry = List.of(new GeoPoint(29.6516, -82.3248), new GeoPoint(29.66, -82.30));
        when(routing.route(any())).thenReturn(new RoadRoute(geometry, 3053.2, 272.5));

        mvc.perform(post("/api/v1/routing/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geometry[0].latitude").value(29.6516))
                .andExpect(jsonPath("$.distanceMetres").value(3053.2))
                .andExpect(jsonPath("$.durationSeconds").value(272.5));
    }

    @Test
    void routeWithOnePointIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/routing/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\":[{\"latitude\":29.6516,\"longitude\":-82.3248}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void routeWithLatitudeOutOfRangeIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/routing/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\":[{\"latitude\":91,\"longitude\":-82.3248},"
                                + "{\"latitude\":29.66,\"longitude\":-82.30}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unavailableRouterIs503() throws Exception {
        when(routing.route(any())).thenThrow(new RoutingUnavailableException("Road router is down"));

        mvc.perform(post("/api/v1/routing/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Road router is down"));
    }

    @Test
    void matrixReturnsRowsInPointOrder() throws Exception {
        List<GeoPoint> points = List.of(new GeoPoint(29.6516, -82.3248), new GeoPoint(29.66, -82.30));
        when(routing.matrix(any())).thenReturn(new DistanceMatrix(points,
                List.of(List.of(0.0, 272.5), List.of(300.0, 0.0)),
                List.of(List.of(0.0, 3053.2), List.of(3200.0, 0.0))));

        mvc.perform(post("/api/v1/routing/matrix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationsSeconds[0][1]").value(272.5))
                .andExpect(jsonPath("$.distancesMetres[1][0]").value(3200.0));
    }

    @Test
    void statusReportsAvailability() throws Exception {
        when(routing.status()).thenReturn(new RoutingStatus(true, "http://osrm:5000", "driving"));

        mvc.perform(get("/api/v1/routing/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.profile").value("driving"));
    }
}
