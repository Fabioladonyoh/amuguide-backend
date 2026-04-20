-- =========================
-- ADMINISTRATEUR
-- =========================
INSERT INTO administrateur (id_admin, nom, prenom, email, login, mot_de_passe, actif)
VALUES
    (1, 'Admin', 'AMU', 'admin@amuguide.tg', 'admin', 'admin123', true);

-- =========================
-- ASSURE AMU
-- =========================
INSERT INTO assure_amu (id_assure, nom, prenom, numeroamu, date_naissance, telephone, email, adresse, statut)
VALUES
    (1, 'Kossi', 'Mensah', 'AMU001', '2000-05-14', '90000000', 'kossi@example.com', 'Lomé', 'ACTIF');

-- =========================
-- PRESTATIONS
-- =========================
INSERT INTO prestation (
    id_prestation,
    code_acte,
    nom_acte,
    categorie,
    description,
    taux_couverture,
    pris_en_charge,
    conditions_prise_en_charge,
    documents_requis
)
VALUES
    (1, 'CONS001', 'Consultation générale', 'CONSULTATION', 'Consultation chez un médecin généraliste', 80, true, 'Présentation de la carte AMU', 'Carte AMU'),
    (2, 'HOSP001', 'Hospitalisation standard', 'HOSPITALISATION', 'Hospitalisation de courte durée', 70, true, 'Prescription médicale', 'Carte AMU, ordonnance'),
    (3, 'MED001', 'Médicaments essentiels', 'MEDICAMENT', 'Médicaments couverts par l’AMU', 60, true, 'Prescription valide', 'Ordonnance'),
    (4, 'RAD001', 'Radiologie', 'RADIOLOGIE', 'Examen de radiologie', 50, true, 'Demande d’examen', 'Ordonnance, carte AMU'),
    (5, 'DENT001', 'Soins dentaires esthétiques', 'DENTAIRE', 'Soins dentaires non couverts', 0, false, 'Non pris en charge', 'Aucun');

-- =========================
-- STRUCTURES DE SANTE
-- =========================
INSERT INTO structure_sante (
    id_structure,
    nom,
    type,
    adresse,
    ville,
    telephone,
    latitude,
    longitude,
    agrementamu,
    specialites,
    horaires
)
VALUES
    (1, 'CHU Sylvanus Olympio', 'HOPITAL', 'Centre-ville', 'Lomé', '22210001', 6.1319, 1.2228, true, 'Médecine générale, urgences', '24h/24'),
    (2, 'Clinique Biasa', 'CLINIQUE', 'Nyékonakpoè', 'Lomé', '22210002', 6.1370, 1.2100, true, 'Consultation, hospitalisation', '08h-20h'),
    (3, 'Pharmacie du Campus', 'PHARMACIE', 'Agoè', 'Lomé', '22210003', 6.2000, 1.1800, true, 'Médicaments', '07h-22h'),
    (4, 'Centre Médical Kara', 'CENTRE_DE_SANTE', 'Quartier central', 'Kara', '26210004', 9.5511, 1.1860, true, 'Consultation, soins primaires', '08h-18h');

-- =========================
-- RELATION STRUCTURE / PRESTATION
-- =========================
INSERT INTO structure_prestation (structure_id, prestation_id)
VALUES
    (1, 1),
    (1, 2),
    (2, 1),
    (2, 4),
    (3, 3),
    (4, 1);

-- =========================
-- DEMANDES
-- =========================
INSERT INTO demande (
    id_demande,
    type_demande,
    date_demande,
    statut,
    description,
    resultat,
    assure_id,
    prestation_id
)
VALUES
    (1, 'VERIFICATION_PRISE_EN_CHARGE', NOW(), 'TRAITEE',
     'Vérification de la prise en charge pour une consultation générale',
     'Acte couvert à 80%, carte AMU requise',
     1, 1),

    (2, 'VERIFICATION_PRISE_EN_CHARGE', NOW(), 'REJETEE',
     'Vérification pour soin dentaire esthétique',
     'Acte non couvert par l’AMU',
     1, 5),

    (3, 'QUESTION_CHATBOT', NOW(), 'TRAITEE',
     'Quels documents sont nécessaires ?',
     'Carte AMU + ordonnance selon le cas',
     1, NULL),

    (4, 'RECHERCHE_STRUCTURE', NOW(), 'TRAITEE',
     'Structures disponibles à Lomé',
     'CHU, Clinique Biasa, Pharmacie du Campus',
     1, NULL);

-- =========================
-- HISTORIQUES
-- =========================
INSERT INTO historique (id_historique, date_action, action, details, demande_id)
VALUES
    (1, NOW(), 'CREATION', 'Demande de vérification créée', 1),
    (2, NOW(), 'TRAITEMENT', 'Acte couvert à 80%', 1),

    (3, NOW(), 'CREATION', 'Demande dentaire créée', 2),
    (4, NOW(), 'TRAITEMENT', 'Acte non couvert', 2),

    (5, NOW(), 'CREATION', 'Question chatbot posée', 3),
    (6, NOW(), 'TRAITEMENT', 'Réponse envoyée', 3),

    (7, NOW(), 'CREATION', 'Recherche de structure', 4),
    (8, NOW(), 'TRAITEMENT', 'Liste des structures envoyée', 4);