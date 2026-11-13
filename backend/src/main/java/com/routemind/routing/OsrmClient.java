package com.routemind.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routemind.tracking.GeoPoint;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Thin HTTP client for a self-hosted OSRM instance. It speaks only the
 * two OSRM services RouteMind needs — {@code /route} for road geometry
 * and {@code /table} for travel-time/distance matrices — and translates
 * every OSRM answer into plain records. It never invents geometry or
 * times: a transport failure, a timeout, or an OSRM answer that is not
 * {@code "Ok"} becomes a {@link RoutingUnavailableException}, and the
 * caller decides the fallback.
 *
 * <p>OSRM coordinates are {@code longitude,latitude}; {@link GeoPoint} is
 * {@code latitude,longitude}, so both directions convert explicitly.
 */
@Component
public class OsrmClient {

    private final RoutingProperties properties;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public OsrmClient(RoutingProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout());
        Authenticator proxyAuth = proxyAuthenticator();
        if (proxyAuth != null) {
            builder.authenticator(proxyAuth);
        }
        this.http = builder.build();
    }

    /**
     * Answers proxy authentication challenges when proxy credentials are
     * configured as JVM properties ({@code https.proxyUser} /
     * {@code https.proxyPassword}, falling back to the {@code http.*}
     * variants). The JVM's proxy selector already honours
     * {@code http.proxyHost} / {@code https.proxyHost}, but without an
     * authenticator a proxy that demands credentials answers 407 and the
     * request fails. Returns {@code null} when no credentials are
     * configured, so direct and unauthenticated-proxy setups are
     * unchanged.
     */
    static Authenticator proxyAuthenticator() {
        String user = firstNonBlank(System.getProperty("https.proxyUser"), System.getProperty("http.proxyUser"));
        String password = firstNonBlank(
                System.getProperty("https.proxyPassword"), System.getProperty("http.proxyPassword"));
        if (user == null || password == null) {
            return null;
        }
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == RequestorType.PROXY) {
                    return new PasswordAuthentication(user, password.toCharArray());
                }
                return null;
            }
        };
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    /** Road route through the waypoints, in order. */
    public RoadRoute route(List<GeoPoint> waypoints) {
        String path = "/route/v1/" + properties.profile() + "/" + coordinates(waypoints)
                + "?overview=full&geometries=geojson&steps=false";
        JsonNode body = get(path);
        JsonNode routes = body.path("routes");
        if (!routes.isArray() || routes.isEmpty()) {
            throw new RoutingUnavailableException("OSRM returned no route");
        }
        JsonNode route = routes.get(0);
        JsonNode geometry = route.path("geometry").path("coordinates");
        if (!geometry.isArray() || geometry.size() < 2) {
            throw new RoutingUnavailableException("OSRM returned a route without geometry");
        }
        List<GeoPoint> points = new ArrayList<>();
        for (JsonNode coordinate : geometry) {
            points.add(new GeoPoint(coordinate.get(1).asDouble(), coordinate.get(0).asDouble()));
        }
        return new RoadRoute(points, route.path("distance").asDouble(), route.path("duration").asDouble());
    }

    /** Travel-time and distance matrix for every ordered pair of points. */
    public DistanceMatrix matrix(List<GeoPoint> points) {
        String path = "/table/v1/" + properties.profile() + "/" + coordinates(points)
                + "?annotations=distance,duration";
        JsonNode body = get(path);
        return new DistanceMatrix(points, readMatrix(body, "durations"), readMatrix(body, "distances"));
    }

    /**
     * True when the OSRM server answers at all — any HTTP response, even
     * an OSRM error for the probe coordinates, proves the server is up.
     * Transport failures and timeouts mean it is not.
     */
    public boolean probe() {
        try {
            HttpRequest request = request("/route/v1/" + properties.profile()
                    + "/-82.3248,29.6516;-82.3248,29.6516?overview=false");
            http.send(request, HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private List<List<Double>> readMatrix(JsonNode body, String field) {
        JsonNode matrix = body.path(field);
        if (!matrix.isArray()) {
            throw new RoutingUnavailableException("OSRM returned no " + field + " matrix");
        }
        List<List<Double>> rows = new ArrayList<>();
        for (JsonNode row : matrix) {
            List<Double> values = new ArrayList<>();
            for (JsonNode cell : row) {
                values.add(cell.isNull() ? null : cell.asDouble());
            }
            rows.add(values);
        }
        return rows;
    }

    private JsonNode get(String path) {
        HttpRequest request = request(path);
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RoutingUnavailableException("Could not reach the road router at " + properties.baseUrl(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RoutingUnavailableException("Road routing request was interrupted", e);
        }
        JsonNode body = parse(response.body(), path);
        String code = body.path("code").asText("");
        if (response.statusCode() == 400 || "InvalidInput".equals(code) || "InvalidOptions".equals(code)) {
            throw new IllegalArgumentException("Road routing rejected the request: "
                    + body.path("message").asText(code));
        }
        if (response.statusCode() != 200 || !"Ok".equals(code)) {
            throw new RoutingUnavailableException("Road router answered " + response.statusCode()
                    + " (" + (code.isBlank() ? "no OSRM code" : code) + ")");
        }
        return body;
    }

    private JsonNode parse(String body, String path) {
        try {
            return mapper.readTree(body);
        } catch (IOException e) {
            throw new RoutingUnavailableException("Road router returned a response that is not JSON for " + path, e);
        }
    }

    private HttpRequest request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(properties.baseUrl() + path))
                .timeout(properties.readTimeout())
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    private static String coordinates(List<GeoPoint> points) {
        return points.stream()
                .map(point -> point.longitude() + "," + point.latitude())
                .collect(Collectors.joining(";"));
    }
}
