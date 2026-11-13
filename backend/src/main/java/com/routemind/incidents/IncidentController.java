package com.routemind.incidents;

import com.routemind.common.PagedResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
 * Breakdown incident endpoints. Controllers only translate HTTP to
 * service calls and service results to DTOs; all rules live in
 * IncidentService.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidents;

    public IncidentController(IncidentService incidents) {
        this.incidents = incidents;
    }

    /** The dispatcher reports a breakdown. The reason is optional — a breakdown with no known cause yet is still a breakdown. */
    public record BreakdownRequest(
            @NotNull UUID vehicleId,
            @Size(max = 500) String reason) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    IncidentResponse reportBreakdown(@Valid @RequestBody BreakdownRequest request) {
        return IncidentResponse.from(incidents.reportBreakdown(request.vehicleId(), request.reason()));
    }

    @GetMapping
    PagedResponse<IncidentResponse> list(@RequestParam(required = false) IncidentStatus status,
            @PageableDefault(size = 20, sort = "reportedAt") Pageable pageable) {
        return PagedResponse.from(incidents.list(status, pageable),
                incident -> IncidentResponse.from(incidents.get(incident.getId())));
    }

    @GetMapping("/{id}")
    IncidentResponse get(@PathVariable UUID id) {
        return IncidentResponse.from(incidents.get(id));
    }

    @PostMapping("/{id}/reroute")
    RerouteResponse reroute(@PathVariable UUID id) {
        return RerouteResponse.from(incidents.reroute(id));
    }

    @PostMapping("/{id}/resolve")
    IncidentResponse resolve(@PathVariable UUID id) {
        return IncidentResponse.from(incidents.resolve(id));
    }
}
