package com.amuguide.backend.controller;

import com.amuguide.backend.dto.HospitalisationTarifImportResultDTO;
import com.amuguide.backend.service.HospitalisationTarifImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/hospitalisations")
@RequiredArgsConstructor
public class AdminHospitalisationTarifImportController {

    private final HospitalisationTarifImportService hospitalisationTarifImportService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HospitalisationTarifImportResultDTO importHospitalisations(@RequestPart("file") MultipartFile file) {
        return hospitalisationTarifImportService.importFile(file);
    }
}
