package com.amuguide.backend.service;

import com.amuguide.backend.dto.AdminLoginRequestDTO;
import com.amuguide.backend.dto.AssureLoginRequestDTO;
import com.amuguide.backend.dto.AssureRegisterRequestDTO;
import com.amuguide.backend.dto.AuthResponseDTO;
import com.amuguide.backend.dto.LoginRequestDTO;
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
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AssureAMURepository assureAMURepository;
    private final AdministrateurRepository administrateurRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponseDTO login(LoginRequestDTO request) {
        String identifiant = request.getIdentifiant().trim();

        Optional<Administrateur> admin = administrateurRepository.findByLogin(identifiant)
                .or(() -> administrateurRepository.findByEmail(identifiant));

        if (admin.isPresent()) {
            AdminLoginRequestDTO adminRequest = new AdminLoginRequestDTO();
            adminRequest.setLogin(admin.get().getLogin());
            adminRequest.setMotDePasse(request.getMotDePasse());
            return loginAdmin(adminRequest);
        }

        Optional<AssureAMU> assure = assureAMURepository.findByNumeroAMU(identifiant)
                .or(() -> assureAMURepository.findByEmail(identifiant));

        if (assure.isPresent()) {
            AssureLoginRequestDTO assureRequest = new AssureLoginRequestDTO();
            assureRequest.setNumeroAMU(assure.get().getNumeroAMU());
            assureRequest.setMotDePasse(request.getMotDePasse());
            return loginAssure(assureRequest);
        }

        throw new BadRequestException("Identifiant ou mot de passe incorrect");
    }

    public Map<String, String> registerAssure(AssureRegisterRequestDTO request) {
        String numeroAMU = request.getNumeroAMU().trim();
        String email = request.getEmail().trim();

        if (assureAMURepository.findByNumeroAMU(numeroAMU).isPresent()) {
            throw new BadRequestException("Ce numéro AMU est déjà utilisé.");
        }

        if (assureAMURepository.findByEmail(email).isPresent()) {
            throw new BadRequestException("Cette adresse e-mail est déjà utilisée.");
        }

        AssureAMU assure = AssureAMU.builder()
                .nom(request.getNom().trim())
                .prenom(request.getPrenom().trim())
                .numeroAMU(numeroAMU)
                .dateNaissance(request.getDateNaissance())
                .telephone(request.getTelephone().trim())
                .email(email)
                .adresse(request.getAdresse().trim())
                .statut(StatutAssure.ACTIF)
                .motDePasse(passwordEncoder.encode(request.getMotDePasse()))
                .build();

        assureAMURepository.save(assure);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Votre compte a été créé avec succès. Vous pouvez maintenant vous connecter.");
        return response;
    }

    public AuthResponseDTO loginAssure(AssureLoginRequestDTO request) {
        String numeroAMU = request.getNumeroAMU().trim();

        AssureAMU assure = assureAMURepository.findByNumeroAMU(numeroAMU)
                .orElseThrow(() -> {
                    Optional<Administrateur> admin = administrateurRepository.findByLogin(numeroAMU)
                            .or(() -> administrateurRepository.findByEmail(numeroAMU));

                    if (admin.isPresent()) {
                        return new BadRequestException("Ce compte ne permet pas d'accéder à l'espace assuré.");
                    }

                    return new BadRequestException("Numéro AMU ou mot de passe incorrect.");
                });

        if (assure.getStatut() != StatutAssure.ACTIF) {
            throw new BadRequestException("Votre compte est désactivé. Veuillez contacter l'assistance.");
        }

        if (assure.getMotDePasse() == null || !passwordEncoder.matches(request.getMotDePasse(), assure.getMotDePasse())) {
            throw new BadRequestException("Numéro AMU ou mot de passe incorrect.");
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
                .utilisateurId(assure.getIdAssure())
                .identifiant(assure.getNumeroAMU())
                .nom(assure.getNom())
                .prenom(assure.getPrenom())
                .email(assure.getEmail())
                .statut(assure.getStatut().name())
                .build();
    }

    public AuthResponseDTO loginAdmin(AdminLoginRequestDTO request) {
        Administrateur admin = administrateurRepository.findByLogin(request.getLogin())
                .orElseThrow(() -> new BadRequestException("Login ou mot de passe incorrect"));

        if (!Boolean.TRUE.equals(admin.getActif())) {
            throw new BadRequestException("Compte administrateur desactive");
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
                .utilisateurId(admin.getIdAdmin())
                .identifiant(admin.getLogin())
                .nom(admin.getNom())
                .prenom(admin.getPrenom())
                .email(admin.getEmail())
                .build();
    }
}
