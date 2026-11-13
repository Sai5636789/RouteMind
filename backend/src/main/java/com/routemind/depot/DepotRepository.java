package com.routemind.depot;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Reads depots with plain SQL. The location column is PostGIS geography,
 * so the coordinates are projected with ST_X/ST_Y here instead of mapping
 * the geography type into JPA — no spatial library is needed for a read
 * of two numbers. Latitude is ST_Y, longitude is ST_X.
 */
@Repository
public class DepotRepository {

    private static final RowMapper<Depot> ROW = (rs, rowNum) -> new Depot(
            UUID.fromString(rs.getString("id")),
            rs.getString("name"),
            rs.getString("address"),
            rs.getDouble("latitude"),
            rs.getDouble("longitude"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private static final String SELECT = """
            SELECT id, name, address,
                   ST_Y(location::geometry) AS latitude,
                   ST_X(location::geometry) AS longitude,
                   created_at, updated_at
              FROM depots
            """;

    private final JdbcTemplate jdbc;

    public DepotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<Depot> findAll() {
        return jdbc.query(SELECT + " ORDER BY created_at", ROW);
    }

    Optional<Depot> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE id = ?", ROW, id).stream().findFirst();
    }
}
