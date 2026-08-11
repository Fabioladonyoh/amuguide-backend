package com.amuguide.backend.config;

import com.amuguide.backend.entity.Pharmacie;
import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.TypeStructure;
import com.amuguide.backend.repository.PharmacieRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
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
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@ConditionalOnProperty(name = "amuguide.seed.partner-pharmacies.enabled", havingValue = "true")
@RequiredArgsConstructor
public class PharmacyPartnerDataInitializer implements CommandLineRunner {

    private static final String TO_VERIFY = "Donnee a verifier";

    private final PharmacieRepository pharmacieRepository;
    private final StructureSanteRepository structureRepository;

    private static final List<PharmacyData> PHARMACIES = List.of(
            pharmacy("Pharmacie Bon Pasteur", "22 21 13 67", "44 Av. de la Liberation, en face de Brother Home", "Lome"),
            pharmacy("Pharmacie 31eme Arrondissement", "22 21 52 27", "Bd. du 13 Janvier, pres de l'immeuble FIATA", "Lome"),
            pharmacy("Pharmacie Biova", "22 34 50 93", "Bd. Houphouet-Boigny", "Lome"),
            pharmacy("Pharmacie Port", "22 27 61 88", "Face Hotel Sarakawa", "Lome"),
            pharmacy("Pharmacie Ocam", "22 21 62 05", "Rue de l'Entente", "Lome"),
            pharmacy("Pharmacie Horizon", "22 20 42 42", "165, Bd. du 13 Janvier, Nyekonakpoe, face aux Sapeurs-Pompiers, a cote de l'immeuble A.AC.", "Lome"),
            pharmacy("Pharmacie Justine", "96 80 09 31", "291, Bd. des Armees, Tokoin Habitat", "Lome"),
            pharmacy("Pharmacie Agbegnigan", "70 20 00 00", "Tokoin Ramco-Gbadago, Av. de la Liberation, pres du Pret-a-Manger", "Lome"),
            pharmacy("Pharmacie Bon Secours", "70 45 76 74", "Rue du Grand College du Plateau, Casablanca", "Lome"),
            pharmacy("Pharmacie Notre Dame", "96 80 10 12", "Route de l'Aeroport, entre la Foire Togo 2000 et l'aeroport", "Lome", "Pharmacie Notre-Dame"),
            pharmacy("Pharmacie La Prosperite", "96 80 09 9", "Bd. Eyadema, entre l'immeuble EDA OBA et la Direction de la Police judiciaire (DPJ)", "Lome", true),
            pharmacy("Pharmacie Madina", "91 18 33 33", "Wuiti, en face de la cite de la CNSS", "Lome", true),
            pharmacy("Pharmacie Saint-Pierre", "22 26 19 73", "Sagboville, Hedzranawe, boulevard Hao", "Lome", "Pharmacie St Pierre"),
            pharmacy("Pharmacie Deo Gratias", "96 28 57 13", "Rue Notre-Dame-de-la-Misericorde, Kegue Dingble", "Lome"),
            pharmacy("Pharmacie Peuple", "22 61 37 29", "Rue Santiagou, pres du marche Nukafu, 06 BP 61217 Lome 06", "Lome"),
            pharmacy("Pharmacie Ba-Ayeta", "97 72 69 69", "Kegue, Zogbedji, non loin de la station NRL, ancienne station Ouando", "Lome"),
            pharmacy("Pharmacie O Grain d'Or", "71 90 11 66", "Ahadji-Kpota, rue Carrefour Zorrobar, Grand Contournement, Lome", "Lome"),
            pharmacy("Pharmacie Sepopo", "70 34 65 65", "Adakpame, Grand Contournement, rond-point Saweto, non loin de la station Somayaf", "Lome"),
            pharmacy("Pharmacie 2000", "96 37 94 25", "Be-Kpota, pres du marche Dzifa", "Lome"),
            pharmacy("Pharmacie Bethel", "22 25 23 70", "Adidogome Soviepe, bd. du 30 Aout, face a Orabank et Banque Atlantique", "Lome"),
            pharmacy("Pharmacie des Ecoles", "22 51 75 75", "Face au lycee technique d'Adidogome, pres du CEG, route de Kpalime", "Lome"),
            pharmacy("Pharmacie El-Nissi", "99 73 39 32", "Route Lome-Kpalime, carrefour Apedokoe-Gbomame, a 200 m de la station Total d'Apedokoe", "Lome", "Pharmacie El Nissi"),
            pharmacy("Pharmacie Hosanna", "97 77 69 59", "Carrefour Sagbado-Semekonawo, en face de la station-service Sanol", "Lome"),
            pharmacy("Pharmacie Magnificat", "70 44 51 59", "Adidogome Yokoe-Agblegan, rue de la Pampa, a 100 m du palais royal de Yokoe", "Lome"),
            pharmacy("Pharmacie GreenRx", "92 96 19 19", "Segbe, dans l'immeuble Mabiz Plaza, non loin du rond-point Douane", "Lome"),
            pharmacy("Pharmacie Mathilda", "93 02 52 12", "Route Patasse, Lomegan, ODEF", "Lome"),
            pharmacy("Pharmacie El-Shadai", "22 51 44 25", "Face a l'Ecole de theologie ESTAO", "Lome", "Pharmacie El Shadai"),
            pharmacy("Pharmacie Enouli", "22 55 86 46", "Derriere la gare routiere d'Agbalepedogan, 05 BP 633", "Lome"),
            pharmacy("Pharmacie Le Galien", "22 51 71 71", "Rue pavee d'Adidoadin", "Lome"),
            pharmacy("Pharmacie des Roses", "70 42 37 72", "Agoe-Vakpossito, pres de l'entreprise de l'Union", "Lome"),
            pharmacy("Pharmacie Betania", "96 80 10 11", "Rue Sito, Totsi-Glenkome, non loin de la salle des Temoins de Jehovah", "Lome"),
            pharmacy("Pharmacie Volontas Dei", "70 42 23 60", "Avedji, carrefour Sun City, face a l'ancien bar Sun City", "Lome"),
            pharmacy("Pharmacie El-Shammah", "70 43 25 85", "Amadahome, a cote de la Maison des jeunes, 04 BP 1004", "Lome"),
            pharmacy("Pharmacie Notre-Dame-de-Lourdes", "70 44 01 01", "Carrefour Maison Blanche, en allant a Deux Lions, en face de STAM", "Lome"),
            pharmacy("Pharmacie La Grace", "22 25 91 65", "Pres de l'Auberge Sahara, avant la station Sun Agip, Agoe", "Lome"),
            pharmacy("Pharmacie Tchep'Son", "70 42 94 41", "Face au Terminal du Sahel, Togblekope", "Lome"),
            pharmacy("Pharmacie Liddy", "70 90 19 60", "Agoe-Dikame, Bernard Cope, apres la station CAP, en face du camp de tir", "Lome"),
            pharmacy("Pharmacie Regina Pacis", "70 45 98 58", "Adetikope, route nationale n1, pres du bar Sous l'Antenne", "Adetikope"),
            pharmacy("Pharmacie Espace Vie", "99 85 89 07", "Agoe-Logope, non loin de l'espace de loisirs BKS 2", "Lome"),
            pharmacy("Pharmacie Aureole", "70 70 98 98", "Agoe-Trokpossime, au carrefour Camp GP, a 50 m de l'EPP du Camp GP", "Lome"),
            pharmacy("Pharmacie Emmaus", "70 40 25 40", "Route de Mission-Tove, a cote du bar Solidarite", "Lome"),
            pharmacy("Pharmacie Zossime", "22 55 43 52", "Agoe-Zossime, pres du marche", "Lome"),
            pharmacy("Pharmacie Saint-Sylvestre", "93 51 51 98", "Zanguera, quartier Sangame, non loin du rond-point San Ame", "Zanguera"),
            pharmacy("Pharmacie Saint-Philippe", "90 67 33 24", "Zanguera, route Lome-Kpalime, pres de la station-service Oando", "Zanguera"),
            pharmacy("Pharmacie Eva", "92 16 32 32", "Zanguera, Klikame, non loin de T-Oil", "Zanguera"),
            pharmacy("Pharmacie Nouvelle Tulipe", "99 47 00 70", "Route de Mission-Tove, pres de la station CAP Agoe-Legbassito", "Lome"),
            pharmacy("Pharmacie Gratitude", "92 18 94 85", "Agoe-Legbassito, Zovadjin, non loin du carrefour Avinato", "Lome"),
            pharmacy("Pharmacie Baguida", "70 42 47 7", "Face au CMS de Baguida", "Baguida", true),
            pharmacy("Pharmacie La Flamme d'Amour", "70 45 70 14", "Quartier Bobole-Kope/Kpogan, non loin du cimetiere Zogbedjimonou de Kpogan", "Kpogan"),
            pharmacy("Pharmacie La Patience", "70 05 23 39", "Djagble, a 300 metres du CMS d'Afoklefe", "Djagble"),
            pharmacy("Pharmacie Sika", "92 62 06 51", "Djagble, Hiheatro, a 200 m du complexe scolaire La Perseverance, route Akakope-Gbamakope", "Djagble")
    );

