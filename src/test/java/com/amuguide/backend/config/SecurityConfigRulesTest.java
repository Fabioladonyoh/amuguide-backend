package com.amuguide.backend.config;

import com.amuguide.backend.security.JwtAuthenticationFilter;
import com.amuguide.backend.security.JwtService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = {
                SecurityConfig.class,
                CorsConfig.class,
                JwtAuthenticationFilter.class,
                SecurityConfigRulesTest.TestSecurityController.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
class SecurityConfigRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    void adminEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/security-test"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointRejectsInvalidJwt() throws Exception {
        when(jwtService.extractUsername("bad-token")).thenThrow(new JwtException("bad token"));

        mockMvc.perform(get("/api/admin/security-test")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointRejectsAssureRole() throws Exception {
        mockMvc.perform(get("/api/admin/security-test").with(user("assure").roles("ASSURE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointAcceptsAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/security-test").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void assureEndpointAcceptsAssureRole() throws Exception {
        mockMvc.perform(get("/api/assure/security-test").with(user("assure").roles("ASSURE")))
                .andExpect(status().isOk());
    }

    @Test
    void importEndpointRejectsAssureRole() throws Exception {
        mockMvc.perform(post("/api/admin/structures/import").with(user("assure").roles("ASSURE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void importEndpointAcceptsAdminRole() throws Exception {
        mockMvc.perform(post("/api/admin/structures/import").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void chatbotEndpointRemainsPublic() throws Exception {
        mockMvc.perform(post("/api/chat"))
                .andExpect(status().isOk());
    }

    @RestController
    static class TestSecurityController {

        @GetMapping("/api/admin/security-test")
        String admin() {
            return "admin";
        }

        @PostMapping("/api/admin/structures/import")
        String importStructures() {
            return "import";
        }

        @GetMapping("/api/assure/security-test")
        String assure() {
            return "assure";
        }

        @PostMapping("/api/chat")
        String chat() {
            return "chat";
        }
    }
}
