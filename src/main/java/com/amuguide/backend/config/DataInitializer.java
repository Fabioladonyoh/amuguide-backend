package com.amuguide.backend.config;

import com.amuguide.backend.entity.Administrateur;
import com.amuguide.backend.entity.AssureAMU;
import com.amuguide.backend.entity.Prestation;
import com.amuguide.backend.entity.StructureSante;
import com.amuguide.backend.enums.CategorieActe;
import com.amuguide.backend.enums.StatutAssure;
import com.amuguide.backend.enums.TypeStructure;
import com.amuguide.backend.repository.AdministrateurRepository;
import com.amuguide.backend.repository.AssureAMURepository;
import com.amuguide.backend.repository.PrestationRepository;
import com.amuguide.backend.repository.StructureSanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "amuguide.seed.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AdministrateurRepository administrateurRepository;
    private final AssureAMURepository assureAMURepository;
    private final PrestationRepository prestationRepository;
    private final StructureSanteRepository structureSanteRepository;
    private final PasswordEncoder passwordEncoder;

    private static final DateTimeFormatter DDMMyyyyFmt = DateTimeFormatter.ofPattern("ddMMyyyy");

    // ─────────────────────────────────────────────────────────────────────────
    // Données de référence
    // ─────────────────────────────────────────────────────────────────────────

    private record AdminData(String login, String motDePasse, String nom, String prenom, String email) {}

    private record AssureData(String numeroAMU, String nom, String prenom,
                              LocalDate dateNaissance, String telephone, String email, String adresse) {}

    private record PrestationData(String codeActe, String nomActe, CategorieActe categorie,
                                  String description, Double tauxCouverture, Boolean prisEnCharge,
                                  String conditions, String documents) {}

    private record StructureData(String nom, TypeStructure type, String adresse, String ville,
                                 String telephone, Double latitude, Double longitude,
                                 Boolean agrementAMU, String specialites, String horaires,
                                 List<String> codesActe) {}

    // ── 4 Administrateurs ────────────────────────────────────────────────────

    private static final List<AdminData> ADMINS = List.of(
        new AdminData("admin",        "admin123",           "Admin",    "AMU",   "admin@amuguide.tg"),
        new AdminData("directeur",    "Directeur@2024",     "Agbeko",   "Kofi",  "directeur@amuguide.tg"),
        new AdminData("superviseur",  "Superviseur@2024",   "Dossou",   "Ama",   "superviseur@amuguide.tg"),
        new AdminData("gestionnaire", "Gestionnaire@2024",  "Foli",     "Yao",   "gestionnaire@amuguide.tg")
    );

    // ── 10 Assurés ───────────────────────────────────────────────────────────

    private static final List<AssureData> ASSURES = List.of(
        new AssureData("AMU001", "Mensah",    "Kossi",  LocalDate.of(2000,  5, 14), "90000000", "kossi@example.com",   "Lomé"),
        new AssureData("AMU002", "Agbenyega", "Afi",    LocalDate.of(1995,  3, 22), "90000001", "afi@example.com",     "Lomé"),
        new AssureData("AMU003", "Blewu",     "Koffi",  LocalDate.of(1988, 11,  7), "90000002", "koffi@example.com",   "Kara"),
        new AssureData("AMU004", "Tetteh",    "Akua",   LocalDate.of(2001,  8, 30), "90000003", "akua@example.com",    "Sokodé"),
        new AssureData("AMU005", "Dossou",    "Yao",    LocalDate.of(1975, 12, 15), "90000004", "yao@example.com",     "Lomé"),
        new AssureData("AMU006", "Kpade",     "Abla",   LocalDate.of(1990,  6,  3), "90000005", "abla@example.com",    "Atakpamé"),
        new AssureData("AMU007", "Tsevi",     "Koami",  LocalDate.of(1983,  9, 18), "90000006", "koami@example.com",   "Lomé"),
        new AssureData("AMU008", "Agbanyo",   "Afua",   LocalDate.of(1998,  2, 25), "90000007", "afua@example.com",    "Dapaong"),
        new AssureData("AMU009", "Edem",      "Kafui",  LocalDate.of(2003,  7, 11), "90000008", "kafui@example.com",   "Lomé"),
        new AssureData("AMU010", "Akpalo",    "Mawuli", LocalDate.of(1967,  4, 28), "90000009", "mawuli@example.com",  "Kpalimé")
    );

    // ── 9 Prestations (une par CategorieActe) ────────────────────────────────

    private static final List<PrestationData> PRESTATIONS = List.of(
        new PrestationData(
            "CONS001", "Consultation générale", CategorieActe.CONSULTATION,
            "Consultation chez un médecin généraliste agréé AMU",
            80.0, true,
            "Présentation de la carte AMU valide",
            "Carte AMU"
        ),
        new PrestationData(
            "HOSP001", "Hospitalisation standard", CategorieActe.HOSPITALISATION,
            "Hospitalisation en salle commune dans un établissement agréé",
            70.0, true,
            "Prescription médicale obligatoire, accord préalable pour plus de 72h",
            "Carte AMU, ordonnance médicale, fiche de prise en charge"
        ),
        new PrestationData(
            "MED001", "Médicaments essentiels", CategorieActe.MEDICAMENT,
            "Médicaments figurant sur la liste nationale des médicaments essentiels AMU",
            60.0, true,
            "Prescription valide d'un médecin agréé, médicaments sur liste AMU",
            "Ordonnance médicale"
        ),
        new PrestationData(
            "RAD001", "Radiologie standard", CategorieActe.RADIOLOGIE,
            "Examens de radiologie conventionnelle (radio, échographie)",
            50.0, true,
            "Demande d'examen signée par un médecin agréé",
            "Ordonnance médicale, carte AMU"
        ),
        new PrestationData(
            "BIO001", "Analyses biologiques", CategorieActe.BIOLOGIE,
            "Examens de laboratoire (NFS, glycémie, bilan rénal, etc.)",
            70.0, true,
            "Prescription médicale obligatoire",
            "Ordonnance médicale, carte AMU"
        ),
        new PrestationData(
            "CHIR001", "Chirurgie générale", CategorieActe.CHIRURGIE,
            "Interventions chirurgicales programmées ou d'urgence",
            80.0, true,
            "Avis spécialisé et accord préalable AMU requis pour les actes programmés",
            "Carte AMU, fiche de prise en charge, dossier médical complet"
        ),
        new PrestationData(
            "MAT001", "Suivi grossesse et accouchement", CategorieActe.MATERNITE,
            "Consultations prénatales, accouchement et suivi postnatal",
            90.0, true,
            "Inscription obligatoire au registre de maternité, carnet de suivi prénatal",
            "Carte AMU, carnet de santé maternelle, livret de famille"
        ),
        new PrestationData(
            "DENT001", "Soins dentaires esthétiques", CategorieActe.DENTAIRE,
            "Soins dentaires à visée esthétique non couverts par l'AMU",
            0.0, false,
            "Non pris en charge — soins dentaires conservateurs uniquement (obturations urgentes) sur accord",
            "Aucun document (hors couverture AMU standard)"
        ),
        new PrestationData(
            "OPHT001", "Consultation ophtalmologique", CategorieActe.OPHTALMOLOGIE,
            "Consultation chez un ophtalmologue, prescription de lunettes exclue",
            60.0, true,
            "Ordonnance de renouvellement annuel, première consultation sur prescription du généraliste",
            "Carte AMU, ordonnance médicale"
        )
    );

    // ── 10 Structures de santé ────────────────────────────────────────────────

    private static final List<StructureData> STRUCTURES = List.of(
        new StructureData(
            "CHU Sylvanus Olympio", TypeStructure.HOPITAL, "Avenue du 24 Janvier", "Lomé",
            "22210001", 6.1319, 1.2228, true,
            "Médecine générale, urgences, chirurgie, maternité, radiologie, biologie",
            "24h/24 — 7j/7",
            List.of("CONS001", "HOSP001", "RAD001", "BIO001", "CHIR001", "MAT001")
        ),
        new StructureData(
            "Clinique Biasa", TypeStructure.CLINIQUE, "Nyékonakpoè", "Lomé",
            "22210002", 6.1370, 1.2100, true,
            "Consultation, hospitalisation, radiologie, biologie",
            "Lun–Sam : 07h–21h",
            List.of("CONS001", "HOSP001", "RAD001", "BIO001")
        ),
        new StructureData(
            "Pharmacie du Campus", TypeStructure.PHARMACIE, "Quartier Agoè", "Lomé",
            "22210003", 6.2000, 1.1800, true,
            "Médicaments essentiels, conseils pharmaceutiques",
            "Lun–Sam : 07h–22h  |  Dim : 08h–18h",
            List.of("MED001")
        ),
        new StructureData(
            "Centre Médical de Kara", TypeStructure.CENTRE_DE_SANTE, "Quartier central", "Kara",
            "26210004", 9.5511, 1.1860, true,
            "Consultation, soins primaires, maternité",
            "Lun–Ven : 08h–18h",
            List.of("CONS001", "MAT001")
        ),
        new StructureData(
            "CHU Kara", TypeStructure.HOPITAL, "Avenue de l'Indépendance", "Kara",
            "26210005", 9.5500, 1.1900, true,
            "Médecine générale, chirurgie, maternité, radiologie, biologie",
            "24h/24 — 7j/7",
            List.of("CONS001", "HOSP001", "RAD001", "BIO001", "CHIR001", "MAT001")
        ),
        new StructureData(
            "Clinique Évangélique de Bè", TypeStructure.CLINIQUE, "Quartier Bè", "Lomé",
            "22210006", 6.1450, 1.2300, true,
            "Consultation générale, ophtalmologie, analyses biologiques",
            "Lun–Sam : 07h–19h",
            List.of("CONS001", "OPHT001", "BIO001")
        ),
        new StructureData(
            "Pharmacie Centrale de Sokodé", TypeStructure.PHARMACIE, "Centre-ville", "Sokodé",
            "26610007", 8.9833, 1.1333, true,
            "Médicaments essentiels et génériques",
            "Lun–Sam : 08h–20h",
            List.of("MED001")
        ),
        new StructureData(
            "Hôpital Régional de Sokodé", TypeStructure.HOPITAL, "Avenue principale", "Sokodé",
            "26610008", 8.9800, 1.1400, true,
            "Médecine générale, chirurgie, maternité, urgences",
            "24h/24 — 7j/7",
            List.of("CONS001", "HOSP001", "CHIR001", "MAT001", "BIO001")
        ),
        new StructureData(
            "Pharmacie de Kpalimé", TypeStructure.PHARMACIE, "Marché central", "Kpalimé",
            "24410009", 6.8990, 0.6270, true,
            "Médicaments essentiels",
            "Lun–Sam : 07h–21h",
            List.of("MED001")
        ),
        new StructureData(
            "Centre de Santé d'Atakpamé", TypeStructure.CENTRE_DE_SANTE, "Quartier nord", "Atakpamé",
            "24210010", 7.5333, 1.1333, true,
            "Consultation, soins primaires, analyses biologiques",
            "Lun–Ven : 07h–18h  |  Sam : 07h–13h",
            List.of("CONS001", "BIO001")
        )
    );

    // ─────────────────────────────────────────────────────────────────────────
    // Exécution
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void run(String... args) {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║      DataInitializer — AMU-Guide Backend     ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        insererAdmins();
        insererAssures();
        migrerMotsDePasseManquants();
        insererPrestations();
        insererStructures();
        System.out.println("══════════════════════════════════════════════");
        System.out.println(" Initialisation terminée.");
        System.out.println("══════════════════════════════════════════════");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Admins
    // ─────────────────────────────────────────────────────────────────────────

    private void insererAdmins() {
        System.out.println("\n[Admins]");
        for (AdminData d : ADMINS) {
            administrateurRepository.findByLogin(d.login()).ifPresentOrElse(
                existing -> {
                    if (!isBCrypt(existing.getMotDePasse())) {
                        existing.setMotDePasse(passwordEncoder.encode(existing.getMotDePasse()));
                        administrateurRepository.save(existing);
                        System.out.println("  Compte administrateur demo : mot de passe migre en BCrypt");
                    } else {
                        System.out.println("  Compte administrateur demo : deja en base");
                    }
                },
                () -> {
                    administrateurRepository.save(Administrateur.builder()
                            .login(d.login()).motDePasse(passwordEncoder.encode(d.motDePasse()))
                            .nom(d.nom()).prenom(d.prenom()).email(d.email()).actif(true).build());
                    System.out.println("  Compte administrateur demo cree");
                }
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Assurés
    // ─────────────────────────────────────────────────────────────────────────

    private void insererAssures() {
        System.out.println("\n[Assurés]");
        for (AssureData d : ASSURES) {
            if (assureAMURepository.findByNumeroAMU(d.numeroAMU()).isEmpty()) {
                String mdp = d.dateNaissance().format(DDMMyyyyFmt);
                assureAMURepository.save(AssureAMU.builder()
                        .numeroAMU(d.numeroAMU()).nom(d.nom()).prenom(d.prenom())
                        .dateNaissance(d.dateNaissance()).telephone(d.telephone())
                        .email(d.email()).adresse(d.adresse()).statut(StatutAssure.ACTIF)
                        .motDePasse(passwordEncoder.encode(mdp)).build());
                System.out.println("  Compte assure demo cree");
            } else {
                System.out.println("  Compte assure demo deja en base");
            }
        }
    }

    private void migrerMotsDePasseManquants() {
        assureAMURepository.findAll().forEach(assure -> {
            if (!isBCrypt(assure.getMotDePasse())) {
                String mdp = assure.getDateNaissance().format(DDMMyyyyFmt);
                assure.setMotDePasse(passwordEncoder.encode(mdp));
                assureAMURepository.save(assure);
                System.out.println("  Compte assure demo : mot de passe migre en BCrypt");
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prestations
    // ─────────────────────────────────────────────────────────────────────────

    private void insererPrestations() {
        System.out.println("\n[Prestations]");
        for (PrestationData d : PRESTATIONS) {
            if (prestationRepository.findByCodeActe(d.codeActe()).isEmpty()) {
                prestationRepository.save(Prestation.builder()
                        .codeActe(d.codeActe()).nomActe(d.nomActe()).categorie(d.categorie())
                        .description(d.description()).tauxCouverture(d.tauxCouverture())
                        .prisEnCharge(d.prisEnCharge()).conditionsPriseEnCharge(d.conditions())
                        .documentsRequis(d.documents()).build());
                System.out.printf("  %-8s %-38s %s%n",
                        d.codeActe(), d.nomActe(),
                        d.prisEnCharge() ? "(couvert " + d.tauxCouverture().intValue() + "%)" : "(non couvert)");
            } else {
                System.out.printf("  %-8s → déjà en base%n", d.codeActe());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Structures de santé
    // ─────────────────────────────────────────────────────────────────────────

    private void insererStructures() {
        System.out.println("\n[Structures de santé]");
        for (StructureData d : STRUCTURES) {
            if (structureSanteRepository.findByNomAndVille(d.nom(), d.ville()).isEmpty()) {

                // Charger les prestations associées depuis la base
                List<Prestation> prestations = new ArrayList<>();
                for (String code : d.codesActe()) {
                    prestationRepository.findByCodeActe(code).ifPresent(prestations::add);
                }

                // Construire la structure avec ses prestations (entité neuve, pas de lazy loading)
                StructureSante structure = StructureSante.builder()
                        .nom(d.nom()).type(d.type()).adresse(d.adresse()).ville(d.ville())
                        .telephone(d.telephone()).latitude(d.latitude()).longitude(d.longitude())
                        .agrementAMU(d.agrementAMU()).specialites(d.specialites()).horaires(d.horaires())
                        .build();
                structure.getPrestations().addAll(prestations);

                structureSanteRepository.save(structure);
                System.out.printf("  %-38s [%s] %s — %d prestation(s)%n",
                        d.nom(), d.type(), d.ville(), prestations.size());
            } else {
                System.out.printf("  %-38s → déjà en base%n", d.nom());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilitaires
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isBCrypt(String hash) {
        return hash != null && hash.startsWith("$2");
    }
}
