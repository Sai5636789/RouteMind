package com.routemind.orders;

import com.routemind.common.PagedResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order endpoints. Lifecycle moves are separate endpoints rather than a
 * generic status field, so an illegal transition is a 409 with a reason
 * instead of a silently accepted bad state.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    PagedResponse<OrderResponse> list(@RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PagedResponse.from(orders.list(status, pageable), OrderResponse::from);
    }

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(orders.get(id));
    }

    @GetMapping("/by-vehicle/{vehicleId}")
    PagedResponse<OrderResponse> listByVehicle(@PathVariable UUID vehicleId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PagedResponse.from(orders.listByVehicle(vehicleId, pageable), OrderResponse::from);
    }

    @GetMapping("/by-driver/{driverId}")
    PagedResponse<OrderResponse> listByDriver(@PathVariable UUID driverId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PagedResponse.from(orders.listByDriver(driverId, pageable), OrderResponse::from);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse create(@Valid @RequestBody OrderRequest request) {
        Order created = orders.create(request.customerName(), request.deliveryAddress(),
                request.latitude(), request.longitude(), request.weightKg());
        return OrderResponse.from(created);
    }

    @PutMapping("/{id}")
    OrderResponse update(@PathVariable UUID id, @Valid @RequestBody OrderRequest request) {
        Order updated = orders.update(id, request.customerName(), request.deliveryAddress(),
                request.latitude(), request.longitude(), request.weightKg());
        return OrderResponse.from(updated);
    }

    @PostMapping("/{id}/assign")
    OrderResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignOrderRequest request) {
        return OrderResponse.from(orders.assign(id, request.vehicleId(), request.driverId()));
    }

    @PostMapping("/{id}/start-transit")
    OrderResponse startTransit(@PathVariable UUID id) {
        return OrderResponse.from(orders.startTransit(id));
    }

    @PostMapping("/{id}/deliver")
    OrderResponse deliver(@PathVariable UUID id) {
        return OrderResponse.from(orders.deliver(id));
    }

    @PostMapping("/{id}/cancel")
    OrderResponse cancel(@PathVariable UUID id) {
        return OrderResponse.from(orders.cancel(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        orders.delete(id);
    }
}
