package com.amuguide_backend.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "prestations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prestation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPrestation;

    @Column(nullable = false)
    private String nomActe;

    private String categorie;

    @Column(length = 1000)
    private String description;

    private Double tauxCouverture;

    @Column(length = 1000)
    private String conditions;

    @ManyToMany(mappedBy = "prestations")
    private List<StructureSante> structures = new ArrayList<>();

}
