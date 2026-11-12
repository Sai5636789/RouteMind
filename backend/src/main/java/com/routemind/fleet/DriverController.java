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

/** Driver endpoints. Controllers only translate HTTP to service calls and service results to DTOs. */
@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverService drivers;

    public DriverController(DriverService drivers) {
        this.drivers = drivers;
    }

    @GetMapping
    PagedResponse<DriverResponse> list(@RequestParam(required = false) DriverStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PagedResponse.from(drivers.list(status, pageable), DriverResponse::from);
    }

    @GetMapping("/{id}")
    DriverResponse get(@PathVariable UUID id) {
        return DriverResponse.from(drivers.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DriverResponse create(@Valid @RequestBody DriverRequest request) {
        Driver created = drivers.create(request.firstName(), request.lastName(),
                request.licenseNumber(), request.phone());
        return DriverResponse.from(created);
    }

    @PutMapping("/{id}")
    DriverResponse update(@PathVariable UUID id, @Valid @RequestBody DriverUpdateRequest request) {
        Driver updated = drivers.update(id, request.firstName(), request.lastName(),
                request.licenseNumber(), request.phone(), request.status());
        return DriverResponse.from(updated);
    }

    @PatchMapping("/{id}/status")
    DriverResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody DriverStatusRequest request) {
        return DriverResponse.from(drivers.changeStatus(id, request.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        drivers.delete(id);
    }
}
