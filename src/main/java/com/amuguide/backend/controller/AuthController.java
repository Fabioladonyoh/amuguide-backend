package com.amuguide.backend.controller;

import com.amuguide.backend.dto.AdminLoginRequestDTO;
import com.amuguide.backend.dto.AssureLoginRequestDTO;
import com.amuguide.backend.dto.AuthResponseDTO;
import com.amuguide.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Connexion assuré et administrateur")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/assure/login")
    @Operation(summary = "Connexion assuré", description = "Authentification avec numéro AMU et mot de passe (défaut : date de naissance au format ddMMyyyy)")
    public ResponseEntity<AuthResponseDTO> loginAssure(@Valid @RequestBody AssureLoginRequestDTO request) {
        return ResponseEntity.ok(authService.loginAssure(request));
    }

    @PostMapping("/admin/login")
    @Operation(summary = "Connexion administrateur", description = "Authentification avec login et mot de passe admin")
    public ResponseEntity<AuthResponseDTO> loginAdmin(@Valid @RequestBody AdminLoginRequestDTO request) {
        return ResponseEntity.ok(authService.loginAdmin(request));
    }
}
