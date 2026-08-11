package com.amuguide.backend.config;

import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.enums.CategorieActe;
import com.amuguide.backend.repository.PrestationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ConditionalOnProperty(name = "amuguide.seed.reference-prestations.enabled", havingValue = "true")
@RequiredArgsConstructor
public class PrestationReferenceDataInitializer implements CommandLineRunner {

    private final PrestationRepository prestationRepository;

    private static final List<ReferencePrestation> REFERENCE_PRESTATIONS = List.of(
            prestation("CONSULTATION_GENERALE", "Consultation générale", CategorieActe.CONSULTATION, 80.0,
                    List.of("CONS001"), List.of("consultation generale", "consultation")),
            prestation("CONSULTATION_ENFANT_MOINS_5_ANS", "Consultation au centre de santé public pour les enfants de moins de 5 ans", CategorieActe.CONSULTATION, 100.0,
                    List.of(), List.of("consultation enfant moins 5 ans", "consultation enfants moins de 5 ans")),
            prestation("CONSULTATION_SPECIALITE", "Consultation de spécialité", CategorieActe.CONSULTATION, 80.0,
                    List.of(), List.of("consultation specialite", "consultation specialisee")),
            prestation("HOSPITALISATION_SEJOUR", "Hospitalisation (frais de séjour)", CategorieActe.HOSPITALISATION, 90.0,
                    List.of("HOSP001"), List.of("hospitalisation", "hospitalisation standard", "frais de sejour")),
            prestation("EXAMENS_LABORATOIRE", "Examens de laboratoire", CategorieActe.BIOLOGIE, 80.0,
                    List.of("BIO001"), List.of("analyses biologiques", "examens de laboratoire", "laboratoire")),
            prestation("INTERVENTION_CHIRURGICALE", "Intervention chirurgicale", CategorieActe.CHIRURGIE, 90.0,
                    List.of("CHIR001"), List.of("chirurgie generale", "intervention chirurgicale")),
            prestation("PETITE_CHIRURGIE", "Petite chirurgie", CategorieActe.CHIRURGIE, 80.0,
                    List.of(), List.of("petite chirurgie")),
            prestation("SOINS_INFIRMIERS", "Soins infirmiers", CategorieActe.CONSULTATION, 80.0,
                    List.of(), List.of("soins infirmiers")),
            prestation("ACCOUCHEMENT_SIMPLE", "Accouchement simple (acte)", CategorieActe.MATERNITE, 100.0,
                    List.of(), List.of("accouchement simple")),
            prestation("ACCOUCHEMENT_COMPLIQUE", "Accouchement compliqué (acte)", CategorieActe.MATERNITE, 100.0,
                    List.of(), List.of("accouchement complique")),
            prestation("CESARIENNE", "Césarienne (acte)", CategorieActe.MATERNITE, 100.0,
                    List.of(), List.of("cesarienne", "ces-arienne")),
            prestation("SOINS_OPHTALMOLOGIQUES", "Soins ophtalmologiques", CategorieActe.OPHTALMOLOGIE, 80.0,
                    List.of("OPHT001"), List.of("consultation ophtalmologique", "soins ophtalmologie", "soins ophtalmologiques")),
            prestation("SOINS_DENTAIRES", "Soins dentaires", CategorieActe.DENTAIRE, 80.0,
                    List.of(), List.of("soins dentaires")),
            prestation("POCHE_SANG", "Poche de sang", CategorieActe.BIOLOGIE, 80.0,
                    List.of(), List.of("poche de sang")),
            prestation("ECHOGRAPHIE", "Échographie", CategorieActe.RADIOLOGIE, 80.0,
                    List.of(), List.of("echographie")),
            prestation("RADIOLOGIE", "Radiologie", CategorieActe.RADIOLOGIE, 80.0,
                    List.of("RAD001"), List.of("radiologie", "radiologie standard")),
            prestation("MEDICAMENTS", "Médicaments", CategorieActe.MEDICAMENT, 80.0,
                    List.of("MED001"), List.of("medicaments essentiels", "medicaments")),
            prestation("CONSULTATION_PRENATALE", "Consultation prénatale", CategorieActe.CONSULTATION, 80.0,
                    List.of("MAT001"), List.of("suivi grossesse et accouchement", "consultation prenatale"))
    );

