package com.frauddetection.api.adapter.in.web.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentification", description = "Obtention d'un jeton JWT (comptes de démo : analyst / analyst123, supervisor / supervisor123)")
class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokens;

    AuthController(AuthenticationManager authenticationManager, JwtTokenService tokens) {
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
    }

    @PostMapping("/login")
    @SecurityRequirements   // route publique : pas de cadenas dans Swagger
    @Operation(summary = "Connexion", description = "Renvoie un jeton Bearer à placer dans l'en-tête Authorization.")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        JwtTokenService.Token token = tokens.issue(authentication);
        return new LoginResponse(token.accessToken(), "Bearer", token.expiresAt(), token.username(), token.roles());
    }

    record LoginRequest(
            @NotBlank @Schema(example = "analyst") String username,
            @NotBlank @Schema(example = "analyst123") String password) {
    }

    record LoginResponse(String accessToken, String tokenType, Instant expiresAt, String username, List<String> roles) {
    }
}
