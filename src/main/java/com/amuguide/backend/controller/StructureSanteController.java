package com.amuguide.backend.controller;

import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.TypeStructure;
import com.amuguide.backend.service.StructureSanteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/structures")
@RequiredArgsConstructor
@CrossOrigin("*")
public class StructureSanteController {

    private final StructureSanteService structureSanteService;

    @GetMapping
    public ResponseEntity<List<StructureSante>> getAllStructures() {
        return ResponseEntity.ok(structureSanteService.getAllStructures());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StructureSante> getStructureById(@PathVariable Long id) {
        return ResponseEntity.ok(structureSanteService.getStructureById(id));
    }

    @GetMapping("/ville/{ville}")
    public ResponseEntity<List<StructureSante>> getStructuresByVille(@PathVariable String ville) {
        return ResponseEntity.ok(structureSanteService.getStructuresByVille(ville));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<StructureSante>> getStructuresByType(@PathVariable TypeStructure type) {
        return ResponseEntity.ok(structureSanteService.getStructuresByType(type));
    }

    @GetMapping("/agrees")
    public ResponseEntity<List<StructureSante>> getStructuresAgrees() {
        return ResponseEntity.ok(structureSanteService.getStructuresAgrees());
    }

    @GetMapping("/proches")
    public ResponseEntity<List<StructureSante>> getStructuresProches(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam double rayonKm) {
        return ResponseEntity.ok(structureSanteService.findStructuresProches(latitude, longitude, rayonKm));
    }

    @PostMapping
    public ResponseEntity<StructureSante> createStructure(@RequestBody StructureSante structureSante) {
        return ResponseEntity.ok(structureSanteService.saveStructure(structureSante));
    }

    @PutMapping("/{id}")
    public ResponseEntity<StructureSante> updateStructure(@PathVariable Long id, @RequestBody StructureSante structureSante) {
        return ResponseEntity.ok(structureSanteService.updateStructure(id, structureSante));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStructure(@PathVariable Long id) {
        structureSanteService.deleteStructure(id);
        return ResponseEntity.noContent().build();
    }
}
