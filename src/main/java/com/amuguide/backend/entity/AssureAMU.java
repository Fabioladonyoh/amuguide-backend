package com.amuguide.backend.entity;

import com.amuguide.backend.enums.StatutAssure;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assure_amu")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssureAMU {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAssure;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false, unique = true, length = 50)
    private String numeroAMU;

    @Column(nullable = false)
    private LocalDate dateNaissance;

    @Column(nullable = false, length = 20)
    private String telephone;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String adresse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutAssure statut;

    @OneToMany(mappedBy = "assure", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Demande> demandes = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (statut == null) {
            statut = StatutAssure.ACTIF;
        }
    }
}