    @Override
    @Transactional
    public void run(String... args) {
        List<Prestation> before = prestationRepository.findAll();
        System.out.printf("%n[Prestations AMU] Avant mise a jour : %d prestation(s)%n", before.size());
        before.stream()
                .sorted(Comparator.comparing(Prestation::getCodeActe, Comparator.nullsLast(String::compareToIgnoreCase)))
                .forEach(prestation -> System.out.printf("  %-35s | %-80s | %.0f | actif=%s%n",
                        prestation.getCodeActe(),
                        prestation.getNomActe(),
                        prestation.getTauxCouverture(),
                        Boolean.TRUE.equals(prestation.getPrisEnCharge())));

        Set<String> referenceCodes = new LinkedHashSet<>();
        for (ReferencePrestation reference : REFERENCE_PRESTATIONS) {
            referenceCodes.add(reference.codeActe());
            upsert(reference);
        }

        List<Prestation> deactivated = prestationRepository.findAll().stream()
                .filter(prestation -> Boolean.TRUE.equals(prestation.getPrisEnCharge()))
                .filter(prestation -> !referenceCodes.contains(prestation.getCodeActe()))
                .toList();
        deactivated.forEach(prestation -> {
            prestation.setPrisEnCharge(false);
            prestationRepository.save(prestation);
            System.out.printf("  Ancienne prestation desactivee : %s (%s)%n",
                    prestation.getNomActe(), prestation.getCodeActe());
        });

        List<Prestation> after = prestationRepository.findAll();
        System.out.printf("[Prestations AMU] Apres mise a jour : %d prestation(s), dont %d active(s)%n",
                after.size(), prestationRepository.countByPrisEnChargeTrue());
    }

    private void upsert(ReferencePrestation reference) {
        Prestation prestation = findExisting(reference).orElseGet(Prestation::new);
        boolean isNew = prestation.getIdPrestation() == null;

        prestation.setCodeActe(reference.codeActe());
        prestation.setNomActe(reference.nomActe());
        prestation.setCategorie(reference.categorie());
        prestation.setTauxCouverture(reference.tauxCouverture());
        prestation.setPrisEnCharge(true);
        if (isNew) {
            prestation.setDescription("");
            prestation.setConditionsPriseEnCharge("");
            prestation.setDocumentsRequis("");
        }

        prestationRepository.save(prestation);
        System.out.printf("  %-35s %s (%.0f)%n",
                reference.codeActe(),
                isNew ? "cree" : "mis a jour",
                reference.tauxCouverture());
    }

    private Optional<Prestation> findExisting(ReferencePrestation reference) {
        Optional<Prestation> byCode = prestationRepository.findByCodeActe(reference.codeActe());
        if (byCode.isPresent()) {
            return byCode;
        }

        for (String legacyCode : reference.legacyCodes()) {
            Optional<Prestation> byLegacyCode = prestationRepository.findByCodeActe(legacyCode);
            if (byLegacyCode.isPresent()) {
                return byLegacyCode;
            }
        }

        Set<String> aliases = new LinkedHashSet<>();
        aliases.add(reference.nomActe());
        aliases.addAll(reference.aliases());
        Set<String> normalizedAliases = aliases.stream()
                .map(this::normalize)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return prestationRepository.findAll().stream()
                .filter(prestation -> normalizedAliases.contains(normalize(prestation.getNomActe())))
                .findFirst();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('-', ' ')
                .replaceAll("[^a-z0-9'\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static ReferencePrestation prestation(String codeActe, String nomActe, CategorieActe categorie,
                                                  Double tauxCouverture, List<String> legacyCodes, List<String> aliases) {
        return new ReferencePrestation(codeActe, nomActe, categorie, tauxCouverture, legacyCodes, aliases);
    }

    private record ReferencePrestation(String codeActe, String nomActe, CategorieActe categorie,
                                       Double tauxCouverture, List<String> legacyCodes, List<String> aliases) {
    }
}
