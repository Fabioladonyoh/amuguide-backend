package com.amuguide.backend.controller;

import com.amuguide.backend.dto.StructureSanteImportResultDTO;
import com.amuguide.backend.service.StructureSanteCsvImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/structures")
@RequiredArgsConstructor
public class AdminStructureImportController {

    private final StructureSanteCsvImportService structureSanteCsvImportService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StructureSanteImportResultDTO importStructures(@RequestPart("file") MultipartFile file) {
        return structureSanteCsvImportService.importFile(file);
    }
}
