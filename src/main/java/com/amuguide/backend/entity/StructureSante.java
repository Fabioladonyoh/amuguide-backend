package com.amuguide.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.amuguide.backend.enums.TypeStructure;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "structure_sante")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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

    private String region;

    @Column(nullable = false, length = 20)
    private String telephone;

    private String email;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false)
    private Boolean agrementAMU;

    private Boolean actif;

    @Column(columnDefinition = "TEXT")
    private String specialites;

    private String horaires;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @ManyToMany
    @JoinTable(
            name = "structure_prestation",
            joinColumns = @JoinColumn(name = "structure_id"),
            inverseJoinColumns = @JoinColumn(name = "prestation_id")
    )
    @Builder.Default
    @JsonIgnore
    private List<Prestation> prestations = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (agrementAMU == null) {
            agrementAMU = true;
        }
        if (actif == null) {
            actif = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
