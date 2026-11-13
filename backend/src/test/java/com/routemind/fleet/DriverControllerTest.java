package com.routemind.fleet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.routemind.common.ConflictException;
import com.routemind.auth.JwtService;
import com.routemind.config.SecurityConfig;
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
@WebMvcTest(DriverController.class)
@Import(SecurityConfig.class)
@WithMockUser
class DriverControllerTest {

    // SecurityConfig builds its JWT filter from JwtService; the mock
    // keeps the slice focused on the controller, not on token parsing.
    @MockitoBean
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DriverService drivers;

    private static final String VALID_BODY =
            "{\"firstName\":\"Asha\",\"lastName\":\"Patel\",\"licenseNumber\":\"DL-884120\",\"phone\":\"+1 352-555-0140\"}";

    @Test
    void listReturnsPage() throws Exception {
        when(drivers.list(any(), any()))
                .thenReturn(new PageImpl<>(List.of(new Driver("Asha", "Patel", "DL-884120", "+1 352-555-0140"))));

        mockMvc.perform(get("/api/v1/drivers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].licenseNumber").value("DL-884120"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void createReturns201() throws Exception {
        when(drivers.create(eq("Asha"), eq("Patel"), eq("DL-884120"), any()))
                .thenReturn(new Driver("Asha", "Patel", "DL-884120", "+1 352-555-0140"));

        mockMvc.perform(post("/api/v1/drivers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Asha"));
    }

    @Test
    void createWithInvalidBodyReturns400() throws Exception {
        String invalid = "{\"firstName\":\"\",\"lastName\":\"Patel\",\"licenseNumber\":\"x\"}";
        mockMvc.perform(post("/api/v1/drivers").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDuplicateLicenseReturns409() throws Exception {
        when(drivers.create(any(), any(), any(), any()))
                .thenThrow(new ConflictException("A driver with license number DL-884120 already exists"));

        mockMvc.perform(post("/api/v1/drivers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/drivers/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}
