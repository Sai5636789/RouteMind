package com.routemind.depot;

import com.routemind.common.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only depot endpoints. Depots are seeded by the Flyway migration;
 * there is no write endpoint yet, so the fleet map reads the depot
 * location from the database instead of hard-coding it in the frontend.
 */
@RestController
@RequestMapping("/api/v1/depots")
public class DepotController {

    private final DepotRepository depots;

    public DepotController(DepotRepository depots) {
        this.depots = depots;
    }

    @GetMapping
    List<DepotResponse> list() {
        return depots.findAll().stream().map(DepotResponse::from).toList();
    }

    @GetMapping("/{id}")
    DepotResponse get(@PathVariable UUID id) {
        Depot depot = depots.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Depot", id));
        return DepotResponse.from(depot);
    }
}
