package com.routemind;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Foundation smoke test: the application context loads, Flyway migrated the
 * test database (PostGIS + seeded depot), and Redis answers a ping.
 * If any of those fail, the foundation is not actually done.
 */
@SpringBootTest
@ActiveProfiles("test")
class RouteMindApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void flywaySeededTheDepot() {
        Integer depotCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM depots", Integer.class);
        assertThat(depotCount).isEqualTo(1);

        Boolean postgisPresent = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) > 0 FROM pg_extension WHERE extname = 'postgis'", Boolean.class);
        assertThat(postgisPresent).isTrue();
    }

    @Test
    void redisAnswers() {
        assertThat(redisTemplate.getConnectionFactory().getConnection().ping()).isEqualTo("PONG");
    }
}
