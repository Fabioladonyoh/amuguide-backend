package com.amuguide.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "hospitalisation_tarif",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hospitalisation_tarif_officiel",
                columnNames = {"categorie", "chambre", "population", "type_prestataire", "date_debut"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HospitalisationTarif {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String categorie;

    @Column(nullable = false)
    private String chambre;

    @Column(nullable = false)
    private String population;

    @Column(name = "type_prestataire", nullable = false)
    private String typePrestataire;

    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;

    @Column(name = "taux_remboursement", nullable = false)
    private Double tauxRemboursement;

    @Column(name = "premiere_semaine")
    private String premiereSemaine;

    @Column(name = "deuxieme_semaine")
    private String deuxiemeSemaine;

    @Column(name = "a_partir_troisieme_semaine")
    private String aPartirTroisiemeSemaine;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
