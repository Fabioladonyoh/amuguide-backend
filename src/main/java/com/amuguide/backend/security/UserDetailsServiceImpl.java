package com.amuguide.backend.security;

import com.amuguide.backend.entity.Administrateur;
import com.amuguide.backend.entity.AssureAMU;
import com.amuguide.backend.enums.StatutAssure;
import com.amuguide.backend.repository.AdministrateurRepository;
import com.amuguide.backend.repository.AssureAMURepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Résout les utilisateurs depuis la base selon le préfixe du subject JWT :
 *   "ADMIN:<login>"   → table administrateur
 *   "ASSURE:<numAMU>" → table assure_amu
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AdministrateurRepository administrateurRepository;
    private final AssureAMURepository assureAMURepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        if (username.startsWith("ADMIN:")) {
            String login = username.substring(6);
            Administrateur admin = administrateurRepository.findByLogin(login)
                    .orElseThrow(() -> new UsernameNotFoundException("Admin non trouvé : " + login));

            if (!Boolean.TRUE.equals(admin.getActif())) {
                throw new UsernameNotFoundException("Compte admin désactivé : " + login);
            }

            return User.builder()
                    .username(username)
                    .password(admin.getMotDePasse())
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                    .build();
        }

        if (username.startsWith("ASSURE:")) {
            String numeroAMU = username.substring(7);
            AssureAMU assure = assureAMURepository.findByNumeroAMU(numeroAMU)
                    .orElseThrow(() -> new UsernameNotFoundException("Assuré non trouvé : " + numeroAMU));

            if (assure.getStatut() != StatutAssure.ACTIF) {
                throw new UsernameNotFoundException("Compte assuré inactif : " + numeroAMU);
            }

            return User.builder()
                    .username(username)
                    .password(assure.getMotDePasse())
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_ASSURE")))
                    .build();
        }

        throw new UsernameNotFoundException("Format d'identifiant invalide : " + username);
    }
}
