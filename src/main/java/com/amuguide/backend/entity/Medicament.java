package com.amuguide.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "medicament")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medicament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false)
    private String nom;

    private String dci;

    private String dosage;

    private String formePharmaceutique;

    private String categorie;

    private Boolean prisEnCharge;

    private Double tauxCouverture;

    @Column(columnDefinition = "TEXT")
    private String conditions;

    private Boolean actif;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (prisEnCharge == null) {
            prisEnCharge = true;
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
