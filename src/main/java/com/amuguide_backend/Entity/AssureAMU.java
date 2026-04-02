package com.amuguide_backend.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "assures")
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

    @Column(nullable = false , unique = true)
    private String numeroAMU;

    @Column(nullable = false)
    private String telephone;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String adresse;

    @OneToMany(mappedBy = "assure", cascade = CascadeType.ALL , orphanRemoval = true)
    private List<Demande> demandes = new ArrayList<>();


}
