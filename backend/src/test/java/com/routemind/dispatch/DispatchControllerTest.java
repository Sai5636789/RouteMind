package com.routemind.dispatch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.routemind.auth.JwtService;
import com.routemind.common.ResourceNotFoundException;
import com.routemind.config.SecurityConfig;
import com.routemind.orders.OrderStatus;
import java.time.Instant;
import java.util.List;
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
@WebMvcTest(DispatchController.class)
@Import(SecurityConfig.class)
@WithMockUser
class DispatchControllerTest {

    @MockitoBean
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DispatchService dispatch;

    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");
    private static final UUID VEHICLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DRIVER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ORDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID PLAN_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID STOP_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");

    private DispatchPlanResponse planResponse(DispatchPlanStatus status) {
        return new DispatchPlanResponse(PLAN_ID, status, "STRAIGHT_LINE", 4200, 378, 20, List.of(),
                List.of(new DispatchPlanResponse.RouteView(VEHICLE_ID, "ABC-123", DRIVER_ID, "Ana Reyes",
                        4200, 378, 20,
                        List.of(new DispatchPlanResponse.StopView(STOP_ID, ORDER_ID, "Ana", "1 Main St", 0,
                                DispatchStopStatus.PENDING, OrderStatus.ASSIGNED, false)))),
                null, NOW, NOW);
    }

    @Test
    void listPlans() throws Exception {
        when(dispatch.list(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/dispatch/plans")).andExpect(status().isOk());
    }

    @Test
    void getPlan() throws Exception {
        when(dispatch.get(PLAN_ID)).thenReturn(planResponse(DispatchPlanStatus.DRAFT));

        mockMvc.perform(get("/api/v1/dispatch/plans/" + PLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.routes[0].stops[0].customerName").value("Ana"));
    }

    @Test
    void getUnknownPlanIs404() throws Exception {
        when(dispatch.get(PLAN_ID)).thenThrow(new ResourceNotFoundException("Dispatch plan", PLAN_ID));

        mockMvc.perform(get("/api/v1/dispatch/plans/" + PLAN_ID)).andExpect(status().isNotFound());
    }

    @Test
    void createPlan() throws Exception {
        when(dispatch.createPlan(any())).thenReturn(
                new DispatchService.CreatedPlan(planResponse(DispatchPlanStatus.DRAFT), List.of()));

        mockMvc.perform(post("/api/v1/dispatch/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverAssignments\":{}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void approvePlan() throws Exception {
        when(dispatch.approve(PLAN_ID)).thenReturn(planResponse(DispatchPlanStatus.APPROVED));

        mockMvc.perform(post("/api/v1/dispatch/plans/" + PLAN_ID + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void driverRoute() throws Exception {
        when(dispatch.driverRoute(DRIVER_ID)).thenReturn(List.of(
                new DispatchPlanResponse.DriverStopView(PLAN_ID, STOP_ID, VEHICLE_ID, "ABC-123", ORDER_ID,
                        "Ana", "1 Main St", 29.65, -82.32, 0, DispatchStopStatus.PENDING,
                        OrderStatus.ASSIGNED)));

        mockMvc.perform(get("/api/v1/dispatch/driver/" + DRIVER_ID + "/route"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerName").value("Ana"));
    }

    @Test
    void deliveryUpdate() throws Exception {
        when(dispatch.updateDelivery(any(), any())).thenReturn(planResponse(DispatchPlanStatus.APPROVED));

        mockMvc.perform(post("/api/v1/dispatch/stops/" + STOP_ID + "/delivery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }
}
