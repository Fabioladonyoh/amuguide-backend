package com.amuguide.backend.service;

import com.amuguide.backend.dto.PharmacieDTO;
import com.amuguide.backend.entity.Pharmacie;
import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.exception.BadRequestException;
import com.amuguide.backend.exception.ResourceNotFoundException;
import com.amuguide.backend.repository.PharmacieRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PharmacieService {

    private final PharmacieRepository pharmacieRepository;
    private final StructureSanteRepository structureSanteRepository;

    public Page<PharmacieDTO> list(String search, String ville, String quartier, Boolean active, Boolean agreee, Pageable pageable) {
        return pharmacieRepository.searchPharmacies(search, ville, quartier, active, agreee, pageable)
                .map(pharmacie -> toDTO(pharmacie, null));
    }

    public Page<PharmacieDTO> listAccessible(String search, String ville, String quartier, Pageable pageable) {
        List<PharmacieDTO> values = structureSanteRepository.findOfficialPharmacies(mapOfficialPharmacySort(pageable.getSort())).stream()
                .filter(this::isOfficialPharmacy)
                .filter(structure -> matchesOfficialPharmacyLocation(structure, ville))
                .filter(structure -> matchesOfficialPharmacySearch(structure, firstText(search, quartier)))
                .map(structure -> toDTO(structure, null))
                .toList();
        return pageFromList(values, pageable.getPageNumber(), pageable.getPageSize());
    }

    public List<PharmacieDTO> listAccessibleValues(String search, String ville, String quartier, Sort sort) {
        return structureSanteRepository.findOfficialPharmacies(mapOfficialPharmacySort(sort)).stream()
                .filter(this::isOfficialPharmacy)
                .filter(structure -> matchesOfficialPharmacyLocation(structure, ville))
                .filter(structure -> matchesOfficialPharmacySearch(structure, firstText(search, quartier)))
                .map(structure -> toDTO(structure, null))
                .toList();
    }

    public PharmacieDTO get(Long id) {
        return toDTO(find(id), null);
    }

    public PharmacieDTO getAccessible(Long id) {
        StructureSante pharmacie = structureSanteRepository.findById(id)
                .filter(this::isOfficialPharmacy)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacie officielle introuvable : " + id));
        return toDTO(pharmacie, null);
    }

    public PharmacieDTO create(PharmacieDTO dto) {
        String code = codeOrGenerated(dto);
        pharmacieRepository.findByCode(code).ifPresent(existing -> {
            throw new BadRequestException("Une pharmacie avec ce code existe deja");
        });
        Pharmacie pharmacie = toEntity(dto, new Pharmacie());
        pharmacie.setCode(code);
        return toDTO(pharmacieRepository.save(pharmacie), null);
    }

    public PharmacieDTO update(Long id, PharmacieDTO dto) {
        Pharmacie pharmacie = find(id);
        String code = codeOrGenerated(dto);
        pharmacieRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new BadRequestException("Une pharmacie avec ce code existe deja");
                });
        pharmacie.setCode(code);
        return toDTO(pharmacieRepository.save(toEntity(dto, pharmacie)), null);
    }

    public PharmacieDTO updateStatus(Long id, Boolean active) {
        Pharmacie pharmacie = find(id);
        pharmacie.setActive(active);
        return toDTO(pharmacieRepository.save(pharmacie), null);
    }

    public void deleteOrArchive(Long id) {
        pharmacieRepository.delete(find(id));
    }

    public List<PharmacieDTO> nearby(double latitude, double longitude, double radiusKm) {
        if (radiusKm < 0) {
            throw new BadRequestException("Le rayon doit etre positif");
        }
        return structureSanteRepository.findOfficialPharmacies(Sort.by("nom")).stream()
                .filter(this::isOfficialPharmacy)
                .filter(pharmacie -> pharmacie.getLatitude() != null && pharmacie.getLongitude() != null)
                .map(pharmacie -> toDTO(pharmacie, distanceKm(latitude, longitude, pharmacie.getLatitude(), pharmacie.getLongitude())))
                .filter(dto -> dto.getDistanceKm() != null && dto.getDistanceKm() <= radiusKm)
                .sorted(Comparator.comparing(PharmacieDTO::getDistanceKm))
                .toList();
    }

    public Page<PharmacieDTO> pageFromList(List<PharmacieDTO> values, int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        int from = Math.min(normalizedPage * normalizedSize, values.size());
        int to = Math.min(from + normalizedSize, values.size());
        return new PageImpl<>(values.subList(from, to), Pageable.ofSize(normalizedSize).withPage(normalizedPage), values.size());
    }

    public Pharmacie find(Long id) {
        return pharmacieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacie introuvable : " + id));
    }

    public PharmacieDTO toDTO(Pharmacie pharmacie, Double distanceKm) {
        return PharmacieDTO.builder()
                .id(pharmacie.getId())
                .code(pharmacie.getCode())
                .nom(pharmacie.getNom())
                .telephone(pharmacie.getTelephone())
                .adresse(pharmacie.getAdresse())
                .quartier(pharmacie.getQuartier())
                .ville(pharmacie.getVille())
                .region(pharmacie.getRegion())
                .email(pharmacie.getEmail())
                .latitude(pharmacie.getLatitude())
                .longitude(pharmacie.getLongitude())
                .agreee(pharmacie.getAgreee())
                .active(pharmacie.getActive())
                .notes(pharmacie.getNotes())
                .distanceKm(distanceKm == null ? null : Math.round(distanceKm * 100.0) / 100.0)
                .createdAt(pharmacie.getCreatedAt())
                .updatedAt(pharmacie.getUpdatedAt())
                .build();
    }

    public PharmacieDTO toDTO(StructureSante pharmacie, Double distanceKm) {
        return PharmacieDTO.builder()
                .id(pharmacie.getIdStructure())
                .code(pharmacie.getCode())
                .nom(pharmacie.getNom())
                .telephone(null)
                .adresse(pharmacie.getAdresse())
                .quartier(null)
                .ville(null)
                .region(pharmacie.getRegion())
                .email(null)
                .latitude(pharmacie.getLatitude())
                .longitude(pharmacie.getLongitude())
                .agreee(true)
                .active(true)
                .notes(pharmacie.getTypeOfficiel())
                .distanceKm(distanceKm == null ? null : Math.round(distanceKm * 100.0) / 100.0)
                .createdAt(pharmacie.getCreatedAt())
                .updatedAt(pharmacie.getUpdatedAt())
                .build();
    }

    private Pharmacie toEntity(PharmacieDTO dto, Pharmacie pharmacie) {
        pharmacie.setNom(dto.getNom());
        pharmacie.setTelephone(dto.getTelephone());
        pharmacie.setAdresse(dto.getAdresse());
        pharmacie.setQuartier(dto.getQuartier());
        pharmacie.setVille(dto.getVille());
        pharmacie.setRegion(dto.getRegion());
        pharmacie.setEmail(dto.getEmail());
        pharmacie.setLatitude(dto.getLatitude());
        pharmacie.setLongitude(dto.getLongitude());
        pharmacie.setAgreee(dto.getAgreee() != null ? dto.getAgreee() : true);
        pharmacie.setActive(dto.getActive() != null ? dto.getActive() : true);
        pharmacie.setNotes(dto.getNotes());
        return pharmacie;
    }

    private String codeOrGenerated(PharmacieDTO dto) {
        if (dto.getCode() != null && !dto.getCode().isBlank()) {
            return dto.getCode().trim();
        }
        return generateCode(dto.getNom());
    }

    public String generateCode(String nom) {
        String normalized = Normalizer.normalize(nom == null ? "" : nom, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return "PHARM_" + normalized;
    }

    private boolean isAccessible(Pharmacie pharmacie) {
        return Boolean.TRUE.equals(pharmacie.getActive()) && Boolean.TRUE.equals(pharmacie.getAgreee());
    }

    private boolean isOfficialPharmacy(StructureSante structure) {
        if (structure == null || structure.getCode() == null || structure.getCode().isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(structure.getAgrementAMU())
                && List.of("PHARMACIE", "DEPOT PHARMACIE", "DEPOT PHARMACEUTIQUE").contains(structure.getTypeOfficiel());
    }

    private boolean matchesOfficialPharmacySearch(StructureSante pharmacie, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String normalizedSearch = normalizeForSearch(search);
        return containsNormalized(pharmacie.getCode(), normalizedSearch)
                || containsNormalized(pharmacie.getNom(), normalizedSearch)
                || containsNormalized(pharmacie.getAdresse(), normalizedSearch)
                || containsNormalized(pharmacie.getRegion(), normalizedSearch)
                || containsNormalized(pharmacie.getTypeOfficiel(), normalizedSearch);
    }

    private boolean matchesOfficialPharmacyLocation(StructureSante pharmacie, String location) {
        if (location == null || location.isBlank()) {
            return true;
        }
        String normalizedLocation = normalizeForSearch(location);
        return containsNormalized(pharmacie.getRegion(), normalizedLocation)
                || containsNormalized(pharmacie.getAdresse(), normalizedLocation)
                || containsNormalized(pharmacie.getNom(), normalizedLocation);
    }

    private boolean matchesSearch(Pharmacie pharmacie, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String normalizedSearch = normalizeForSearch(search);
        return containsNormalized(pharmacie.getCode(), normalizedSearch)
                || containsNormalized(pharmacie.getNom(), normalizedSearch)
                || containsNormalized(pharmacie.getTelephone(), normalizedSearch)
                || containsNormalized(pharmacie.getAdresse(), normalizedSearch)
                || containsNormalized(pharmacie.getQuartier(), normalizedSearch)
                || containsNormalized(pharmacie.getVille(), normalizedSearch);
    }

    private boolean matchesText(String value, String expected, boolean exact) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        String normalizedValue = normalizeForSearch(value);
        String normalizedExpected = normalizeForSearch(expected);
        return exact ? normalizedValue.equals(normalizedExpected) : normalizedValue.contains(normalizedExpected);
    }

    private boolean containsNormalized(String value, String expected) {
        return normalizeForSearch(value).contains(expected);
    }

    private String normalizeForSearch(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Sort mapOfficialPharmacySort(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return Sort.by("nom");
        }
        List<Sort.Order> orders = sort.stream()
                .map(order -> new Sort.Order(order.getDirection(), switch (order.getProperty()) {
                    case "code", "region", "createdAt", "updatedAt" -> order.getProperty();
                    default -> "nom";
                }))
                .toList();
        return Sort.by(orders);
    }

    private String firstText(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        final int earthRadiusKm = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
