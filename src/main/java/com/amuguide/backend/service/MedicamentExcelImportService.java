package com.amuguide.backend.service;

import com.amuguide.backend.dto.MedicamentImportResultDTO;
import com.amuguide.backend.entity.Medicament;
import com.amuguide.backend.exception.BadRequestException;
import com.amuguide.backend.repository.MedicamentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicamentExcelImportService {

    private static final String SHEET_NAME = "Worksheet";
    private static final int HEADER_ROW_INDEX = 0;

    private static final Map<String, BiConsumer<Medicament, String>> TEXT_MAPPERS = Map.of(
            "Code", Medicament::setCode,
            "Nom commercial", Medicament::setNom,
            "Dosage", Medicament::setDosage,
            "Statut", Medicament::setStatut,
            "DCI", Medicament::setDci,
            "Forme medicament", Medicament::setFormePharmaceutique,
            "Type medicament", Medicament::setTypeMedicament,
            "Groupe therapeutique", Medicament::setGroupeTherapeutique
    );

    private static final List<String> REQUIRED_HEADERS = List.of(
            "Code",
            "Nom commercial",
            "Quantite de presentation",
            "Unite de presentation",
            "Dosage",
            "Statut",
            "DCI",
            "Forme medicament",
            "Type medicament",
            "Groupe therapeutique",
            "Type emballage",
            "Base remboursement",
            "Prix public",
            "Taux prise en charge",
            "Part INAM",
            "Part beneficiaire"
    );

    private final MedicamentRepository medicamentRepository;
    private final FileUploadValidationService fileUploadValidationService;

    @Transactional
    public MedicamentImportResultDTO importFile(MultipartFile file) {
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

    private MedicamentImportResultDTO importSheet(Sheet sheet, Map<String, Integer> columns, DataFormatter formatter) {
        MedicamentImportResultDTO result = MedicamentImportResultDTO.builder()
                .detailsErreurs(new ArrayList<>())
                .build();
        Map<String, ImportedMedicament> validRows = new LinkedHashMap<>();

        for (int rowIndex = HEADER_ROW_INDEX + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isBlankRow(row, formatter)) {
                result.setIgnorees(result.getIgnorees() + 1);
                continue;
            }

            result.setLignesLues(result.getLignesLues() + 1);
            ImportedMedicament imported = readMedicament(row, columns, formatter, result);
            if (imported == null) {
                continue;
            }

            if (validRows.containsKey(imported.medicament().getCode())) {
                addLineError(result, row.getRowNum(), "Code duplique dans le fichier : " + imported.medicament().getCode());
                continue;
            }

            validRows.put(imported.medicament().getCode(), imported);
        }

        saveValidRows(validRows, result);
        return result;
    }

    private ImportedMedicament readMedicament(
            Row row,
            Map<String, Integer> columns,
            DataFormatter formatter,
            MedicamentImportResultDTO result) {
        Medicament medicament = new Medicament();
        List<String> lineErrors = new ArrayList<>();

        for (Map.Entry<String, BiConsumer<Medicament, String>> entry : TEXT_MAPPERS.entrySet()) {
            String value = readText(row, columns.get(entry.getKey()), formatter);
            entry.getValue().accept(medicament, value);
        }

        if (isBlank(medicament.getCode())) {
            lineErrors.add("Code obligatoire manquant");
        }
        if (isBlank(medicament.getNom())) {
            lineErrors.add("Nom commercial obligatoire manquant");
        }

        setBigDecimal(row, columns, formatter, "Base remboursement", medicament::setBaseRemboursement, lineErrors);
        setBigDecimal(row, columns, formatter, "Prix public", medicament::setPrixPublic, lineErrors);
        setBigDecimal(row, columns, formatter, "Part INAM", medicament::setPartInam, lineErrors);
        setBigDecimal(row, columns, formatter, "Part beneficiaire", medicament::setPartBeneficiaire, lineErrors);
        setDouble(row, columns, formatter, "Taux prise en charge", medicament::setTauxCouverture, lineErrors);

        medicament.setPrisEnCharge(true);
        medicament.setActif(true);

        if (!lineErrors.isEmpty()) {
            addLineErrors(result, row.getRowNum(), lineErrors);
            return null;
        }

        return new ImportedMedicament(row.getRowNum(), medicament);
    }

    private void saveValidRows(Map<String, ImportedMedicament> validRows, MedicamentImportResultDTO result) {
        if (validRows.isEmpty()) {
            return;
        }

        Map<String, Medicament> existingByCode = medicamentRepository.findByCodeIn(validRows.keySet()).stream()
                .collect(Collectors.toMap(Medicament::getCode, medicament -> medicament));
        List<Medicament> toSave = new ArrayList<>();

        for (ImportedMedicament imported : validRows.values()) {
            Medicament source = imported.medicament();
            Medicament target = existingByCode.get(source.getCode());
            if (target == null) {
                target = new Medicament();
                result.setAjoutees(result.getAjoutees() + 1);
            } else {
                result.setMisesAJour(result.getMisesAJour() + 1);
            }

            copyImportedValues(source, target);
            toSave.add(target);
        }

        medicamentRepository.saveAll(toSave);
    }

    private void copyImportedValues(Medicament source, Medicament target) {
        target.setCode(source.getCode());
        target.setNom(source.getNom());
        target.setDosage(source.getDosage());
        target.setStatut(source.getStatut());
        target.setDci(source.getDci());
        target.setFormePharmaceutique(source.getFormePharmaceutique());
        target.setTypeMedicament(source.getTypeMedicament());
        target.setGroupeTherapeutique(source.getGroupeTherapeutique());
        target.setBaseRemboursement(source.getBaseRemboursement());
        target.setPrixPublic(source.getPrixPublic());
        target.setTauxCouverture(source.getTauxCouverture());
        target.setPartInam(source.getPartInam());
        target.setPartBeneficiaire(source.getPartBeneficiaire());
        target.setPrisEnCharge(true);
        target.setActif(true);
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

    private void setBigDecimal(
            Row row,
            Map<String, Integer> columns,
            DataFormatter formatter,
            String header,
            Consumer<BigDecimal> setter,
            List<String> errors) {
        String value = readText(row, columns.get(header), formatter);
        if (isBlank(value)) {
            setter.accept(null);
            return;
        }

        try {
            setter.accept(new BigDecimal(normalizeNumber(value)));
        } catch (NumberFormatException ex) {
            errors.add(header + " invalide : " + value);
        }
    }

    private void setDouble(
            Row row,
            Map<String, Integer> columns,
            DataFormatter formatter,
            String header,
            Consumer<Double> setter,
            List<String> errors) {
        String value = readText(row, columns.get(header), formatter);
        if (isBlank(value)) {
            setter.accept(null);
            return;
        }

        try {
            setter.accept(Double.valueOf(normalizeNumber(value)));
        } catch (NumberFormatException ex) {
            errors.add(header + " invalide : " + value);
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

    private String normalizeNumber(String value) {
        String normalized = value.replace('\u00A0', ' ')
                .replace(" ", "")
                .replace(",", ".");
        if (normalized.endsWith(".0")) {
            return normalized;
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void addLineErrors(MedicamentImportResultDTO result, int zeroBasedRowNum, List<String> errors) {
        addLineError(result, zeroBasedRowNum, String.join("; ", errors));
    }

    private void addLineError(MedicamentImportResultDTO result, int zeroBasedRowNum, String error) {
        result.setErreurs(result.getErreurs() + 1);
        result.setIgnorees(result.getIgnorees() + 1);
        result.getDetailsErreurs().add("Ligne " + (zeroBasedRowNum + 1) + " : " + error);
    }

    private record ImportedMedicament(int rowNum, Medicament medicament) {
    }
}
