package com.amuguide.backend.config;

import com.amuguide.backend.entity.FaqChatbot;
import com.amuguide.backend.enums.FaqChatbotCategorie;
import com.amuguide.backend.repository.FaqChatbotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class ChatbotFaqDataInitializer implements CommandLineRunner {

    private final FaqChatbotRepository repository;

    private record FaqSeed(
            String code,
            String question,
            String reponse,
            FaqChatbotCategorie categorie,
            String motsCles,
            int priorite
    ) {
    }

    private static final List<FaqSeed> FAQS = List.of(
            faq("PRESTATIONS_LISTE", "Quelles prestations sont couvertes ?",
                    "Les prestations couvertes sont recherchees dans la table des prestations AMU afin de retourner les actes, taux, conditions et documents reellement enregistres.",
                    FaqChatbotCategorie.PRESTATION,
                    "prestations couvertes, prestations prises en charge, actes couverts, couverture amu, liste prestations", 9),
            faq("PRESTATION_CONSULTATION", "Est-ce que la consultation est prise en charge ?",
                    "La consultation est verifiee dans la table des prestations avant de repondre avec son taux, ses conditions et ses documents requis.",
                    FaqChatbotCategorie.PRESTATION,
                    "consultation, consulter, medecin, docteur, prise en charge consultation, consultation couverte, remboursement consultation", 10),
            faq("PRESTATION_HOSPITALISATION", "Quel est le taux de couverture pour l'hospitalisation ?",
                    "Le taux de l'hospitalisation est recherche dans la table des prestations, sans valeur inventee.",
                    FaqChatbotCategorie.PRESTATION,
                    "hospitalisation, hospitalise, hopital, taux hospitalisation, couverture hospitalisation, prise en charge hospitalisation", 10),
            faq("PRESTATION_MEDICAMENT", "Les medicaments sont-ils rembourses ?",
                    "La couverture des medicaments est verifiee dans la table des prestations et le referentiel medicament peut etre consulte pour un nom precis.",
                    FaqChatbotCategorie.PRESTATION,
                    "medicament, medicaments, remboursement medicament, prise en charge medicament, ordonnance, pharmacie", 8),
            faq("PRESTATION_RADIOLOGIE", "La radiologie est-elle couverte ?",
                    "La radiologie est recherchee dans la table des prestations avant de repondre avec son taux et ses conditions.",
                    FaqChatbotCategorie.PRESTATION,
                    "radiologie, radio, imagerie, radiographie, echographie, scanner, couverture radiologie", 9),
            faq("PRESTATION_CONSULTATION_DOCUMENTS", "Quels documents faut-il pour une consultation ?",
                    "Les documents requis pour la consultation sont lus dans la table des prestations.",
                    FaqChatbotCategorie.PRESTATION,
                    "documents consultation, piece consultation, carte amu consultation, ordonnance consultation, justificatifs consultation", 9),

            faq("STRUCTURE_PHARMACIE_LOME", "Existe-t-il une pharmacie partenaire a Lome ?",
                    "Les pharmacies partenaires a Lome sont recherchees dans la table des structures agreees AMU.",
                    FaqChatbotCategorie.STRUCTURE_SANTE,
                    "pharmacie, partenaire, agreee, lome, officine, proche, pharmacie lome", 10),
            faq("STRUCTURE_HOPITAL_KARA", "Trouve-moi un hopital agree a Kara.",
                    "Les hopitaux agrees a Kara sont recherches dans la table des structures de sante.",
                    FaqChatbotCategorie.STRUCTURE_SANTE,
                    "hopital, hopitaux, chu, agree, kara, structure kara, centre hospitalier", 10),
            faq("STRUCTURE_CLINIQUE_PROCHE", "Y a-t-il une clinique AMU pres de moi ?",
                    "Les cliniques agreees sont recherchees dans la table des structures. La geolocalisation est utilisee si elle est fournie.",
                    FaqChatbotCategorie.STRUCTURE_SANTE,
                    "clinique, amu, pres de moi, proche, autour de moi, geolocalisation, structure proche", 8),
            faq("STRUCTURES_AGREEES", "Quelles structures de sante sont agreees ?",
                    "Les structures agreees sont listees depuis la table des structures de sante.",
                    FaqChatbotCategorie.STRUCTURE_SANTE,
                    "structures agreees, structures de sante, centres agrees, hopitaux agrees, cliniques agreees", 8),
            faq("STRUCTURE_PHARMACIE_AGREEE", "Ou trouver une pharmacie agreee ?",
                    "Les pharmacies agreees sont recherchees dans la table des structures de sante.",
                    FaqChatbotCategorie.STRUCTURE_SANTE,
                    "pharmacie agreee, trouver pharmacie, officine agreee, pharmacie partenaire", 8),

            faq("MEDICAMENT_PARACETAMOL", "Le paracetamol est-il dans le referentiel ?",
                    "Le paracetamol est recherche dans le referentiel medicament enregistre dans le backend.",
                    FaqChatbotCategorie.MEDICAMENT,
                    "paracetamol, doliprane, panadol, dafalgan, efferalgan, referentiel paracetamol", 10),
            faq("MEDICAMENT_AMOXICILLINE", "Est-ce que l'amoxicilline est prise en charge ?",
                    "L'amoxicilline est recherchee dans le referentiel medicament enregistre dans le backend.",
                    FaqChatbotCategorie.MEDICAMENT,
                    "amoxicilline, antibiotique, referentiel amoxicilline, prise en charge amoxicilline", 10),
            faq("MEDICAMENTS_REFERENTIEL", "Quels medicaments sont disponibles dans le referentiel AMU ?",
                    "La liste des medicaments est extraite du referentiel enregistre dans le backend.",
                    FaqChatbotCategorie.MEDICAMENT,
                    "liste medicaments, referentiel amu, medicaments disponibles, quels medicaments, liste referentiel", 9),
            faq("MEDICAMENT_IBUPROFENE", "L'ibuprofene est-il reconnu ?",
                    "L'ibuprofene est recherche dans le referentiel medicament enregistre dans le backend.",
                    FaqChatbotCategorie.MEDICAMENT,
                    "ibuprofene, anti inflammatoire, referentiel ibuprofene, reconnu, medicament reconnu", 10),

            faq("AMU_DEFINITION", "C'est quoi l'AMU ?",
                    "L'AMU, ou Assurance Maladie Universelle, est un dispositif qui facilite l'acces aux soins de sante en prenant en charge une partie des frais medicaux des assures, selon les prestations, taux et conditions enregistres.",
                    FaqChatbotCategorie.INFORMATION_AMU,
                    "amu, assurance maladie universelle, definition amu, c'est quoi amu, role amu, regime amu", 10),
            faq("AMU_OBJECTIF", "A quoi sert l'assurance maladie universelle ?",
                    "L'assurance maladie universelle sert a faciliter l'acces aux soins en reduisant la part des frais supportee par l'assure, lorsque la prestation est eligible et realisee dans les conditions prevues.",
                    FaqChatbotCategorie.INFORMATION_AMU,
                    "a quoi sert amu, objectif amu, role assurance maladie, utilite amu, assurance maladie universelle", 9),
            faq("AMU_FONCTIONNEMENT", "Comment fonctionne la prise en charge AMU ?",
                    "La prise en charge AMU depend de la prestation demandee, de son taux de couverture, des documents requis et des conditions enregistrees dans AMU-Guide.",
                    FaqChatbotCategorie.INFORMATION_AMU,
                    "fonctionnement amu, prise en charge amu, comment fonctionne, taux, conditions, couverture", 9),

            faq("PROCEDURE_DOCUMENTS", "Quels documents dois-je fournir ?",
                    "Les documents dependent de la prestation. Pour une reponse precise, le backend recherche la prestation concernee et retourne les documents requis enregistres.",
                    FaqChatbotCategorie.PROCEDURE,
                    "documents, pieces, fournir, justificatifs, dossier, carte amu, ordonnance", 9),
            faq("PROCEDURE_CARTE_UTILISATION", "Comment utiliser ma carte AMU ?",
                    "Presentez votre carte AMU valide dans une structure agreee, indiquez la prestation souhaitee et fournissez les documents demandes selon la prestation.",
                    FaqChatbotCategorie.PROCEDURE,
                    "carte amu, utiliser carte, presenter carte, carte valide, prise en charge avec carte", 10),
            faq("PROCEDURE_PRESTATION", "Que faut-il pour beneficier d'une prestation ?",
                    "Pour beneficier d'une prestation, il faut une situation AMU active, une carte AMU valide, une structure agreee et les documents requis pour la prestation.",
                    FaqChatbotCategorie.PROCEDURE,
                    "beneficier prestation, que faut il, condition prestation, obtenir prestation, profiter prestation", 9),
            faq("PROCEDURE_CARTE_RENOUVELLEMENT", "Comment renouveler ma carte AMU ?",
                    "Pour renouveler la carte AMU, presentez l'ancienne carte et une piece d'identite valide, puis suivez la procedure indiquee par le service competent.",
                    FaqChatbotCategorie.PROCEDURE,
                    "renouveler carte, renouvellement carte, carte expiree, refaire carte, perte carte, nouvelle carte amu", 10)
    );

    @Override
    public void run(String... args) {
        FAQS.forEach(seed -> repository.findByCode(seed.code()).orElseGet(() -> repository.save(
                FaqChatbot.builder()
                        .code(seed.code())
                        .question(seed.question())
                        .reponse(seed.reponse())
                        .categorie(seed.categorie())
                        .motsCles(seed.motsCles())
                        .priorite(seed.priorite())
                        .actif(true)
                        .build()
        )));
    }

    private static FaqSeed faq(String code, String question, String reponse,
                               FaqChatbotCategorie categorie, String motsCles, int priorite) {
        return new FaqSeed(code, question, reponse, categorie, motsCles, priorite);
    }
}
