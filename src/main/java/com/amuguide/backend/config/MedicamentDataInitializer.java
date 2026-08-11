package com.amuguide.backend.config;

import com.amuguide.backend.chat.service.MedicationKnowledgeService;
import com.amuguide.backend.entity.Medicament;
import com.amuguide.backend.repository.MedicamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.IntStream;

@Component
@Order(30)
@ConditionalOnProperty(name = "amuguide.seed.legacy-medicaments.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MedicamentDataInitializer implements CommandLineRunner {

    private final MedicamentRepository medicamentRepository;
    private final MedicationKnowledgeService medicationKnowledgeService;

    @Override
    public void run(String... args) {
        if (medicamentRepository.count() > 0) {
            return;
        }

        List<String> labels = medicationKnowledgeService.list(medicationKnowledgeService.count());
        List<Medicament> medicaments = IntStream.range(0, labels.size())
                .mapToObj(index -> Medicament.builder()
                        .code("REF-MED-" + String.format("%04d", index + 1))
                        .nom(labels.get(index))
                        .prisEnCharge(true)
                        .actif(true)
                        .build())
                .toList();
        medicamentRepository.saveAll(medicaments);
    }
}
