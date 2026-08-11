package com.amuguide.backend.service;

import com.amuguide.backend.dto.HospitalisationTarifImportResultDTO;
import com.amuguide.backend.entity.HospitalisationTarif;
import com.amuguide.backend.exception.BadRequestException;
import com.amuguide.backend.repository.HospitalisationTarifRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HospitalisationTarifImportService {

    private static final String SHEET_NAME = "Worksheet";
    private static final int HEADER_ROW_INDEX = 0;
    private static final List<String> REQUIRED_HEADERS = List.of(
            "Categorie",
            "Chambre",
            "Population",
            "Type prestataire",
            "Date debut",
            "Taux remboursement",
            "Premier semaine",
            "Deuxieme semaine",
            "A partir de la troisieme semaine"
    );

    private final HospitalisationTarifRepository hospitalisationTarifRepository;
    private final FileUploadValidationService fileUploadValidationService;

    @Transactional
    public HospitalisationTarifImportResultDTO importFile(MultipartFile file) {
        fileUploadValidationService.validate(file, "xlsx", java.util.Set.of(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/octet-stream"
        ));

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                throw new BadRequestException("La feuille Excel attendue est introuvable : " + SHEET_NAME);
            }
            DataFormatter formatter = new DataFormatter(Locale.FRANCE);
            Map<String, Integer> columns = readAndValidateHeaders(sheet, formatter);
            return importSheet(sheet, columns, formatter);
        } catch (IOException ex) {
            throw new BadRequestException("Impossible de lire le fichier Excel : " + ex.getMessage());
        }
    }

    private HospitalisationTarifImportResultDTO importSheet(Sheet sheet, Map<String, Integer> columns, DataFormatter formatter) {
        HospitalisationTarifImportResultDTO result = HospitalisationTarifImportResultDTO.builder()
                .detailsErreurs(new ArrayList<>())
                .build();
        Map<String, ImportedHospitalisationTarif> validRows = new LinkedHashMap<>();

        for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isBlankRow(row, formatter)) {
                result.setIgnorees(result.getIgnorees() + 1);
                continue;
            }
            result.setLignesLues(result.getLignesLues() + 1);

            ImportedHospitalisationTarif imported = readTarif(row, columns, formatter, result);
            if (imported == null) {
                continue;
            }
            String key = naturalKey(imported.tarif());
            if (validRows.containsKey(key)) {
                addLineError(result, row.getRowNum(), "Ligne dupliquee dans le fichier pour la meme cle officielle");
                continue;
            }
            validRows.put(key, imported);
        }

        saveValidRows(validRows, result);
        return result;
    }

    private ImportedHospitalisationTarif readTarif(
            Row row,
            Map<String, Integer> columns,
            DataFormatter formatter,
            HospitalisationTarifImportResultDTO result) {
        List<String> errors = new ArrayList<>();
        HospitalisationTarif tarif = new HospitalisationTarif();

        tarif.setCategorie(readText(row, columns.get("Categorie"), formatter));
        tarif.setChambre(readText(row, columns.get("Chambre"), formatter));
        tarif.setPopulation(readText(row, columns.get("Population"), formatter));
        tarif.setTypePrestataire(readText(row, columns.get("Type prestataire"), formatter));
        tarif.setDateDebut(readDate(row.getCell(columns.get("Date debut"), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL), formatter, errors));
        tarif.setTauxRemboursement(readDouble(row, columns.get("Taux remboursement"), formatter, "Taux remboursement", errors));
        tarif.setPremiereSemaine(readText(row, columns.get("Premier semaine"), formatter));
        tarif.setDeuxiemeSemaine(readText(row, columns.get("Deuxieme semaine"), formatter));
        tarif.setAPartirTroisiemeSemaine(readText(row, columns.get("A partir de la troisieme semaine"), formatter));

        requireText(tarif.getCategorie(), "Categorie", errors);
        requireText(tarif.getChambre(), "Chambre", errors);
        requireText(tarif.getPopulation(), "Population", errors);
        requireText(tarif.getTypePrestataire(), "Type prestataire", errors);
        if (tarif.getDateDebut() == null) {
            errors.add("Date debut obligatoire manquante ou invalide");
        }
        if (tarif.getTauxRemboursement() == null) {
            errors.add("Taux remboursement obligatoire manquant ou invalide");
        }

        if (!errors.isEmpty()) {
            addLineError(result, row.getRowNum(), String.join("; ", errors));
            return null;
        }
        return new ImportedHospitalisationTarif(tarif);
    }

    private void saveValidRows(Map<String, ImportedHospitalisationTarif> validRows, HospitalisationTarifImportResultDTO result) {
        for (ImportedHospitalisationTarif imported : validRows.values()) {
            HospitalisationTarif source = imported.tarif();
            HospitalisationTarif target = hospitalisationTarifRepository.findByNaturalKey(
                    source.getCategorie(),
                    source.getChambre(),
                    source.getPopulation(),
                    source.getTypePrestataire(),
                    source.getDateDebut()
            ).orElseGet(HospitalisationTarif::new);

            if (target.getId() == null) {
                result.setAjoutees(result.getAjoutees() + 1);
            } else {
                result.setMisesAJour(result.getMisesAJour() + 1);
            }
            copyValues(source, target);
            hospitalisationTarifRepository.save(target);
        }
    }

    private void copyValues(HospitalisationTarif source, HospitalisationTarif target) {
        target.setCategorie(source.getCategorie());
        target.setChambre(source.getChambre());
        target.setPopulation(source.getPopulation());
        target.setTypePrestataire(source.getTypePrestataire());
        target.setDateDebut(source.getDateDebut());
        target.setTauxRemboursement(source.getTauxRemboursement());
        target.setPremiereSemaine(source.getPremiereSemaine());
        target.setDeuxiemeSemaine(source.getDeuxiemeSemaine());
        target.setAPartirTroisiemeSemaine(source.getAPartirTroisiemeSemaine());
    }

    private Map<String, Integer> readAndValidateHeaders(Sheet sheet, DataFormatter formatter) {
        Row headerRow = sheet.getRow(HEADER_ROW_INDEX);
        if (headerRow == null) {
            throw new BadRequestException("La ligne d'en-tetes Excel est introuvable");
        }
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : headerRow) {
            String header = readText(cell, formatter);
            if (!isBlank(header)) {
                columns.put(header, cell.getColumnIndex());
            }
        }
        List<String> missingHeaders = REQUIRED_HEADERS.stream()
                .filter(header -> !columns.containsKey(header))
                .toList();
        if (!missingHeaders.isEmpty()) {
            throw new BadRequestException("Colonnes Excel manquantes : " + String.join(", ", missingHeaders));
        }
        return columns;
    }

    private LocalDate readDate(Cell cell, DataFormatter formatter, List<String> errors) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String value = readText(cell, formatter);
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            errors.add("Date debut invalide : " + value);
            return null;
        }
    }

    private Double readDouble(Row row, int columnIndex, DataFormatter formatter, String header, List<String> errors) {
        String value = readText(row, columnIndex, formatter);
        if (isBlank(value)) {
            return null;
        }
        try {
            return Double.valueOf(value.replace('\u00A0', ' ').replace(" ", "").replace(",", "."));
        } catch (NumberFormatException ex) {
            errors.add(header + " invalide : " + value);
            return null;
        }
    }

    private String readText(Row row, int columnIndex, DataFormatter formatter) {
        if (row == null) {
            return null;
        }
        return readText(row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL), formatter);
    }

    private String readText(Cell cell, DataFormatter formatter) {
        if (cell == null) {
            return null;
        }
        return clean(formatter.formatCellValue(cell));
    }

    private boolean isBlankRow(Row row, DataFormatter formatter) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            if (!isBlank(readText(cell, formatter))) {
                return false;
            }
        }
        return true;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
        return cleaned.isBlank() ? null : cleaned;
    }

    private void requireText(String value, String header, List<String> errors) {
        if (isBlank(value)) {
            errors.add(header + " obligatoire manquant");
        }
    }

    private String naturalKey(HospitalisationTarif tarif) {
        return String.join("|",
                tarif.getCategorie().toLowerCase(Locale.ROOT),
                tarif.getChambre().toLowerCase(Locale.ROOT),
                tarif.getPopulation().toLowerCase(Locale.ROOT),
                tarif.getTypePrestataire().toLowerCase(Locale.ROOT),
                tarif.getDateDebut().toString());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void addLineError(HospitalisationTarifImportResultDTO result, int zeroBasedRowNum, String error) {
        result.setErreurs(result.getErreurs() + 1);
        result.setIgnorees(result.getIgnorees() + 1);
        result.getDetailsErreurs().add("Ligne " + (zeroBasedRowNum + 1) + " : " + error);
    }

    private record ImportedHospitalisationTarif(HospitalisationTarif tarif) {
    }
}
