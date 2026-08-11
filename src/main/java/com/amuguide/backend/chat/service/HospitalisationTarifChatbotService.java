package com.amuguide.backend.chat.service;

import com.amuguide.backend.chat.nlp.ChatIntent;
import com.amuguide.backend.chat.nlp.IntentDetectionResult;
import com.amuguide.backend.dto.ChatbotResponseDTO;
import com.amuguide.backend.dto.ChatbotSourceDTO;
import com.amuguide.backend.entity.HospitalisationTarif;
import com.amuguide.backend.service.HospitalisationTarifService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class HospitalisationTarifChatbotService {

    private static final String CATEGORIE_HOSPITALISATION = "CABINE D'HOSPITALISATION";
    private static final List<String> TYPE_PRESTATAIRES = List.of(
            "NIVEAU DE SOINS 4",
            "HOPITAL DE DISTRICT",
            "CHU",
            "CHR"
    );

    private final HospitalisationTarifService hospitalisationTarifService;

    public boolean isHospitalisationTarifQuestion(IntentDetectionResult detection) {
        if (detection == null || detection.getNormalizedMessage() == null) {
            return false;
        }
        String message = detection.getNormalizedMessage();
        return mentionsHospitalisation(message)
                && (extractTypePrestataire(message) != null
                || message.contains("chambre")
                || message.contains("cabine")
                || message.contains("salle commune")
                || asksWeeklyTarif(message));
    }

    public ChatbotResponseDTO answer(IntentDetectionResult detection) {
        String message = detection.getNormalizedMessage();
        String typePrestataire = extractTypePrestataire(message);
        String population = extractPopulation(message);
        String chambre = extractChambre(message);
        List<HospitalisationTarif> matches = hospitalisationTarifService.searchForChatbot(
                CATEGORIE_HOSPITALISATION,
                chambre,
                population,
                typePrestataire
        );

        if (matches.isEmpty()) {
            return base("Je n'ai pas trouve de tarif d'hospitalisation correspondant dans le referentiel AMU disponible.", false)
                    .sources(List.of())
                    .build();
        }
        if (matches.size() > 1) {
            return ambiguous(matches);
        }
        return preciseAnswer(matches.get(0), message);
    }

    private ChatbotResponseDTO preciseAnswer(HospitalisationTarif tarif, String message) {
        String value;
        if (asksSecondWeek(message)) {
            value = officialValue("Deuxieme semaine", tarif.getDeuxiemeSemaine());
        } else if (asksThirdWeek(message)) {
            value = officialValue("A partir de la troisieme semaine", tarif.getAPartirTroisiemeSemaine());
        } else if (asksFirstWeek(message)) {
            value = officialValue("Premiere semaine", tarif.getPremiereSemaine());
        } else if (asksRate(message)) {
            value = tarif.getTauxRemboursement() == null
                    ? "Taux remboursement : information non disponible."
                    : String.format(Locale.FRANCE, "Taux remboursement : %.0f%%.", tarif.getTauxRemboursement());
        } else {
            value = String.format(Locale.FRANCE,
                    "Taux remboursement : %s. Premiere semaine : %s. Deuxieme semaine : %s. A partir de la troisieme semaine : %s.",
                    tarif.getTauxRemboursement() == null ? "information non disponible" : String.format(Locale.FRANCE, "%.0f%%", tarif.getTauxRemboursement()),
                    displayTarif(tarif.getPremiereSemaine()),
                    displayTarif(tarif.getDeuxiemeSemaine()),
                    displayTarif(tarif.getAPartirTroisiemeSemaine()));
        }

        return ChatbotResponseDTO.builder()
                .intent(ChatIntent.COVERAGE.name())
                .found(true)
                .message("Referentiel hospitalisation AMU : " + tarif.getTypePrestataire()
                        + ", " + tarif.getChambre()
                        + ", " + tarif.getPopulation()
                        + ", date debut " + tarif.getDateDebut()
                        + ". " + value)
                .sources(List.of(source(tarif)))
                .suggestionList(List.of("Premiere semaine", "Deuxieme semaine", "A partir de la troisieme semaine"))
                .suggestions("Suggestions : Premiere semaine, Deuxieme semaine, A partir de la troisieme semaine")
                .build();
    }

    private ChatbotResponseDTO ambiguous(List<HospitalisationTarif> matches) {
        String items = matches.stream()
                .limit(5)
                .map(tarif -> "\n- " + tarif.getTypePrestataire()
                        + " | " + tarif.getChambre()
                        + " | " + tarif.getPopulation()
                        + " | date debut " + tarif.getDateDebut())
                .reduce("", String::concat);
        String more = matches.size() > 5 ? "\n- ..." : "";
        return base("J'ai trouve plusieurs tarifs d'hospitalisation possibles :" + items + more
                + "\n\nVeuillez preciser le type prestataire, la chambre et la population.", false)
                .sources(matches.stream().limit(5).map(this::source).toList())
                .build();
    }

    private String officialValue(String label, String value) {
        if (value == null || value.isBlank()) {
            return label + " : information non disponible.";
        }
        if ("exclus".equals(normalize(value))) {
            return "Cette prise en charge est indiquee comme exclue dans le referentiel officiel.";
        }
        return label + " : " + value + ".";
    }

    private String displayTarif(String value) {
        if (value == null || value.isBlank()) {
            return "information non disponible";
        }
        if ("exclus".equals(normalize(value))) {
            return "Exclus";
        }
        return value;
    }

    private String extractTypePrestataire(String message) {
        String normalized = normalize(message);
        for (String type : TYPE_PRESTATAIRES) {
            if (normalized.contains(normalize(type))) {
                return type;
            }
        }
        return null;
    }

    private String extractPopulation(String message) {
        String normalized = normalize(message);
        if (normalized.contains("school amu") || normalized.contains("scolaire")) {
            return "SCHOOL AMU";
        }
        if (normalized.contains("toute autre population") || normalized.contains("autre population")) {
            return "Toute autre population";
        }
        return null;
    }

    private String extractChambre(String message) {
        String normalized = normalize(message);
        if (normalized.contains("reanimation") || normalized.contains("rea")) {
            return "REANIMATION";
        }
        if (normalized.contains("cabine climatisee")) {
            return normalized.contains("2 lit") ? "CABINE CLIMATISEE AVEC 2 LITS" : "CABINE CLIMATISEE AVEC 1 LIT";
        }
        if (normalized.contains("cabine ventillee") || normalized.contains("cabine ventilee")) {
            if (normalized.contains("10 lit")) {
                return "Chambre ventillee avec 10 lits";
            }
            if (normalized.contains("5 lit")) {
                return "Chambre ventillee avec 5 lits";
            }
            return normalized.contains("2 lit") ? "CABINE VENTILLEE AVEC 2 LITS" : "CABINE VENTILLEE AVEC 1 LIT";
        }
        if (normalized.contains("salle commune")) {
            if (normalized.contains("10") || normalized.contains("12")) {
                return "SALLE COMMUNE 10 A 12 LITS";
            }
            if (normalized.contains("7") || normalized.contains("9")) {
                return "SALLE COMMUNE 7 A 9 LITS";
            }
            if (normalized.contains("3") || normalized.contains("6")) {
                return "SALLE COMMUNE 3 A 6 LITS";
            }
        }
        return null;
    }

    private boolean mentionsHospitalisation(String message) {
        return message.contains("hospitalisation")
                || message.contains("hospitalise")
                || message.contains("cabine")
                || message.contains("chambre")
                || message.contains("salle commune");
    }

    private boolean asksWeeklyTarif(String message) {
        return message.contains("tarif")
                || message.contains("premiere semaine")
                || message.contains("deuxieme semaine")
                || message.contains("troisieme semaine");
    }

    private boolean asksFirstWeek(String message) {
        return message.contains("premiere semaine") || message.contains("1ere semaine") || message.contains("premier semaine");
    }

    private boolean asksSecondWeek(String message) {
        return message.contains("deuxieme semaine") || message.contains("2eme semaine");
    }

    private boolean asksThirdWeek(String message) {
        return message.contains("troisieme semaine") || message.contains("3eme semaine") || message.contains("a partir de la troisieme");
    }

    private boolean asksRate(String message) {
        return message.contains("taux") || message.contains("pourcentage") || message.contains("prise en charge");
    }

    private ChatbotResponseDTO.ChatbotResponseDTOBuilder base(String message, boolean found) {
        return ChatbotResponseDTO.builder()
                .intent(ChatIntent.COVERAGE.name())
                .found(found)
                .message(message)
                .suggestionList(List.of("Type prestataire", "Chambre", "Population"))
                .suggestions("Suggestions : Type prestataire, Chambre, Population");
    }

    private ChatbotSourceDTO source(HospitalisationTarif tarif) {
        return ChatbotSourceDTO.builder()
                .type("HOSPITALISATION_TARIF_POSTGRESQL")
                .id(tarif.getId())
                .title(tarif.getTypePrestataire() + " - " + tarif.getChambre())
                .build();
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
}
