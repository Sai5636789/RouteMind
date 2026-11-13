package com.routemind.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login for the single dispatcher account.
 *
 * POST /api/v1/auth/login with {"username", "password"} returns a JWT
 * ({"token", "tokenType": "Bearer", "expiresInSeconds", "username"}).
 * Callers send it back as {@code Authorization: Bearer <token>} on every
 * other API request. Wrong credentials answer 401 with the same message
 * for a wrong username and a wrong password, so the response does not
 * reveal which half was wrong. There is no logout endpoint: tokens are
 * stateless, so logging out just discards the token client-side.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final DispatcherCredentials credentials;
    private final JwtService jwtService;

    public AuthController(DispatcherCredentials credentials, JwtService jwtService) {
        this.credentials = credentials;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        if (!credentials.matches(request.username(), request.password())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return new LoginResponse(
                jwtService.generateToken(request.username()),
                "Bearer",
                jwtService.tokenTtl().toSeconds(),
                request.username());
    }

    public record LoginRequest(
            @NotBlank(message = "username is required") String username,
            @NotBlank(message = "password is required") String password) {
    }

    public record LoginResponse(String token, String tokenType, long expiresInSeconds, String username) {
    }
}
