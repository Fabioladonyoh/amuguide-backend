package com.amuguide.backend.controller;

import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.service.PrestationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/prestations")
@RequiredArgsConstructor
@CrossOrigin("*")
public class PrestationController {

    private final PrestationService prestationService;

    @GetMapping
    public ResponseEntity<List<Prestation>> getAllPrestations() {
        return ResponseEntity.ok(prestationService.getAllPrestations());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Prestation> getPrestationById(@PathVariable Long id) {
        return ResponseEntity.ok(prestationService.getPrestationById(id));
    }

    @GetMapping("/code/{codeActe}")
    public ResponseEntity<Prestation> getPrestationByCodeActe(@PathVariable String codeActe) {
        return ResponseEntity.ok(prestationService.getPrestationByCodeActe(codeActe));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Prestation>> searchPrestations(@RequestParam String nom) {
        return ResponseEntity.ok(prestationService.searchPrestationsByNom(nom));
    }

    @PostMapping
    public ResponseEntity<Prestation> createPrestation(@RequestBody Prestation prestation) {
        return ResponseEntity.ok(prestationService.savePrestation(prestation));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Prestation> updatePrestation(@PathVariable Long id, @RequestBody Prestation prestation) {
        return ResponseEntity.ok(prestationService.updatePrestation(id, prestation));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePrestation(@PathVariable Long id) {
        prestationService.deletePrestation(id);
        return ResponseEntity.noContent().build();
    }
}