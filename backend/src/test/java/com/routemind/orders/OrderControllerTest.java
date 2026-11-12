package com.routemind.orders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.routemind.common.ConflictException;
import com.routemind.config.SecurityConfig;
import java.math.BigDecimal;
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

// See VehicleControllerTest: authentication itself is covered by
// HealthControllerTest; these run as an authenticated caller.
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
@WithMockUser
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orders;

    private static final String VALID_BODY = "{\"customerName\":\"Green Pharmacy\",\"deliveryAddress\":"
            + "\"1200 Main St, Gainesville, FL\",\"latitude\":29.6516,\"longitude\":-82.3248,\"weightKg\":12.5}";

    private static Order sampleOrder() {
        return new Order("Green Pharmacy", "1200 Main St, Gainesville, FL",
                29.6516, -82.3248, new BigDecimal("12.5"));
    }

    @Test
    void listReturnsPage() throws Exception {
        when(orders.list(any(), any())).thenReturn(new PageImpl<>(List.of(sampleOrder())));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerName").value("Green Pharmacy"))
                .andExpect(jsonPath("$.content[0].status").value("CREATED"));
    }

    @Test
    void createReturns201() throws Exception {
        when(orders.create(eq("Green Pharmacy"), any(), eq(29.6516), eq(-82.3248), eq(new BigDecimal("12.5"))))
                .thenReturn(sampleOrder());

        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void createWithLatitudeOutOfRangeReturns400() throws Exception {
        String invalid = "{\"customerName\":\"Green Pharmacy\",\"deliveryAddress\":\"1200 Main St\","
                + "\"latitude\":120.0,\"longitude\":-82.3248,\"weightKg\":12.5}";
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assignReturnsAssignedOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID vehicleId = UUID.randomUUID();
        UUID driverId = UUID.randomUUID();
        Order assigned = sampleOrder();
        assigned.setAssignedVehicleId(vehicleId);
        assigned.setAssignedDriverId(driverId);
        assigned.setStatus(OrderStatus.ASSIGNED);
        when(orders.assign(orderId, vehicleId, driverId)).thenReturn(assigned);

        String body = "{\"vehicleId\":\"" + vehicleId + "\",\"driverId\":\"" + driverId + "\"}";
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/assign")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedVehicleId").value(vehicleId.toString()));
    }

    @Test
    void illegalTransitionReturns409() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orders.deliver(orderId))
                .thenThrow(new ConflictException("Order " + orderId + " cannot move from CREATED to DELIVERED"));

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/deliver"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
