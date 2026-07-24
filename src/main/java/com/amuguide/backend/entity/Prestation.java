package com.amuguide.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.amuguide.backend.enums.CategorieActe;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "prestation")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
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

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @ManyToMany(mappedBy = "prestations")
    @Builder.Default
    @JsonIgnore
    private List<StructureSante> structures = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (prisEnCharge == null) {
            prisEnCharge = true;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
