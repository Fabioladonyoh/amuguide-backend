package com.amuguide.backend.controller;

import com.amuguide.backend.dto.PageResponseDTO;
import com.amuguide.backend.dto.PharmacieDTO;
import com.amuguide.backend.service.PharmacieService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssurePharmacieControllerTest {

    @Mock
    private PharmacieService pharmacieService;

    @Test
    void returnsPaginatedOfficialPharmaciesMetadata() {
        AssurePharmacieController controller = new AssurePharmacieController(pharmacieService);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        PharmacieDTO dto = PharmacieDTO.builder()
                .id(10L)
                .code("PH001")
                .nom("PHARMACIE OFFICIELLE")
                .build();

        when(pharmacieService.listAccessible(eq(null), eq(null), eq(null), pageableCaptor.capture()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(dto), pageableCaptor.getValue(), 321));

        PageResponseDTO<PharmacieDTO> response = controller.pharmacies(0, 10, null, null, null, "nom", "asc");

        assertThat(response.getContent()).extracting(PharmacieDTO::getCode).containsExactly("PH001");
        assertThat(response.getTotalElements()).isEqualTo(321);
        assertThat(response.getTotalPages()).isEqualTo(33);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getPage()).isZero();
        assertThat(response.getNumber()).isZero();
    }
}
