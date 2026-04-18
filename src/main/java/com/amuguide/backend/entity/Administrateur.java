package com.amuguide.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "administrateur")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Administrateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAdmin;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String login;

    @Column(nullable = false)
    private String motDePasse;

    @Column(nullable = false)
    private Boolean actif;

    @PrePersist
    public void prePersist() {
        if (actif == null) {
            actif = true;
        }
    }
}