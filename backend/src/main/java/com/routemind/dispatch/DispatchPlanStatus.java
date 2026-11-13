package com.routemind.dispatch;

/**
 * A dispatch plan's lifecycle. DRAFT is a saved optimization that assigns
 * nothing yet; APPROVED is the dispatcher's go-ahead, after which the
 * orders are assigned and the vehicles are on route; REJECTED and
 * CANCELLED end a plan without assigning anything (cancel also unassigns
 * orders that were already assigned); COMPLETED means every stop reached
 * a terminal state.
 */
public enum DispatchPlanStatus {
    DRAFT,
    APPROVED,
    REJECTED,
    COMPLETED,
    CANCELLED
}
