package com.amuguide.backend.controller;

import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/verifications")
@RequiredArgsConstructor
@CrossOrigin("*")
public class VerificationController {

    private final VerificationService verificationService;

    @GetMapping("/code/{codeActe}")
    public ResponseEntity<Map<String, Object>> verifierParCodeActe(@PathVariable String codeActe) {
        return ResponseEntity.ok(verificationService.verifierParCodeActe(codeActe));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Prestation>> rechercherParMotCle(@RequestParam String motCle) {
        return ResponseEntity.ok(verificationService.rechercherParMotCle(motCle));
    }
}
