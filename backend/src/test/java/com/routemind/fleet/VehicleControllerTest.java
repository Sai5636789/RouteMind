package com.routemind.fleet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.routemind.common.ConflictException;
import com.routemind.common.ResourceNotFoundException;
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

// Authentication is exercised by HealthControllerTest (protected paths answer
// 401); these tests run as an authenticated caller so they verify the
// controller behaviour itself.
@WebMvcTest(VehicleController.class)
@Import(SecurityConfig.class)
@WithMockUser
class VehicleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleService vehicles;

    private static final String VALID_BODY =
            "{\"plateNumber\":\"ABC-123\",\"model\":\"Ford Transit\",\"capacityKg\":1200.50}";

    @Test
    void listReturnsPage() throws Exception {
        Vehicle vehicle = new Vehicle("ABC-123", "Ford Transit", new BigDecimal("1200.50"), null);
        when(vehicles.list(any(), any())).thenReturn(new PageImpl<>(List.of(vehicle)));

        mockMvc.perform(get("/api/v1/vehicles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].plateNumber").value("ABC-123"))
                .andExpect(jsonPath("$.content[0].status").value("AVAILABLE"));
    }

    @Test
    void getReturnsVehicle() throws Exception {
        UUID id = UUID.randomUUID();
        when(vehicles.get(id)).thenReturn(new Vehicle("ABC-123", "Ford Transit", new BigDecimal("1200.50"), null));

        mockMvc.perform(get("/api/v1/vehicles/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("Ford Transit"));
    }

    @Test
    void getMissingVehicleReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(vehicles.get(id)).thenThrow(new ResourceNotFoundException("Vehicle", id));

        mockMvc.perform(get("/api/v1/vehicles/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createReturns201() throws Exception {
        when(vehicles.create(eq("ABC-123"), eq("Ford Transit"), eq(new BigDecimal("1200.50")), any()))
                .thenReturn(new Vehicle("ABC-123", "Ford Transit", new BigDecimal("1200.50"), null));

        mockMvc.perform(post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plateNumber").value("ABC-123"));
    }

    @Test
    void createWithInvalidBodyReturns400() throws Exception {
        String invalid = "{\"plateNumber\":\"!\",\"model\":\"\",\"capacityKg\":-5}";
        mockMvc.perform(post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createDuplicatePlateReturns409() throws Exception {
        when(vehicles.create(any(), any(), any(), any()))
                .thenThrow(new ConflictException("A vehicle with plate number ABC-123 already exists"));

        mockMvc.perform(post("/api/v1/vehicles").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateMissingVehicleReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(vehicles.update(eq(id), any(), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Vehicle", id));

        String body = "{\"plateNumber\":\"ABC-123\",\"model\":\"Ford Transit\",\"capacityKg\":1200.50,"
                + "\"status\":\"AVAILABLE\"}";
        mockMvc.perform(put("/api/v1/vehicles/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/vehicles/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}
