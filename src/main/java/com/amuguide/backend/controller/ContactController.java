package com.amuguide.backend.controller;

import com.amuguide.backend.dto.ContactRequestDTO;
import com.amuguide.backend.dto.ContactResponseDTO;
import com.amuguide.backend.service.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
@CrossOrigin("*")
@Tag(name = "Contact", description = "Formulaire de contact public")
public class ContactController {

    private final ContactService contactService;

    @PostMapping
    @Operation(summary = "Envoyer un message à l'assurance",
               description = "Endpoint public — aucune authentification requise")
    public ResponseEntity<ContactResponseDTO> envoyerMessage(@Valid @RequestBody ContactRequestDTO request) {
        return ResponseEntity.ok(contactService.envoyerMessage(request));
    }
}
