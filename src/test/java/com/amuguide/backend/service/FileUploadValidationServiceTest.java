package com.amuguide.backend.service;

import com.amuguide.backend.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadValidationServiceTest {

    private final FileUploadValidationService service = new FileUploadValidationService();

    @Test
    void acceptsExpectedExtensionAndContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "structures.csv",
                "text/csv",
                "code,libelle".getBytes()
        );

        assertThatCode(() -> service.validate(file, "csv", Set.of("text/csv")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnexpectedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "structures.txt",
                "text/csv",
                "code,libelle".getBytes()
        );

        assertThatThrownBy(() -> service.validate(file, "csv", Set.of("text/csv")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(".csv");
    }

    @Test
    void rejectsUnexpectedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "structures.csv",
                "application/x-msdownload",
                "code,libelle".getBytes()
        );

        assertThatThrownBy(() -> service.validate(file, "csv", Set.of("text/csv")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Type de fichier invalide");
    }

    @Test
    void rejectsFilesLargerThanTenMegabytes() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "medicaments.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[(10 * 1024 * 1024) + 1]
        );

        assertThatThrownBy(() -> service.validate(
                file,
                "xlsx",
                Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("10 Mo");
    }
}
