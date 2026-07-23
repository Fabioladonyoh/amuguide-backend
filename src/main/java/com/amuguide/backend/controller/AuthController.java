package com.amuguide.backend.controller;

import com.amuguide.backend.dto.AuthResponseDTO;
import com.amuguide.backend.dto.AssureLoginRequestDTO;
import com.amuguide.backend.dto.AssureRegisterRequestDTO;
import com.amuguide.backend.dto.LoginRequestDTO;
import com.amuguide.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Connexion assure et administrateur")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(
            summary = "Connexion",
            description = "Authentification avec email, login administrateur ou numero AMU"
    )
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/assure/login")
    @Operation(
            summary = "Connexion assure",
            description = "Authentification assure avec numero AMU et mot de passe"
    )
    public ResponseEntity<AuthResponseDTO> loginAssure(
            @Valid @RequestBody AssureLoginRequestDTO request
    ) {
        return ResponseEntity.ok(authService.loginAssure(request));
    }

    @PostMapping("/assure/register")
    @Operation(
            summary = "Inscription assure",
            description = "Creation d'un compte assure AMU depuis l'espace public"
    )
    public ResponseEntity<Map<String, String>> registerAssure(
            @Valid @RequestBody AssureRegisterRequestDTO request
    ) {
        return ResponseEntity.ok(authService.registerAssure(request));
    }
}
