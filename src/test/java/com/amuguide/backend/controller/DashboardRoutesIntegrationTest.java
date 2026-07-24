package com.amuguide.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DashboardRoutesIntegrationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void adminDashboardStatisticsRequireAdminJwtAndReturnDatabaseCounts() throws Exception {
        HttpResponse<String> unauthorized = send(get("/api/admin/dashboard/statistics", null));
        assertThat(unauthorized.statusCode()).isEqualTo(401);

        HttpResponse<String> response = send(get("/api/admin/dashboard/statistics", adminToken()));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("totalAssures").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(body.get("totalAdministrateurs").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(body.get("totalPrestations").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(body.get("totalStructures").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(body.get("totalQuestionsChatbot").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void assureCannotAccessAdminDashboard() throws Exception {
        HttpResponse<String> response = send(get("/api/admin/dashboard/statistics", assureToken()));
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void adminCanSearchAssuresWithPagination() throws Exception {
        HttpResponse<String> response = send(get("/api/admin/assures?page=0&size=5&search=AMU001", adminToken()));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode content = json(response).get("content");
        assertThat(content).hasSizeGreaterThanOrEqualTo(1);
        assertThat(content.get(0).get("numeroAMU").asText()).isEqualTo("AMU001");
    }

    @Test
    void assureDashboardAndSearchRoutesUseConnectedAssure() throws Exception {
        String token = assureToken();

        HttpResponse<String> dashboard = send(get("/api/assure/dashboard", token));
        assertThat(dashboard.statusCode()).isEqualTo(200);
        assertThat(json(dashboard).get("profil").get("numeroAMU").asText()).isEqualTo("AMU001");
        assertThat(json(dashboard).get("totalMessages").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(json(dashboard).get("nombreConversations").asLong()).isGreaterThanOrEqualTo(0);

        HttpResponse<String> prestations = send(get("/api/assure/prestations/search?query=Consultation", token));
        assertThat(prestations.statusCode()).isEqualTo(200);
        assertThat(json(prestations).get("content")).hasSizeGreaterThanOrEqualTo(1);
        assertThat(json(prestations).get("content").get(0).get("prisEnCharge").asBoolean()).isTrue();

        HttpResponse<String> medicaments = send(get("/api/assure/medicaments/search?query=paracetamol", token));
        assertThat(medicaments.statusCode()).isEqualTo(200);
        assertThat(json(medicaments).get("content")).hasSizeGreaterThanOrEqualTo(1);
        assertThat(json(medicaments).get("content").get(0).get("prisEnCharge").asBoolean()).isTrue();
    }

    @Test
    void assureProfileCarteStructuresAndPaginationAreSecured() throws Exception {
        String token = assureToken();

        HttpResponse<String> unauthorized = send(get("/api/assure/profile", null));
        assertThat(unauthorized.statusCode()).isEqualTo(401);

        HttpResponse<String> forbidden = send(get("/api/assure/profile", adminToken()));
        assertThat(forbidden.statusCode()).isEqualTo(403);

        HttpResponse<String> profile = send(get("/api/assure/profile", token));
        assertThat(profile.statusCode()).isEqualTo(200);
        assertThat(json(profile).get("numeroAMU").asText()).isEqualTo("AMU001");

        HttpResponse<String> carte = send(get("/api/assure/carte", token));
        assertThat(carte.statusCode()).isEqualTo(200);
        assertThat(json(carte).get("numeroAmu").asText()).isEqualTo("AMU001");

        HttpResponse<String> structures = send(get("/api/assure/structures/search?page=0&size=2", token));
        assertThat(structures.statusCode()).isEqualTo(200);
        assertThat(json(structures).get("page").asInt()).isEqualTo(0);
        assertThat(json(structures).get("content")).isNotNull();

        HttpResponse<String> notFound = send(get("/api/assure/structures/99999999", token));
        assertThat(notFound.statusCode()).isEqualTo(404);
    }

    @Test
    void assurePasswordValidationAndNoResultSearchReturnBusinessResponses() throws Exception {
        String token = assureToken();

        HttpResponse<String> passwordMismatch = send(put(
                "/api/assure/profile/password",
                token,
                """
                        {
                          "ancienMotDePasse": "mauvais",
                          "nouveauMotDePasse": "nouveau123",
                          "confirmationMotDePasse": "different123"
                        }
                        """
        ));
        assertThat(passwordMismatch.statusCode()).isEqualTo(400);

        HttpResponse<String> noMedication = send(get("/api/assure/medicaments/search?query=medicament-inexistant-xyz", token));
        assertThat(noMedication.statusCode()).isEqualTo(200);
        assertThat(json(noMedication).get("content")).isEmpty();
    }

    @Test
    void chatbotMessageAcceptsAuthenticatedAssureAndReturnsDatabaseAnswer() throws Exception {
        HttpResponse<String> response = send(post(
                "/api/chatbot/message",
                assureToken(),
                """
                        {
                          "message": "Est-ce que la consultation est prise en charge ?",
                          "sessionId": "httpclient-dashboard-test"
                        }
                        """
        ));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("found").asBoolean()).isTrue();
        assertThat(json(response).get("answer").asText()).isNotBlank();
        assertThat(json(response).get("sessionId").asText()).isEqualTo("httpclient-dashboard-test");

        HttpResponse<String> conversations = send(get("/api/assure/chatbot/conversations", assureToken()));
        assertThat(conversations.statusCode()).isEqualTo(200);
        assertThat(json(conversations).get("content")).hasSizeGreaterThanOrEqualTo(1);
        assertThat(json(conversations).get("content").get(0).get("id").asLong()).isPositive();

        long conversationId = json(conversations).get("content").get(0).get("id").asLong();
        HttpResponse<String> detail = send(get("/api/assure/chatbot/conversations/" + conversationId, assureToken()));
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(json(detail).get("messages")).hasSizeGreaterThanOrEqualTo(1);
    }

    private String adminToken() throws Exception {
        return tokenFrom("/api/auth/login", """
                {
                  "identifiant": "admin",
                  "motDePasse": "admin123"
                }
                """);
    }

    private String assureToken() throws Exception {
        return tokenFrom("/api/auth/assure/login", """
                {
                  "numeroAMU": "AMU001",
                  "motDePasse": "14052000"
                }
                """);
    }

    private String tokenFrom(String uri, String body) throws Exception {
        HttpResponse<String> response = send(post(uri, null, body));
        assertThat(response.statusCode()).isEqualTo(200);
        return json(response).get("token").asText();
    }

    private HttpRequest get(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path)).GET();
        addAuth(builder, token);
        return builder.build();
    }

    private HttpRequest post(String path, String token, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        addAuth(builder, token);
        return builder.build();
    }

    private HttpRequest put(String path, String token, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body));
        addAuth(builder, token);
        return builder.build();
    }

    private void addAuth(HttpRequest.Builder builder, String token) {
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    private URI resolve(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }
}
