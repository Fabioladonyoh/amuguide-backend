package com.amuguide.backend.controller;

import com.amuguide.backend.dto.PageResponseDTO;
import com.amuguide.backend.dto.PharmacieDTO;
import com.amuguide.backend.dto.StatusUpdateRequestDTO;
import com.amuguide.backend.service.PharmacieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/pharmacies")
@RequiredArgsConstructor
public class AdminPharmacieController {

    private final PharmacieService pharmacieService;

    @GetMapping
    public PageResponseDTO<PharmacieDTO> pharmacies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) String quartier,
            @RequestParam(required = false) Boolean agreee,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        Page<PharmacieDTO> result = pharmacieService.list(
                search, ville, quartier, active, agreee, PageRequest.of(page, size, sort(mapSort(sortBy), sortDirection)));
        return PageResponseDTO.from(result);
    }

    @GetMapping("/{id}")
    public PharmacieDTO pharmacie(@PathVariable Long id) {
        return pharmacieService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PharmacieDTO createPharmacie(@Valid @RequestBody PharmacieDTO dto) {
        return pharmacieService.create(dto);
    }

    @PutMapping("/{id}")
    public PharmacieDTO updatePharmacie(@PathVariable Long id, @Valid @RequestBody PharmacieDTO dto) {
        return pharmacieService.update(id, dto);
    }

    @PatchMapping("/{id}/status")
    public PharmacieDTO updatePharmacieStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequestDTO req) {
        return pharmacieService.updateStatus(id, req.getActif());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePharmacie(@PathVariable Long id) {
        pharmacieService.deleteOrArchive(id);
        return ResponseEntity.noContent().build();
    }

    private Sort sort(String sortBy, String sortDirection) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, sortBy);
    }

    private String mapSort(String sortBy) {
        return switch (sortBy == null ? "" : sortBy) {
            case "code", "ville", "quartier", "region", "createdAt", "updatedAt" -> sortBy;
            default -> "nom";
        };
    }
}
