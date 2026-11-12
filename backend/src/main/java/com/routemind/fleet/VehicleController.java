package com.routemind.fleet;

import com.routemind.common.PagedResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vehicle endpoints. Controllers only translate HTTP to service calls and
 * service results to DTOs; all rules live in VehicleService.
 */
@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleService vehicles;

    public VehicleController(VehicleService vehicles) {
        this.vehicles = vehicles;
    }

    @GetMapping
    PagedResponse<VehicleResponse> list(@RequestParam(required = false) VehicleStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PagedResponse.from(vehicles.list(status, pageable), VehicleResponse::from);
    }

    @GetMapping("/{id}")
    VehicleResponse get(@PathVariable UUID id) {
        return VehicleResponse.from(vehicles.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    VehicleResponse create(@Valid @RequestBody VehicleRequest request) {
        Vehicle created = vehicles.create(request.plateNumber(), request.model(),
                request.capacityKg(), request.depotId());
        return VehicleResponse.from(created);
    }

    @PutMapping("/{id}")
    VehicleResponse update(@PathVariable UUID id, @Valid @RequestBody VehicleUpdateRequest request) {
        Vehicle updated = vehicles.update(id, request.plateNumber(), request.model(),
                request.capacityKg(), request.status(), request.depotId());
        return VehicleResponse.from(updated);
    }

    @PatchMapping("/{id}/status")
    VehicleResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return VehicleResponse.from(vehicles.changeStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        vehicles.delete(id);
    }
}
