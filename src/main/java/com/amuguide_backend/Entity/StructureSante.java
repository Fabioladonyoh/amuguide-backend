package com.amuguide_backend.Entity;

import com.amuguide_backend.EnumType.TypeStructure;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "structures_sante")
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
    private TypeStructure type;

    private String adresse;

    private String telephone;

    private Boolean agrementAMU;

    private Double latitude;

    private Double longitude;

    @ManyToMany
    @JoinTable(
            name = "structure_prestation",
            joinColumns = @JoinColumn(name = "structure_id"),
            inverseJoinColumns = @JoinColumn(name = "prestation_id")
    )

    private List<Prestation> prestations = new ArrayList<>();

}
