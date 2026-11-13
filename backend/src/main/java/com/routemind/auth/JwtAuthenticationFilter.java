package com.routemind.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns a valid JWT into a Spring Security authentication.
 *
 * Runs on every request, before the authorization rules: a request with a
 * valid token gets the dispatcher principal (ROLE_DISPATCHER); a request
 * with no token or an invalid one stays anonymous and the authorization
 * rules answer 401 on protected paths. The filter never rejects anything
 * itself — that keeps one place (SecurityConfig) deciding what is public.
 *
 * The token normally arrives as {@code Authorization: Bearer <token>}.
 * WebSocket handshakes cannot set that header from a browser, so for
 * /ws the token is also accepted as the {@code token} query parameter.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = resolveToken(request);
            if (token != null) {
                jwtService.extractUsername(token).ifPresent(username -> {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            username, null, List.of(new SimpleGrantedAuthority("ROLE_DISPATCHER")));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length());
        }
        if (request.getRequestURI().startsWith("/ws")) {
            return request.getParameter("token");
        }
        return null;
    }
}
