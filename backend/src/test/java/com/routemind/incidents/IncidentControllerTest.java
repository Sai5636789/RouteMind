package com.routemind.incidents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.routemind.auth.JwtService;
import com.routemind.common.ConflictException;
import com.routemind.common.ResourceNotFoundException;
import com.routemind.config.SecurityConfig;
import com.routemind.fleet.Vehicle;
import com.routemind.fleet.VehicleStatus;
import com.routemind.optimization.OptimizationResult;
import com.routemind.orders.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Authentication is exercised by HealthControllerTest (protected paths answer
// 401); these tests run as an authenticated caller so they verify the
// controller behaviour itself.
@WebMvcTest(IncidentController.class)
@Import(SecurityConfig.class)
@WithMockUser
class IncidentControllerTest {

    // SecurityConfig builds its JWT filter from JwtService; the mock
    // keeps the slice focused on the controller, not on token parsing.
    @MockitoBean
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentService incidents;

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final UUID VEHICLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REPLACEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID INCIDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID DRIVER_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private static void setId(Object entity, UUID id) {
        try {
            var idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private IncidentService.IncidentDetail detail() {
        Vehicle vehicle = new Vehicle("ABC-123", "Van", new BigDecimal("500"), null);
        vehicle.setStatus(VehicleStatus.BROKEN_DOWN);
        setId(vehicle, VEHICLE_ID);
        Incident incident = new Incident(VEHICLE_ID, "Engine failure", NOW);
        setId(incident, INCIDENT_ID);
        IncidentOrder entry = new IncidentOrder(INCIDENT_ID, ORDER_ID, OrderStatus.IN_TRANSIT);
        return new IncidentService.IncidentDetail(incident, vehicle, List.of(entry), Map.of());
    }

    private IncidentService.RerouteResult rerouteResult() {
        Vehicle vehicle = new Vehicle("ABC-123", "Van", new BigDecimal("500"), null);
        vehicle.setStatus(VehicleStatus.BROKEN_DOWN);
        setId(vehicle, VEHICLE_ID);
        Incident incident = new Incident(VEHICLE_ID, "Engine failure", NOW);
        setId(incident, INCIDENT_ID);
        OptimizationResult plan = new OptimizationResult("STRAIGHT_LINE", "OPTIMAL_OR_IMPROVED", 42,
                List.of(new OptimizationResult.OptimizedRoute(REPLACEMENT_ID, "DEF-456",
                        List.of(ORDER_ID), 4200, 378, 10)),
                List.of(), 4200, 378, 10);
        return new IncidentService.RerouteResult(incident, vehicle, plan,
                List.of(new IncidentService.RerouteAssignment(ORDER_ID, "Ana", REPLACEMENT_ID, "DEF-456",
                        DRIVER_ID)),
                List.of());
    }

    @Test
    void reportBreakdownReturns201WithAffectedOrders() throws Exception {
        when(incidents.reportBreakdown(any(), any())).thenReturn(detail());

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":\"" + VEHICLE_ID + "\",\"reason\":\"Engine failure\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(INCIDENT_ID.toString()))
                .andExpect(jsonPath("$.vehicleId").value(VEHICLE_ID.toString()))
                .andExpect(jsonPath("$.vehiclePlateNumber").value("ABC-123"))
                .andExpect(jsonPath("$.vehicleStatus").value("BROKEN_DOWN"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.affectedOrders[0].orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.affectedOrders[0].previousStatus").value("IN_TRANSIT"));
    }

    @Test
    void reportBreakdownMissingVehicleReturns404() throws Exception {
        when(incidents.reportBreakdown(any(), any()))
                .thenThrow(new ResourceNotFoundException("Vehicle", VEHICLE_ID));

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":\"" + VEHICLE_ID + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void reportBreakdownAlreadyBrokenDownReturns409() throws Exception {
        when(incidents.reportBreakdown(any(), any()))
                .thenThrow(new ConflictException("Vehicle " + VEHICLE_ID + " is already broken down"));

        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehicleId\":\"" + VEHICLE_ID + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void reportBreakdownWithoutVehicleIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Engine failure\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rerouteReturnsPlanAndAssignments() throws Exception {
        when(incidents.reroute(INCIDENT_ID)).thenReturn(rerouteResult());

        mockMvc.perform(post("/api/v1/incidents/" + INCIDENT_ID + "/reroute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(INCIDENT_ID.toString()))
                .andExpect(jsonPath("$.matrixSource").value("STRAIGHT_LINE"))
                .andExpect(jsonPath("$.optimization.totalDistanceMetres").value(4200.0))
                .andExpect(jsonPath("$.assignments[0].orderId").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.assignments[0].plateNumber").value("DEF-456"))
                .andExpect(jsonPath("$.stillWaitingOrderIds").isEmpty());
    }

    @Test
    void resolveReturnsResolvedIncident() throws Exception {
        IncidentService.IncidentDetail resolved = detail();
        resolved.incident().setStatus(IncidentStatus.RESOLVED);
        when(incidents.resolve(INCIDENT_ID)).thenReturn(resolved);

        mockMvc.perform(post("/api/v1/incidents/" + INCIDENT_ID + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void getMissingIncidentReturns404() throws Exception {
        when(incidents.get(INCIDENT_ID)).thenThrow(new ResourceNotFoundException("Incident", INCIDENT_ID));

        mockMvc.perform(get("/api/v1/incidents/" + INCIDENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listReturnsPage() throws Exception {
        IncidentService.IncidentDetail one = detail();
        when(incidents.list(any(), any())).thenReturn(new PageImpl<>(List.of(one.incident())));
        when(incidents.get(INCIDENT_ID)).thenReturn(one);

        mockMvc.perform(get("/api/v1/incidents?status=OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(INCIDENT_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
