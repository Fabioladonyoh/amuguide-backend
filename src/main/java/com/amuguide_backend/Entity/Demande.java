package com.amuguide_backend.Entity;

import com.amuguide_backend.EnumType.StatutDemande;
import com.amuguide_backend.EnumType.TypeDemande;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "demandes")
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

    @Column(length = 1000, nullable = false)
    private String description;

    @Column(length = 1000)
    private String resultat;

    @ManyToOne
    @JoinColumn(name = "assure_id", nullable = false)
    private AssureAMU assure;

    @ManyToOne
    @JoinColumn(name = "prestation_id")
    private Prestation prestation;

    @OneToMany(mappedBy = "demande", cascade = CascadeType.ALL, orphanRemoval = true)
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
