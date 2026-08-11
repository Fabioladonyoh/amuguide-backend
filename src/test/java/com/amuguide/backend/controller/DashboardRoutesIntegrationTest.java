package com.amuguide.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

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

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

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
        assertThat(body.get("totalStructuresSante").asLong()).isEqualTo(body.get("totalStructures").asLong());
        assertThat(body.get("structuresSanteActives").asLong()).isEqualTo(body.get("structuresActives").asLong());
        assertThat(body.get("totalPharmacies").asLong()).isGreaterThanOrEqualTo(51);
        assertThat(body.get("pharmaciesActives").asLong()).isGreaterThanOrEqualTo(51);
        assertThat(body.get("pharmaciesAgreees").asLong()).isGreaterThanOrEqualTo(51);
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
    void adminAndAssurePrestationListsExposeOfficialActiveReferenceData() throws Exception {
        HttpResponse<String> adminPrestations = send(get("/api/admin/prestations?size=50&status=true", adminToken()));
        assertThat(adminPrestations.statusCode()).isEqualTo(200);
        JsonNode adminContent = json(adminPrestations).get("content");
        assertThat(adminContent).hasSize(18);
        assertPrestation(adminContent, "CONSULTATION_GENERALE", 80);
        assertPrestation(adminContent, "CONSULTATION_ENFANT_MOINS_5_ANS", 100);
        assertPrestation(adminContent, "CONSULTATION_SPECIALITE", 80);
        assertPrestation(adminContent, "HOSPITALISATION_SEJOUR", 90);
        assertPrestation(adminContent, "INTERVENTION_CHIRURGICALE", 90);
        assertPrestation(adminContent, "ACCOUCHEMENT_SIMPLE", 100);
        assertPrestation(adminContent, "ACCOUCHEMENT_COMPLIQUE", 100);
        assertPrestation(adminContent, "CESARIENNE", 100);
        assertPrestation(adminContent, "ECHOGRAPHIE", 80);
        assertPrestation(adminContent, "RADIOLOGIE", 80);
        assertPrestation(adminContent, "MEDICAMENTS", 80);
        assertPrestation(adminContent, "CONSULTATION_PRENATALE", 80);

        HttpResponse<String> assurePrestations = send(get("/api/assure/prestations?size=50", assureToken()));
        assertThat(assurePrestations.statusCode()).isEqualTo(200);
        JsonNode assureContent = json(assurePrestations).get("content");
        assertThat(assureContent).hasSize(18);
        assertPrestation(assureContent, "SOINS_DENTAIRES", 80);
        assertPrestation(assureContent, "MEDICAMENTS", 80);
    }

    @Test
    void adminPrestationCrudRoutesRemainOperational() throws Exception {
        String token = adminToken();
        String code = "TEST_PRESTATION_CRUD_" + System.nanoTime();

        HttpResponse<String> created = send(post(
                "/api/admin/prestations",
                token,
                """
                        {
                          "codeActe": "%s",
                          "nomActe": "Prestation test CRUD",
                          "categorie": "CONSULTATION",
                          "description": "",
                          "prisEnCharge": true,
                          "tauxCouverture": 80,
                          "conditionsPriseEnCharge": "",
                          "documentsRequis": ""
                        }
                        """.formatted(code)
        ));
        assertThat(created.statusCode()).isEqualTo(201);
        long id = json(created).get("idPrestation").asLong();

        HttpResponse<String> byId = send(get("/api/admin/prestations/" + id, token));
        assertThat(byId.statusCode()).isEqualTo(200);
        assertThat(json(byId).get("codeActe").asText()).isEqualTo(code);

        HttpResponse<String> updated = send(put(
                "/api/admin/prestations/" + id,
                token,
                """
                        {
                          "codeActe": "%s",
                          "nomActe": "Prestation test CRUD modifiee",
                          "categorie": "CONSULTATION",
                          "description": "",
                          "prisEnCharge": true,
                          "tauxCouverture": 90,
                          "conditionsPriseEnCharge": "",
                          "documentsRequis": ""
                        }
                        """.formatted(code)
        ));
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(json(updated).get("tauxCouverture").asInt()).isEqualTo(90);

        HttpResponse<String> status = send(patch(
                "/api/admin/prestations/" + id + "/status",
                token,
                """
                        {
                          "actif": false
                        }
                        """
        ));
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(json(status).get("prisEnCharge").asBoolean()).isFalse();

        HttpResponse<String> deleted = send(delete("/api/admin/prestations/" + id, token));
        assertThat(deleted.statusCode()).isEqualTo(204);
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
    void adminAndAssureCanSearchImportedPartnerPharmaciesInDedicatedModule() throws Exception {
        HttpResponse<String> adminPharmacies = send(get("/api/admin/pharmacies?size=100", adminToken()));
        assertThat(adminPharmacies.statusCode()).isEqualTo(200);
        JsonNode adminContent = json(adminPharmacies).get("content");
        assertThat(adminContent).hasSizeGreaterThanOrEqualTo(51);
        assertPharmacy(adminContent, "Pharmacie Bon Pasteur", "22 21 13 67", true, true);
        assertPharmacy(adminContent, "Pharmacie El-Nissi", "99 73 39 32", true, true);
        assertPharmacy(adminContent, "Pharmacie Sika", "92 62 06 51", true, true);

        String token = assureToken();
        HttpResponse<String> allForAssure = send(get("/api/assure/pharmacies", token));
        assertThat(allForAssure.statusCode()).isEqualTo(200);
        JsonNode allForAssurePage = json(allForAssure);
        assertThat(allForAssurePage.get("content").isArray()).isTrue();
        assertThat(allForAssurePage.get("totalElements").asLong()).isGreaterThanOrEqualTo(321);
        assertThat(allForAssurePage.get("size").asInt()).isEqualTo(10);
        assertThat(allForAssurePage.get("number").asInt()).isZero();
        assertOfficialPharmacyPage(allForAssurePage.get("content"));

        HttpResponse<String> byDepot = send(get("/api/assure/pharmacies/search?query=DEPOT&size=100", token));
        assertThat(byDepot.statusCode()).isEqualTo(200);
        JsonNode depotPage = json(byDepot);
        assertThat(depotPage.get("content").isArray()).isTrue();
        assertThat(depotPage.get("totalElements").asLong()).isGreaterThanOrEqualTo(25);
        assertOfficialPharmacyPage(depotPage.get("content"));

        HttpResponse<String> byOfficialType = send(get("/api/assure/pharmacies/search?query=DEPOT%20PHARMACIE&size=100", token));
        assertThat(byOfficialType.statusCode()).isEqualTo(200);
        assertThat(json(byOfficialType).get("content")).hasSizeGreaterThanOrEqualTo(1);

        HttpResponse<String> bonPasteur = send(get("/api/assure/pharmacies/search?search=Bon&size=100", token));
        assertThat(bonPasteur.statusCode()).isEqualTo(200);
        assertThat(json(bonPasteur).get("content").isArray()).isTrue();

        long firstPharmacyId = allForAssurePage.get("content").get(0).get("id").asLong();
        HttpResponse<String> byId = send(get("/api/assure/pharmacies/" + firstPharmacyId, token));
        assertThat(byId.statusCode()).isEqualTo(200);
        assertThat(json(byId).get("id").asLong()).isEqualTo(firstPharmacyId);

        HttpResponse<String> unauthorized = send(get("/api/assure/pharmacies", null));
        assertThat(unauthorized.statusCode()).isEqualTo(401);

        HttpResponse<String> forbidden = send(get("/api/assure/pharmacies", adminToken()));
        assertThat(forbidden.statusCode()).isEqualTo(403);

        HttpResponse<String> byPhone = send(get("/api/admin/pharmacies?search=99733932&size=20", adminToken()));
        assertThat(byPhone.statusCode()).isEqualTo(200);
        assertPharmacy(json(byPhone).get("content"), "Pharmacie El-Nissi", "99 73 39 32", true, true);
        HttpResponse<String> structuresPharmacyFilter = send(get("/api/admin/structures?type=PHARMACIE&size=20", adminToken()));
        assertThat(structuresPharmacyFilter.statusCode()).isEqualTo(400);
    }

    @Test
    void assurePharmacyRoutesAreRegisteredAndPublishedInOpenApi() throws Exception {
        assertThat(requestMappingHandlerMapping.getHandlerMethods().keySet())
                .anySatisfy(mapping -> assertThat(mapping.toString()).contains("GET [/api/assure/pharmacies]"))
                .anySatisfy(mapping -> assertThat(mapping.toString()).contains("GET [/api/assure/pharmacies/{id}]"));

        HttpResponse<String> openApi = send(get("/v3/api-docs", null));
        assertThat(openApi.statusCode()).isEqualTo(200);
        JsonNode paths = json(openApi).get("paths");
        assertThat(paths.has("/api/assure/pharmacies")).isTrue();
        assertThat(paths.get("/api/assure/pharmacies").has("get")).isTrue();
        assertThat(paths.has("/api/assure/pharmacies/{id}")).isTrue();
        assertThat(paths.get("/api/assure/pharmacies/{id}").has("get")).isTrue();
        assertThat(paths.has("/api/assure/pharmacies/search")).isTrue();
        assertThat(paths.get("/api/assure/pharmacies/search").has("get")).isTrue();
    }

    @Test
    void adminPharmacyCrudRoutesAcceptPharmaciesWithoutCoordinates() throws Exception {
        String token = adminToken();
        String suffix = String.valueOf(System.nanoTime());

        HttpResponse<String> created = send(post(
                "/api/admin/pharmacies",
                token,
                """
                        {
                          "nom": "Pharmacie test %s",
                          "adresse": "Quartier test",
                          "quartier": "Quartier test",
                          "ville": "Lome",
                          "telephone": "90 00 00 00",
                          "agreee": true,
                          "active": true
                        }
                        """.formatted(suffix)
        ));
        assertThat(created.statusCode()).isEqualTo(201);
        long id = json(created).get("id").asLong();
        assertThat(json(created).get("latitude").isNull()).isTrue();
        assertThat(json(created).get("longitude").isNull()).isTrue();

        HttpResponse<String> byId = send(get("/api/admin/pharmacies/" + id, token));
        assertThat(byId.statusCode()).isEqualTo(200);
        assertThat(json(byId).get("nom").asText()).contains("Pharmacie test");

        HttpResponse<String> updated = send(put(
                "/api/admin/pharmacies/" + id,
                token,
                """
                        {
                          "nom": "Pharmacie test modifiee %s",
                          "adresse": "Quartier test modifie",
                          "quartier": "Quartier test",
                          "ville": "Lome",
                          "telephone": "91 00 00 00",
                          "agreee": true,
                          "active": true
                        }
                        """.formatted(suffix)
        ));
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(json(updated).get("telephone").asText()).isEqualTo("91 00 00 00");

        HttpResponse<String> status = send(patch(
                "/api/admin/pharmacies/" + id + "/status",
                token,
                """
                        {
                          "actif": false
                        }
                        """
        ));
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(json(status).get("active").asBoolean()).isFalse();

        HttpResponse<String> hiddenForAssure = send(get("/api/assure/pharmacies/" + id, assureToken()));
        assertThat(hiddenForAssure.statusCode()).isEqualTo(404);

        HttpResponse<String> deleted = send(delete("/api/admin/pharmacies/" + id, token));
        assertThat(deleted.statusCode()).isEqualTo(204);
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

    @Test
    void chatbotUsesOfficialPrestationRatesFromDatabase() throws Exception {
        assertChatbotRate("Quel est le taux de prise en charge de la consultation generale ?", "CONSULTATION_GENERALE", 80);
        assertChatbotRate("Quel est le taux pour l'hospitalisation ?", "HOSPITALISATION_SEJOUR", 90);
        assertChatbotRate("La cesarienne est-elle prise en charge ?", "CESARIENNE", 100);
    }

    @Test
    void chatbotFindsPartnerPharmaciesFromDatabase() throws Exception {
        HttpResponse<String> bonPasteur = send(post(
                "/api/chatbot/message",
                assureToken(),
                """
                        {
                          "message": "Ou se trouve la pharmacie Bon Pasteur ?",
                          "sessionId": "pharmacy-chatbot-test"
                        }
                        """
        ));
        assertThat(bonPasteur.statusCode()).isEqualTo(200);
        assertThat(json(bonPasteur).get("answer").asText()).contains("44 Av. de la Liberation");
        assertThat(json(bonPasteur).get("answer").asText()).contains("22 21 13 67");

        HttpResponse<String> elNissi = send(post(
                "/api/chatbot/message",
                assureToken(),
                """
                        {
                          "message": "Quel est le numero de la pharmacie El-Nissi ?",
                          "sessionId": "pharmacy-chatbot-test"
                        }
                        """
        ));
        assertThat(elNissi.statusCode()).isEqualTo(200);
        assertThat(json(elNissi).get("answer").asText()).contains("99 73 39 32");
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

    private HttpRequest patch(String path, String token, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body));
        addAuth(builder, token);
        return builder.build();
    }

    private HttpRequest delete(String path, String token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(path)).DELETE();
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

    private void assertPrestation(JsonNode content, String codeActe, int tauxCouverture) {
        for (JsonNode prestation : content) {
            if (codeActe.equals(prestation.get("codeActe").asText())) {
                assertThat(prestation.get("tauxCouverture").asInt()).isEqualTo(tauxCouverture);
                assertThat(prestation.get("prisEnCharge").asBoolean()).isTrue();
                return;
            }
        }
        throw new AssertionError("Prestation absente : " + codeActe);
    }

    private void assertStructure(JsonNode content, String nom, String type, String telephone, boolean agrement, boolean actif) {
        for (JsonNode structure : content) {
            if (nom.equals(structure.get("nom").asText())) {
                assertThat(structure.get("type").asText()).isEqualTo(type);
                assertThat(structure.get("telephone").asText()).isEqualTo(telephone);
                assertThat(structure.get("agrementAMU").asBoolean()).isEqualTo(agrement);
                assertThat(structure.get("actif").asBoolean()).isEqualTo(actif);
                assertThat(structure.get("latitude").isNull()).isTrue();
                assertThat(structure.get("longitude").isNull()).isTrue();
                return;
            }
        }
        throw new AssertionError("Structure absente : " + nom);
    }

    private void assertPharmacy(JsonNode content, String nom, String telephone, boolean agreee, boolean active) {
        for (JsonNode pharmacie : content) {
            if (nom.equals(pharmacie.get("nom").asText())) {
                assertThat(pharmacie.get("telephone").asText()).isEqualTo(telephone);
                assertThat(pharmacie.get("agreee").asBoolean()).isEqualTo(agreee);
                assertThat(pharmacie.get("active").asBoolean()).isEqualTo(active);
                assertThat(pharmacie.get("latitude").isNull()).isTrue();
                assertThat(pharmacie.get("longitude").isNull()).isTrue();
                return;
            }
        }
        throw new AssertionError("Pharmacie absente : " + nom);
    }

    private void assertOfficialPharmacyPage(JsonNode content) {
        assertThat(content).hasSizeGreaterThanOrEqualTo(1);
        for (JsonNode pharmacie : content) {
            assertThat(pharmacie.get("code").asText()).doesNotStartWith("PHARM_");
            assertThat(pharmacie.get("telephone").isNull()).isTrue();
            assertThat(pharmacie.get("agreee").asBoolean()).isTrue();
            assertThat(pharmacie.get("active").asBoolean()).isTrue();
            assertThat(pharmacie.get("notes").asText()).isIn("PHARMACIE", "DEPOT PHARMACIE", "DEPOT PHARMACEUTIQUE");
        }
    }

    private void assertChatbotRate(String message, String codeActe, int tauxCouverture) throws Exception {
        HttpResponse<String> response = send(post(
                "/api/chatbot/message",
                assureToken(),
                """
                        {
                          "message": "%s",
                          "sessionId": "official-rates-test"
                        }
                        """.formatted(message)
        ));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("found").asBoolean()).isTrue();
        assertThat(body.get("codeActe").asText()).isEqualTo(codeActe);
        assertThat(body.get("tauxCouverture").asInt()).isEqualTo(tauxCouverture);
    }
}
