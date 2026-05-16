package com.amuguide.backend.service;

import com.amuguide.backend.dto.AdminLoginRequestDTO;
import com.amuguide.backend.dto.AssureLoginRequestDTO;
import com.amuguide.backend.dto.AuthResponseDTO;
import com.amuguide.backend.entity.Administrateur;
import com.amuguide.backend.entity.AssureAMU;
import com.amuguide.backend.enums.StatutAssure;
import com.amuguide.backend.exception.BadRequestException;
import com.amuguide.backend.repository.AdministrateurRepository;
import com.amuguide.backend.repository.AssureAMURepository;
import com.amuguide.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AssureAMURepository assureAMURepository;
    private final AdministrateurRepository administrateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponseDTO loginAssure(AssureLoginRequestDTO request) {
        AssureAMU assure = assureAMURepository.findByNumeroAMU(request.getNumeroAMU())
                .orElseThrow(() -> new BadRequestException("Numéro AMU ou mot de passe incorrect"));

        if (assure.getStatut() != StatutAssure.ACTIF) {
            throw new BadRequestException("Votre compte est inactif ou suspendu. Contactez l'assurance.");
        }

        if (assure.getMotDePasse() == null || !passwordEncoder.matches(request.getMotDePasse(), assure.getMotDePasse())) {
            throw new BadRequestException("Numéro AMU ou mot de passe incorrect");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ASSURE");
        claims.put("assureId", assure.getIdAssure());
        claims.put("nom", assure.getNom());
        claims.put("prenom", assure.getPrenom());

        String token = jwtService.generateToken("ASSURE:" + assure.getNumeroAMU(), claims);

        return AuthResponseDTO.builder()
                .token(token)
                .type("Bearer")
                .role("ASSURE")
                .identifiant(assure.getNumeroAMU())
                .nom(assure.getNom())
                .prenom(assure.getPrenom())
                .build();
    }

    public AuthResponseDTO loginAdmin(AdminLoginRequestDTO request) {
        Administrateur admin = administrateurRepository.findByLogin(request.getLogin())
                .orElseThrow(() -> new BadRequestException("Login ou mot de passe incorrect"));

        if (!Boolean.TRUE.equals(admin.getActif())) {
            throw new BadRequestException("Compte administrateur désactivé");
        }

        if (!passwordEncoder.matches(request.getMotDePasse(), admin.getMotDePasse())) {
            throw new BadRequestException("Login ou mot de passe incorrect");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ADMIN");
        claims.put("adminId", admin.getIdAdmin());
        claims.put("nom", admin.getNom());
        claims.put("prenom", admin.getPrenom());

        String token = jwtService.generateToken("ADMIN:" + admin.getLogin(), claims);

        return AuthResponseDTO.builder()
                .token(token)
                .type("Bearer")
                .role("ADMIN")
                .identifiant(admin.getLogin())
                .nom(admin.getNom())
                .prenom(admin.getPrenom())
                .build();
    }
}
