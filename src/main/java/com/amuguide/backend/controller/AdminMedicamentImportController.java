package com.amuguide.backend.controller;

import com.amuguide.backend.dto.MedicamentImportResultDTO;
import com.amuguide.backend.service.MedicamentExcelImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/medicaments")
@RequiredArgsConstructor
public class AdminMedicamentImportController {

    private final MedicamentExcelImportService medicamentExcelImportService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MedicamentImportResultDTO importMedicaments(@RequestPart("file") MultipartFile file) {
        return medicamentExcelImportService.importFile(file);
    }
}
