package com.routemind.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.routemind.common.ConflictException;
import com.routemind.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicles;

    @InjectMocks
    private VehicleService service;

    @Test
    void createNormalizesPlateToUpperCase() {
        when(vehicles.existsByPlateNumber("ABC-123")).thenReturn(false);
        when(vehicles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Vehicle created = service.create("abc-123", "Ford Transit", new BigDecimal("1200.50"), null);

        assertThat(created.getPlateNumber()).isEqualTo("ABC-123");
        assertThat(created.getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
    }

    @Test
    void createDuplicatePlateThrowsConflict() {
        when(vehicles.existsByPlateNumber("ABC-123")).thenReturn(true);

        assertThatThrownBy(() -> service.create("ABC-123", "Ford Transit", new BigDecimal("1200.50"), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ABC-123");
    }

    @Test
    void getMissingVehicleThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(vehicles.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void changeStatusOfOnRouteVehicleThrowsConflict() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle("ABC-123", "Ford Transit", new BigDecimal("1200.50"), null);
        vehicle.setStatus(VehicleStatus.ON_ROUTE);
        when(vehicles.findById(id)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.changeStatus(id, VehicleStatus.MAINTENANCE))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateToBrokenDownThrowsConflict() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle("ABC-123", "Ford Transit", new BigDecimal("1200.50"), null);
        when(vehicles.findById(id)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.update(id, "ABC-123", "Ford Transit",
                new BigDecimal("1200.50"), VehicleStatus.BROKEN_DOWN, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("/api/v1/incidents");
    }

    @Test
    void changeStatusToBrokenDownThrowsConflict() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle("ABC-123", "Ford Transit", new BigDecimal("1200.50"), null);
        when(vehicles.findById(id)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.changeStatus(id, VehicleStatus.BROKEN_DOWN))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("/api/v1/incidents");
    }

    @Test
    void changeStatusOfBrokenDownVehicleThrowsConflict() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle("ABC-123", "Ford Transit", new BigDecimal("1200.50"), null);
        vehicle.setStatus(VehicleStatus.BROKEN_DOWN);
        when(vehicles.findById(id)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.changeStatus(id, VehicleStatus.AVAILABLE))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("resolve");
    }

    @Test
    void deleteOnRouteVehicleThrowsConflict() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle("ABC-123", "Ford Transit", new BigDecimal("1200.50"), null);
        vehicle.setStatus(VehicleStatus.ON_ROUTE);
        when(vehicles.findById(id)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteAvailableVehicleDeletes() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = new Vehicle("ABC-123", "Ford Transit", new BigDecimal("1200.50"), null);
        when(vehicles.findById(id)).thenReturn(Optional.of(vehicle));

        service.delete(id);

        verify(vehicles).delete(vehicle);
    }
}
