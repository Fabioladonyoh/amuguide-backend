package com.amuguide.backend.service;

import com.amuguide.backend.dto.ContactRequestDTO;
import com.amuguide.backend.dto.ContactResponseDTO;
import com.amuguide.backend.entity.ContactMessage;
import com.amuguide.backend.exception.ResourceNotFoundException;
import com.amuguide.backend.repository.ContactMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactMessageRepository contactMessageRepository;

    public ContactResponseDTO envoyerMessage(ContactRequestDTO request) {
        ContactMessage message = ContactMessage.builder()
                .nom(request.getNom())
                .prenom(blankToEmpty(request.getPrenom()))
                .telephone(blankToEmpty(request.getTelephone()))
                .email(request.getEmail())
                .sujet(request.getSujet())
                .message(request.getMessage())
                .build();
        return toDTO(contactMessageRepository.save(message));
    }

    public List<ContactResponseDTO> getAllMessages() {
        return contactMessageRepository.findAllByOrderByDateEnvoiDesc()
                .stream().map(this::toDTO).toList();
    }

    public ContactResponseDTO marquerCommeTraite(Long id) {
        ContactMessage msg = findById(id);
        msg.setTraite(true);
        return toDTO(contactMessageRepository.save(msg));
    }

    public ContactResponseDTO repondre(Long id, String reponse) {
        ContactMessage msg = findById(id);
        msg.setReponse(reponse);
        msg.setDateReponse(LocalDateTime.now());
        msg.setTraite(true);
        return toDTO(contactMessageRepository.save(msg));
    }

    public void supprimer(Long id) {
        contactMessageRepository.delete(findById(id));
    }

    private ContactMessage findById(Long id) {
        return contactMessageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message de contact non trouvé : " + id));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private ContactResponseDTO toDTO(ContactMessage m) {
        return ContactResponseDTO.builder()
                .id(m.getId())
                .nom(m.getNom())
                .prenom(m.getPrenom())
                .telephone(m.getTelephone())
                .email(m.getEmail())
                .sujet(m.getSujet())
                .message(m.getMessage())
                .dateEnvoi(m.getDateEnvoi())
                .traite(m.getTraite())
                .reponse(m.getReponse())
                .dateReponse(m.getDateReponse())
                .build();
    }
}
