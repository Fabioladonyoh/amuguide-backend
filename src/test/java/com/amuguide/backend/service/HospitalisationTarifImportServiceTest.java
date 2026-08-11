package com.amuguide.backend.service;

import com.amuguide.backend.dto.HospitalisationTarifImportResultDTO;
import com.amuguide.backend.entity.HospitalisationTarif;
import com.amuguide.backend.repository.HospitalisationTarifRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HospitalisationTarifImportServiceTest {

    @Mock
    private HospitalisationTarifRepository hospitalisationTarifRepository;

    @Test
    void importsOfficialWorkbookRowsAndPreservesExclusValues() throws IOException {
        HospitalisationTarifImportService service = new HospitalisationTarifImportService(
                hospitalisationTarifRepository,
                new FileUploadValidationService()
        );
        when(hospitalisationTarifRepository.findByNaturalKey(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        HospitalisationTarifImportResultDTO result = service.importFile(file());

        assertThat(result.getLignesLues()).isEqualTo(106);
        assertThat(result.getAjoutees()).isEqualTo(106);
        assertThat(result.getMisesAJour()).isZero();
        assertThat(result.getErreurs()).isZero();

        ArgumentCaptor<HospitalisationTarif> captor = ArgumentCaptor.forClass(HospitalisationTarif.class);
        org.mockito.Mockito.verify(hospitalisationTarifRepository, org.mockito.Mockito.times(106)).save(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(tarif -> {
            assertThat(tarif.getTypePrestataire()).isEqualTo("CHR");
            assertThat(tarif.getTauxRemboursement()).isEqualTo(90.0);
            assertThat(tarif.getDeuxiemeSemaine()).isEqualTo("Exclus");
            assertThat(tarif.getAPartirTroisiemeSemaine()).isEqualTo("Exclus");
        });
        assertThat(captor.getAllValues()).anySatisfy(tarif ->
                assertThat(tarif.getTauxRemboursement()).isEqualTo(100.0));
    }

    @Test
    void importIsIdempotentWhenNaturalKeyAlreadyExists() throws IOException {
        HospitalisationTarifImportService service = new HospitalisationTarifImportService(
                hospitalisationTarifRepository,
                new FileUploadValidationService()
        );
        when(hospitalisationTarifRepository.findByNaturalKey(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(HospitalisationTarif.builder().id(1L).build()));

        HospitalisationTarifImportResultDTO result = service.importFile(file());

        assertThat(result.getLignesLues()).isEqualTo(106);
        assertThat(result.getAjoutees()).isZero();
        assertThat(result.getMisesAJour()).isEqualTo(106);
        assertThat(result.getErreurs()).isZero();
    }

    private MockMultipartFile file() throws IOException {
        try (InputStream inputStream = java.nio.file.Files.newInputStream(java.nio.file.Path.of("data", "hospitalisation.xlsx"))) {
            return new MockMultipartFile(
                    "file",
                    "hospitalisation.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    inputStream
            );
        }
    }
}