    @Override
    @Transactional
    public void run(String... args) {
        ImportReport report = new ImportReport();
        migrateStructurePharmacies(report);
        importPartnerPharmacies(report);

        System.out.printf("%n[Pharmacies partenaires] importees=%d, creees=%d, mises_a_jour=%d, migrees_structure=%d, anciennes_desactivees=%d, doublons_evites=%d, a_verifier=%d%n",
                PHARMACIES.size(), report.created, report.updated, report.migratedFromStructures,
                report.disabledOldStructures, report.duplicatesAvoided, report.toVerify.size());
        report.toVerify.forEach(value -> System.out.println("  A verifier : " + value));
    }

    private void migrateStructurePharmacies(ImportReport report) {
        List<StructureSante> oldPharmacies = structureRepository.findByType(TypeStructure.PHARMACIE);
        for (StructureSante structure : oldPharmacies) {
            Optional<Pharmacie> existing = findExisting(structure.getNom(), structure.getTelephone(), List.of());
            Pharmacie pharmacie = existing.orElseGet(Pharmacie::new);
            pharmacie.setCode(existing.map(Pharmacie::getCode).orElse(generateCode(structure.getNom())));
            pharmacie.setNom(structure.getNom());
            pharmacie.setTelephone(structure.getTelephone());
            pharmacie.setAdresse(structure.getAdresse());
            pharmacie.setQuartier(extractQuartier(structure.getAdresse(), structure.getVille()));
            pharmacie.setVille(structure.getVille());
            pharmacie.setRegion(structure.getRegion());
            pharmacie.setEmail(structure.getEmail());
            pharmacie.setLatitude(structure.getLatitude());
            pharmacie.setLongitude(structure.getLongitude());
            pharmacie.setAgreee(structure.getAgrementAMU());
            pharmacie.setActive(structure.getActif() == null || Boolean.TRUE.equals(structure.getActif()));
            pharmacie.setNotes(structure.getSpecialites());
            pharmacieRepository.save(pharmacie);

            if (existing.isPresent()) {
                report.duplicatesAvoided++;
            } else {
                report.migratedFromStructures++;
            }
            if (!Boolean.FALSE.equals(structure.getActif())) {
                structure.setActif(false);
                structureRepository.save(structure);
                report.disabledOldStructures++;
            }
        }
    }

