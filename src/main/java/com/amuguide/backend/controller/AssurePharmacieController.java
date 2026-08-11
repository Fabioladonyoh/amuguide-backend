package com.amuguide.backend.controller;

import com.amuguide.backend.dto.PageResponseDTO;
import com.amuguide.backend.dto.PharmacieDTO;
import com.amuguide.backend.exception.BadRequestException;
import com.amuguide.backend.service.PharmacieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assure/pharmacies")
@RequiredArgsConstructor
@Tag(name = "Pharmacies assure", description = "Consultation des pharmacies agreees par les assures")
public class AssurePharmacieController {

    private final PharmacieService pharmacieService;

    @GetMapping
    @Operation(summary = "Lister les pharmacies actives et agreees")
    public PageResponseDTO<PharmacieDTO> pharmacies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) String quartier,
            @RequestParam(defaultValue = "nom") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        return PageResponseDTO.from(pharmacieService.listAccessible(
                search, ville, quartier, pageable(page, size, mapSort(sortBy), sortDirection)));
    }

    @GetMapping("/search")
    @Operation(summary = "Rechercher les pharmacies actives et agreees")
    public PageResponseDTO<PharmacieDTO> searchPharmacies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "query") String query,
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) String quartier,
            @RequestParam(defaultValue = "nom") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        return pharmacies(page, size, firstText(search, query), ville, quartier, sortBy, sortDirection);
    }

    @GetMapping("/nearby")
    @Operation(summary = "Lister les pharmacies actives et agreees proches d'une position")
    public PageResponseDTO<PharmacieDTO> nearbyPharmacies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "10") double radius) {
        if (radius < 0) {
            throw new BadRequestException("Le rayon doit etre positif");
        }
        List<PharmacieDTO> nearby = pharmacieService.nearby(latitude, longitude, radius);
        return PageResponseDTO.from(pharmacieService.pageFromList(nearby, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter une pharmacie active et agreee par ID")
    public PharmacieDTO pharmacie(@PathVariable Long id) {
        return pharmacieService.getAccessible(id);
    }

    private Sort sort(String sortBy, String sortDirection) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, sortBy);
    }

    private Pageable pageable(int page, int size, String sortBy, String sortDirection) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        return org.springframework.data.domain.PageRequest.of(normalizedPage, normalizedSize, sort(sortBy, sortDirection));
    }

    private String mapSort(String sortBy) {
        return switch (sortBy == null ? "" : sortBy) {
            case "code", "ville", "quartier", "region", "createdAt", "updatedAt" -> sortBy;
            default -> "nom";
        };
    }

    private String firstText(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
