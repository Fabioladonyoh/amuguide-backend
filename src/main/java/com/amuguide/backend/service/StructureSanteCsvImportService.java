package com.amuguide.backend.service;

import com.amuguide.backend.dto.StructureSanteImportResultDTO;
import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.TypeStructure;
import com.amuguide.backend.exception.BadRequestException;
import com.amuguide.backend.repository.StructureSanteRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StructureSanteCsvImportService {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "code",
            "libelle",
            "type_officiel",
            "region",
            "adresse"
    );

    private static final Map<String, TypeStructure> TYPE_STRUCTURE_BY_OFFICIAL_TYPE = Map.ofEntries(
            Map.entry("PHARMACIE", TypeStructure.PHARMACIE),
            Map.entry("DEPOT PHARMACIE", TypeStructure.PHARMACIE),
            Map.entry("DEPOT PHARMACEUTIQUE", TypeStructure.PHARMACIE),
            Map.entry("CHU", TypeStructure.HOPITAL),
            Map.entry("CHR", TypeStructure.HOPITAL),
            Map.entry("HOPITAL DE DISTRICT", TypeStructure.HOPITAL),
            Map.entry("NIVEAU DE SOINS 4", TypeStructure.HOPITAL),
            Map.entry("POLYCLINIQUE", TypeStructure.CLINIQUE),
            Map.entry("USP TYPE I", TypeStructure.CENTRE_DE_SANTE),
            Map.entry("USP TYPE II", TypeStructure.CENTRE_DE_SANTE),
            Map.entry("INFIRMERIE", TypeStructure.CENTRE_DE_SANTE),
            Map.entry("CENTRE D'APPAREILLAGE", TypeStructure.CENTRE_DE_SANTE),
            Map.entry("CENTRE DE LUNETTERIE", TypeStructure.CENTRE_DE_SANTE)
    );

    private final StructureSanteRepository structureSanteRepository;
    private final FileUploadValidationService fileUploadValidationService;

    @Transactional
    public StructureSanteImportResultDTO importFile(MultipartFile file) {
        fileUploadValidationService.validate(file, "csv", java.util.Set.of(
                "text/csv",
                "application/csv",
                "application/vnd.ms-excel",
                "text/plain",
                "application/octet-stream"
        ));

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(false)
                     .get()
                     .parse(reader)) {
            validateHeaders(parser);
            return importRecords(parser);
        } catch (IOException ex) {
            throw new BadRequestException("Impossible de lire le fichier CSV : " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Fichier CSV invalide : " + ex.getMessage());
        }
    }

    private StructureSanteImportResultDTO importRecords(CSVParser parser) {
        StructureSanteImportResultDTO result = StructureSanteImportResultDTO.builder()
                .detailsErreurs(new ArrayList<>())
                .build();
        Map<String, ImportedStructure> validRows = new LinkedHashMap<>();

        for (CSVRecord record : parser) {
            if (isBlankRecord(record)) {
                result.setIgnorees(result.getIgnorees() + 1);
                continue;
            }

            result.setLignesLues(result.getLignesLues() + 1);
            ImportedStructure imported = readStructure(record, result);
            if (imported == null) {
                continue;
            }

            if (validRows.containsKey(imported.code())) {
                addLineError(result, record.getRecordNumber(), "Code duplique dans le fichier : " + imported.code());
                continue;
            }

            validRows.put(imported.code(), imported);
        }

        saveValidRows(validRows, result);
        return result;
    }

    private ImportedStructure readStructure(CSVRecord record, StructureSanteImportResultDTO result) {
        List<String> lineErrors = new ArrayList<>();
        String code = value(record, "code");
        String libelle = value(record, "libelle");
        String typeOfficiel = value(record, "type_officiel");
        String region = value(record, "region");
        String adresse = value(record, "adresse");

        if (isBlank(code)) {
            lineErrors.add("Code obligatoire manquant");
        }
        if (isBlank(libelle)) {
            lineErrors.add("Libelle obligatoire manquant");
        }

        TypeStructure mappedType = null;
        if (isBlank(typeOfficiel)) {
            lineErrors.add("Type officiel obligatoire manquant");
        } else {
            mappedType = TYPE_STRUCTURE_BY_OFFICIAL_TYPE.get(normalizeTypeKey(typeOfficiel));
            if (mappedType == null) {
                lineErrors.add("Type officiel inconnu : " + typeOfficiel);
            }
        }

        if (!lineErrors.isEmpty()) {
            addLineError(result, record.getRecordNumber(), String.join("; ", lineErrors));
            return null;
        }

        return new ImportedStructure(code, libelle, typeOfficiel, region, adresse, mappedType);
    }

    private void saveValidRows(Map<String, ImportedStructure> validRows, StructureSanteImportResultDTO result) {
        if (validRows.isEmpty()) {
            return;
        }

        Map<String, StructureSante> existingByCode = new HashMap<>();
        for (StructureSante structure : structureSanteRepository.findByCodeIn(validRows.keySet())) {
            if (isBlank(structure.getCode())) {
                continue;
            }
            existingByCode.putIfAbsent(structure.getCode(), structure);
        }

        List<StructureSante> toSave = new ArrayList<>();
        for (ImportedStructure imported : validRows.values()) {
            StructureSante target = existingByCode.get(imported.code());
            if (target == null) {
                target = new StructureSante();
                result.setAjoutees(result.getAjoutees() + 1);
            } else {
                result.setMisesAJour(result.getMisesAJour() + 1);
            }

            copyImportedValues(imported, target);
            toSave.add(target);
        }

        structureSanteRepository.saveAll(toSave);
    }

    private void copyImportedValues(ImportedStructure source, StructureSante target) {
        target.setCode(source.code());
        target.setNom(source.libelle());
        target.setTypeOfficiel(source.typeOfficiel());
        target.setRegion(source.region());
        target.setAdresse(source.adresse());
        target.setType(source.type());
        target.setAgrementAMU(true);
        target.setActif(true);
    }

    private void validateHeaders(CSVParser parser) {
        Map<String, String> headersByNormalizedName = parser.getHeaderMap().keySet().stream()
                .collect(Collectors.toMap(this::normalizeHeader, header -> header, (first, second) -> first));

        List<String> missingHeaders = REQUIRED_HEADERS.stream()
                .filter(header -> !headersByNormalizedName.containsKey(header))
                .toList();
        if (!missingHeaders.isEmpty()) {
            throw new BadRequestException("Colonnes CSV manquantes : " + String.join(", ", missingHeaders));
        }
    }

    private String value(CSVRecord record, String expectedHeader) {
        return record.toMap().entrySet().stream()
                .filter(entry -> normalizeHeader(entry.getKey()).equals(expectedHeader))
                .map(Map.Entry::getValue)
                .findFirst()
                .map(this::clean)
                .orElse(null);
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
        return cleaned.isBlank() ? null : cleaned;
    }

    private String normalizeHeader(String header) {
        String normalized = clean(header);
        if (normalized == null) {
            return "";
        }
        return normalized.replace("\uFEFF", "").toLowerCase(Locale.ROOT);
    }

    private String normalizeTypeKey(String typeOfficiel) {
        return clean(typeOfficiel).toUpperCase(Locale.ROOT);
    }

    private boolean isBlankRecord(CSVRecord record) {
        return record.toMap().values().stream().allMatch(this::isBlank);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void addLineError(StructureSanteImportResultDTO result, long recordNumber, String error) {
        result.setErreurs(result.getErreurs() + 1);
        result.setIgnorees(result.getIgnorees() + 1);
        result.getDetailsErreurs().add("Ligne " + (recordNumber + 1) + " : " + error);
    }

    private record ImportedStructure(
            String code,
            String libelle,
            String typeOfficiel,
            String region,
            String adresse,
            TypeStructure type) {
    }
}