    private void importPartnerPharmacies(ImportReport report) {
        for (PharmacyData data : PHARMACIES) {
            Optional<Pharmacie> existing = findExisting(data.nom(), data.telephone(), data.aliases());
            Pharmacie pharmacie = existing.orElseGet(Pharmacie::new);
            pharmacie.setCode(existing.map(Pharmacie::getCode).orElse(generateCode(data.nom())));
            pharmacie.setNom(data.nom());
            pharmacie.setTelephone(data.telephone());
            pharmacie.setAdresse(data.adresse());
            pharmacie.setQuartier(extractQuartier(data.adresse(), data.ville()));
            pharmacie.setVille(data.ville());
            pharmacie.setLatitude(null);
            pharmacie.setLongitude(null);
            pharmacie.setAgreee(true);
            pharmacie.setActive(true);
            pharmacie.setNotes(data.toVerify() ? TO_VERIFY : null);
            pharmacieRepository.save(pharmacie);

            if (existing.isPresent()) {
                report.updated++;
                report.duplicatesAvoided++;
            } else {
                report.created++;
            }
            if (data.toVerify()) {
                report.toVerify.add(data.nom() + " (" + data.telephone() + ")");
            }
        }
    }

    private Optional<Pharmacie> findExisting(String nom, String telephone, List<String> aliases) {
        String code = generateCode(nom);
        Optional<Pharmacie> byCode = pharmacieRepository.findByCode(code);
        if (byCode.isPresent()) {
            return byCode;
        }

        Set<String> names = new LinkedHashSet<>();
        names.add(nom);
        names.addAll(aliases);
        Set<String> normalizedNames = names.stream()
                .map(this::normalizeName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String normalizedPhone = normalizePhone(telephone);

        return pharmacieRepository.findAll().stream()
                .sorted(Comparator.comparing(Pharmacie::getId, Comparator.nullsLast(Long::compareTo)))
                .filter(pharmacie -> normalizedNames.contains(normalizeName(pharmacie.getNom()))
                        || !normalizedPhone.isBlank() && normalizedPhone.equals(normalizePhone(pharmacie.getTelephone())))
                .findFirst();
    }

    private String extractQuartier(String adresse, String ville) {
        if (adresse == null || adresse.isBlank()) {
            return null;
        }
        String candidate = adresse.split(",", 2)[0].trim();
        if (candidate.isBlank() || normalizeName(candidate).equals(normalizeName(ville))) {
            return null;
        }
        return candidate;
    }

    private String generateCode(String nom) {
        String normalized = Normalizer.normalize(nom == null ? "" : nom, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return "PHARM_" + normalized;
    }

    private String normalizeName(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace("st ", "saint ")
                .replace('-', ' ')
                .replace("'", " ")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizePhone(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static PharmacyData pharmacy(String nom, String telephone, String adresse, String ville, String... aliases) {
        return new PharmacyData(nom, telephone, adresse, ville, false, List.of(aliases));
    }

    private static PharmacyData pharmacy(String nom, String telephone, String adresse, String ville, boolean toVerify) {
        return new PharmacyData(nom, telephone, adresse, ville, toVerify, List.of());
    }

    private record PharmacyData(String nom, String telephone, String adresse, String ville,
                                boolean toVerify, List<String> aliases) {
    }

    private static class ImportReport {
        private int created;
        private int updated;
        private int migratedFromStructures;
        private int disabledOldStructures;
        private int duplicatesAvoided;
        private final List<String> toVerify = new java.util.ArrayList<>();
    }
}
