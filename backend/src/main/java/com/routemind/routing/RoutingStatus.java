package com.routemind.routing;

/** Whether the road router can be reached, and where it lives. */
public record RoutingStatus(boolean available, String baseUrl, String profile) {
}
