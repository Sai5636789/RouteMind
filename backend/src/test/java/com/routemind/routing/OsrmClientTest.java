package com.routemind.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routemind.tracking.GeoPoint;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The OSRM client against a real HTTP server on localhost serving canned
 * OSRM responses — the parsing and the coordinate conversion are what is
 * under test, not OSRM itself. The stub is a test double for the server,
 * never for the production code path.
 */
class OsrmClientTest {

    private static final String ROUTE_JSON = """
            {"code":"Ok","routes":[{"distance":3053.2,"duration":272.5,
            "geometry":{"type":"LineString","coordinates":
            [[-82.325015,29.651604],[-82.321,29.65196],[-82.3000,29.6600]]}}],
            "waypoints":[{"location":[-82.325015,29.651604]}]}""";

    private static final String TABLE_JSON = """
            {"code":"Ok","durations":[[0,272.5],[300.0,0]],
            "distances":[[0,3053.2],[3200.0,0]]}""";

    private static final String NOT_OK_JSON = """
            {"code":"NoRoute","message":"Impossible route between points"}""";

    private HttpServer server;
    private String mode = "route";
    private OsrmClient client;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = switch (mode) {
                case "table" -> TABLE_JSON;
                case "not-ok" -> NOT_OK_JSON;
                case "garbage" -> "this is not json";
                default -> ROUTE_JSON;
            };
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        RoutingProperties properties = new RoutingProperties(true,
                "http://127.0.0.1:" + server.getAddress().getPort(), "driving",
                Duration.ofSeconds(2), Duration.ofSeconds(5), 25);
        client = new OsrmClient(properties, new ObjectMapper());
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void routeParsesGeometryAndConvertsCoordinates() {
        RoadRoute route = client.route(List.of(
                new GeoPoint(29.6516, -82.3248), new GeoPoint(29.66, -82.30)));

        assertEquals(3, route.geometry().size());
        // OSRM returns [longitude, latitude]; GeoPoint is (latitude, longitude).
        assertEquals(29.651604, route.geometry().get(0).latitude(), 1e-6);
        assertEquals(-82.325015, route.geometry().get(0).longitude(), 1e-6);
        assertEquals(3053.2, route.distanceMetres(), 1e-6);
        assertEquals(272.5, route.durationSeconds(), 1e-6);
    }

    @Test
    void matrixParsesDurationsAndDistances() {
        mode = "table";
        DistanceMatrix matrix = client.matrix(List.of(
                new GeoPoint(29.6516, -82.3248), new GeoPoint(29.66, -82.30)));

        assertEquals(2, matrix.points().size());
        assertEquals(272.5, matrix.durationsSeconds().get(0).get(1));
        assertEquals(3200.0, matrix.distancesMetres().get(1).get(0));
        assertEquals(0.0, matrix.durationsSeconds().get(0).get(0));
    }

    @Test
    void nonOkAnswerBecomesUnavailable() {
        mode = "not-ok";
        assertThrows(RoutingUnavailableException.class, () -> client.route(List.of(
                new GeoPoint(29.6516, -82.3248), new GeoPoint(29.66, -82.30))));
    }

    @Test
    void nonJsonAnswerBecomesUnavailable() {
        mode = "garbage";
        assertThrows(RoutingUnavailableException.class, () -> client.route(List.of(
                new GeoPoint(29.6516, -82.3248), new GeoPoint(29.66, -82.30))));
    }

    @Test
    void unreachableServerBecomesUnavailable() {
        RoutingProperties dead = new RoutingProperties(true,
                "http://127.0.0.1:1", "driving",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 25);
        OsrmClient deadClient = new OsrmClient(dead, new ObjectMapper());

        assertThrows(RoutingUnavailableException.class, () -> deadClient.route(List.of(
                new GeoPoint(29.6516, -82.3248), new GeoPoint(29.66, -82.30))));
        assertFalse(deadClient.probe());
    }

    @Test
    void probeIsTrueWhenServerAnswers() {
        assertTrue(client.probe());
    }

    @Test
    void proxyAuthenticatorIsAbsentWithoutCredentials() {
        assertNull(OsrmClient.proxyAuthenticator());
    }

    @Test
    void proxyAuthenticatorIsPresentWithCredentials() {
        String savedUser = System.getProperty("https.proxyUser");
        String savedPassword = System.getProperty("https.proxyPassword");
        try {
            System.setProperty("https.proxyUser", "proxy-user");
            System.setProperty("https.proxyPassword", "proxy-pass");
            assertNotNull(OsrmClient.proxyAuthenticator());
        } finally {
            restoreProperty("https.proxyUser", savedUser);
            restoreProperty("https.proxyPassword", savedPassword);
        }
    }

    @Test
    void routeStillWorksWhenProxyCredentialsAreConfigured() {
        // A dead proxy plus credentials must not break direct localhost
        // calls: localhost is in the non-proxy list, so the stub is
        // reached directly and the authenticator is never consulted.
        String[] keys = {"http.proxyHost", "http.proxyPort", "http.proxyUser",
                "http.proxyPassword", "http.nonProxyHosts"};
        String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            saved[i] = System.getProperty(keys[i]);
        }
        try {
            System.setProperty("http.proxyHost", "127.0.0.1");
            System.setProperty("http.proxyPort", "9");
            System.setProperty("http.proxyUser", "proxy-user");
            System.setProperty("http.proxyPassword", "proxy-pass");
            System.setProperty("http.nonProxyHosts", "localhost|127.0.0.1");
            RoutingProperties properties = new RoutingProperties(true,
                    "http://127.0.0.1:" + server.getAddress().getPort(), "driving",
                    Duration.ofSeconds(2), Duration.ofSeconds(5), 25);
            OsrmClient proxiedClient = new OsrmClient(properties, new ObjectMapper());

            RoadRoute route = proxiedClient.route(List.of(
                    new GeoPoint(29.6516, -82.3248), new GeoPoint(29.66, -82.30)));

            assertEquals(3, route.geometry().size());
        } finally {
            for (int i = 0; i < keys.length; i++) {
                restoreProperty(keys[i], saved[i]);
            }
        }
    }

    private static void restoreProperty(String key, String savedValue) {
        if (savedValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, savedValue);
        }
    }
}
