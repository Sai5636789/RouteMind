package com.routemind.dispatch;

/**
 * One stop's delivery progress. PENDING is assigned but not yet picked
 * up; PICKED_UP means the driver has the order; DELIVERED and FAILED are
 * terminal (a failed delivery cancels the order — the dispatcher creates
 * a new order if it should be tried again); CANCELLED is set when the
 * whole plan is cancelled before the stop was reached, or when a
 * breakdown takes the stop's order off an approved plan (see
 * IncidentService).
 */
public enum DispatchStopStatus {
    PENDING,
    PICKED_UP,
    DELIVERED,
    FAILED,
    CANCELLED
}
