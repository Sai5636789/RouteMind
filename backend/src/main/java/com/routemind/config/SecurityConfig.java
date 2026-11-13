package com.routemind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Stateless API security. Health endpoints are public so load balancers,
 * Docker healthchecks and the dashboard can probe the service.
 *
 * TEMPORARY, local-demo only: read (GET) endpoints for vehicles, drivers,
 * orders and depots are public so the dispatcher dashboard and the fleet
 * map can load data before authentication lands. Every write and every
 * lifecycle action (POST, PUT, PATCH, DELETE) still requires
 * authentication and answers 401 until the token mechanism lands.
 * This exception must be removed when JWT login is added — see the
 * authentication feature. Do not extend it to writes or to new resources
 * without a deliberate decision.
 */
@Configuration
public class SecurityConfig {

    private static final String[] TEMPORARY_PUBLIC_READS = {
        "/api/v1/vehicles/**",
        "/api/v1/drivers/**",
        "/api/v1/orders/**",
        "/api/v1/depots/**",
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/health", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, TEMPORARY_PUBLIC_READS).permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
