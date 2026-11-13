package com.routemind.dispatch;

import com.routemind.common.PagedResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dispatch endpoints. All of them require the dispatcher login — there
 * is no public read here, and a driver view is the dispatcher looking at
 * a driver's route, not a separate driver login (that was never asked
 * for). Decisions are separate endpoints rather than a generic status
 * field, so an illegal move is a 409 with a reason.
 */
@RestController
@RequestMapping("/api/v1/dispatch")
public class DispatchController {

    private final DispatchService dispatch;

    public DispatchController(DispatchService dispatch) {
        this.dispatch = dispatch;
    }

    @GetMapping("/plans")
    PagedResponse<DispatchPlanResponse> list(@RequestParam(required = false) DispatchPlanStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PagedResponse.from(dispatch.list(status, pageable),
                plan -> dispatch.get(plan.getId()));
    }

    @GetMapping("/plans/{id}")
    DispatchPlanResponse get(@PathVariable UUID id) {
        return dispatch.get(id);
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    DispatchPlanResponse create(@RequestBody(required = false) CreatePlanRequest request) {
        Map<UUID, UUID> assignments = request == null ? Map.of()
                : request.driverAssignments() == null ? Map.of() : request.driverAssignments();
        return dispatch.createPlan(assignments).plan();
    }

    @PostMapping("/plans/{id}/assign-driver")
    DispatchPlanResponse assignDriver(@PathVariable UUID id, @Valid @RequestBody AssignDriverRequest request) {
        return dispatch.assignDriver(id, request.vehicleId(), request.driverId());
    }

    @PostMapping("/plans/{id}/approve")
    DispatchPlanResponse approve(@PathVariable UUID id) {
        return dispatch.approve(id);
    }

    @PostMapping("/plans/{id}/reject")
    DispatchPlanResponse reject(@PathVariable UUID id) {
        return dispatch.reject(id);
    }

    @PostMapping("/plans/{id}/cancel")
    DispatchPlanResponse cancel(@PathVariable UUID id) {
        return dispatch.cancel(id);
    }

    @GetMapping("/driver/{driverId}/route")
    List<DispatchPlanResponse.DriverStopView> driverRoute(@PathVariable UUID driverId) {
        return dispatch.driverRoute(driverId);
    }

    @PostMapping("/stops/{stopId}/delivery")
    DispatchPlanResponse updateDelivery(@PathVariable UUID stopId,
            @Valid @RequestBody DeliveryUpdateRequest request) {
        return dispatch.updateDelivery(stopId, request.status());
    }
}
