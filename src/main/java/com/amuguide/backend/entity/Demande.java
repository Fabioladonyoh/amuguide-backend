package com.amuguide.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.amuguide.backend.enums.StatutDemande;
import com.amuguide.backend.enums.TypeDemande;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "demande")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Demande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDemande;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeDemande typeDemande;

    @Column(nullable = false)
    private LocalDateTime dateDemande;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutDemande statut;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String resultat;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assure_id", nullable = false)
    private AssureAMU assure;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prestation_id")
    private Prestation prestation;

    @JsonIgnore
    @OneToMany(mappedBy = "demande", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Historique> historiques = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (dateDemande == null) {
            dateDemande = LocalDateTime.now();
        }
        if (statut == null) {
            statut = StatutDemande.EN_ATTENTE;
        }
    }
}