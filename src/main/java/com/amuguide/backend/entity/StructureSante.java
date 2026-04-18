package com.amuguide.backend.entity;

import com.amuguide.backend.enums.TypeStructure;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "structure_sante")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StructureSante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idStructure;

    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeStructure type;

    @Column(nullable = false)
    private String adresse;

    @Column(nullable = false)
    private String ville;

    @Column(nullable = false, length = 20)
    private String telephone;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private Boolean agrementAMU;

    @Column(columnDefinition = "TEXT")
    private String specialites;

    private String horaires;

    @ManyToMany
    @JoinTable(
            name = "structure_prestation",
            joinColumns = @JoinColumn(name = "structure_id"),
            inverseJoinColumns = @JoinColumn(name = "prestation_id")
    )
    @Builder.Default
    private List<Prestation> prestations = new ArrayList<>();
}