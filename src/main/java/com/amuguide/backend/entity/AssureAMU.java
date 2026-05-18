package com.amuguide.backend.entity;

import com.amuguide.backend.enums.StatutAssure;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "assure_amu")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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

    @JsonIgnore
    @Column(nullable = true)
    private String motDePasse;

    @JsonIgnore
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