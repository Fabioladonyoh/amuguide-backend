package com.amuguide.backend.entity;

import com.amuguide.backend.enums.CategorieActe;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "prestation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prestation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPrestation;

    @Column(nullable = false, unique = true, length = 30)
    private String codeActe;

    @Column(nullable = false)
    private String nomActe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CategorieActe categorie;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Double tauxCouverture;

    @Column(nullable = false)
    private Boolean prisEnCharge;

    @Column(columnDefinition = "TEXT")
    private String conditionsPriseEnCharge;

    @Column(columnDefinition = "TEXT")
    private String documentsRequis;

    @ManyToMany(mappedBy = "prestations")
    @Builder.Default
    private List<StructureSante> structures = new ArrayList<>();
}