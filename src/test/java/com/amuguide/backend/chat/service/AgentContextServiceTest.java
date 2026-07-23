package com.amuguide.backend.chat.service;

import com.amuguide.backend.chat.nlp.ChatIntent;
import com.amuguide.backend.chat.nlp.IntentDetectionResult;
import com.amuguide.backend.repository.PrestationRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentContextServiceTest {

    @Mock
    private PrestationRepository prestationRepository;

    @Mock
    private StructureSanteRepository structureSanteRepository;

    @Mock
    private MedicationKnowledgeService medicationKnowledgeService;

    @InjectMocks
    private AgentContextService service;

    @Test
    void medicationIntentDoesNotLoadPrestationsOrStructures() {
        IntentDetectionResult detection = IntentDetectionResult.builder()
                .intent(ChatIntent.MEDICATION_SEARCH)
                .normalizedMessage("donne moi la liste des medicaments")
                .build();
        when(medicationKnowledgeService.search(detection.getNormalizedMessage(), 5)).thenReturn(List.of());

        AgentContext context = service.build(detection);

        assertThat(context.prestations()).isEmpty();
        assertThat(context.structures()).isEmpty();
        verifyNoInteractions(prestationRepository, structureSanteRepository);
    }
}
