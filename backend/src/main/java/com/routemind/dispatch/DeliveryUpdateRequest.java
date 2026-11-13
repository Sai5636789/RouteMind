package com.routemind.dispatch;

import jakarta.validation.constraints.NotNull;

/** The delivery update a driver reports for one stop. */
public record DeliveryUpdateRequest(@NotNull DeliveryStatus status) {
}
