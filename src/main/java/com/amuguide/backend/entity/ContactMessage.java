package com.amuguide.backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "contact_message")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false, length = 20)
    private String telephone;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String sujet;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private LocalDateTime dateEnvoi;

    @Column(nullable = false)
    private Boolean traite;

    @Column(columnDefinition = "TEXT")
    private String reponse;

    private LocalDateTime dateReponse;

    @PrePersist
    public void prePersist() {
        if (dateEnvoi == null) dateEnvoi = LocalDateTime.now();
        if (traite == null) traite = false;
    }
}
