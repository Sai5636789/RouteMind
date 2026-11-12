package com.routemind.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.routemind.common.ConflictException;
import com.routemind.common.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private DriverRepository drivers;

    @InjectMocks
    private DriverService service;

    @Test
    void createNormalizesLicenseToUpperCase() {
        when(drivers.existsByLicenseNumber("DL-884120")).thenReturn(false);
        when(drivers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Driver created = service.create("Asha", "Patel", "dl-884120", "+1 352-555-0140");

        assertThat(created.getLicenseNumber()).isEqualTo("DL-884120");
        assertThat(created.getStatus()).isEqualTo(DriverStatus.ACTIVE);
    }

    @Test
    void createDuplicateLicenseThrowsConflict() {
        when(drivers.existsByLicenseNumber("DL-884120")).thenReturn(true);

        assertThatThrownBy(() -> service.create("Asha", "Patel", "DL-884120", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("DL-884120");
    }

    @Test
    void getMissingDriverThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(drivers.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(ResourceNotFoundException.class);
    }
}
