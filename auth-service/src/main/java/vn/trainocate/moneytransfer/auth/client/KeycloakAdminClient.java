package vn.trainocate.moneytransfer.auth.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import vn.trainocate.moneytransfer.auth.config.KeycloakAdminProperties;
import vn.trainocate.moneytransfer.auth.exception.BusinessException;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdminClient {

    private final RestTemplate restTemplate;
    private final KeycloakAdminProperties props;

    /**
     * Obtain an admin access token via client_credentials grant.
     */
    public String getAdminToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", props.getClientId());
        body.add("client_secret", props.getClientSecret());

        ResponseEntity<Map> response = restTemplate.postForEntity(
                props.getTokenUrl(), new HttpEntity<>(body, headers), Map.class);
        return (String) response.getBody().get("access_token");
    }

    /**
     * Login a user via password grant. Returns Keycloak token response map
     * containing access_token, refresh_token, expires_in, etc.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loginUser(String username, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", props.getClientId());
        body.add("client_secret", props.getClientSecret());
        body.add("username", username);
        body.add("password", password);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    props.getTokenUrl(), new HttpEntity<>(body, headers), Map.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.warn("Keycloak login failed for user '{}': {}", username, e.getStatusCode());
            throw new BusinessException("AUTH_INVALID_CREDENTIALS", "Invalid username or password");
        }
    }

    /**
     * Refresh a Keycloak token via refresh_token grant.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> refreshToken(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", props.getClientId());
        body.add("client_secret", props.getClientSecret());
        body.add("refresh_token", refreshToken);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    props.getTokenUrl(), new HttpEntity<>(body, headers), Map.class);
            return response.getBody();
        } catch (HttpClientErrorException e) {
            log.warn("Keycloak token refresh failed: {}", e.getStatusCode());
            throw new BusinessException("AUTH_INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
        }
    }

    /**
     * Create a user in Keycloak and return the assigned Keycloak user UUID.
     * The UUID is extracted from the Location header of the 201 response.
     */
    public String createUser(String username, String password, String email, String firstName) {
        String adminToken = getAdminToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        Map<String, Object> userRepresentation = Map.of(
                "username", username,
                "email", email != null ? email : "",
                "firstName", firstName != null ? firstName : "",
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(Map.of("type", "password", "value", password, "temporary", false)),
                "realmRoles", List.of("USER")
        );

        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(
                    props.getAdminUsersUrl(),
                    new HttpEntity<>(userRepresentation, headers),
                    Void.class);

            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            if (location == null) {
                throw new BusinessException("AUTH_KEYCLOAK_ERROR", "Keycloak did not return user Location");
            }
            // Location: .../admin/realms/money-transfer/users/{uuid}
            return location.substring(location.lastIndexOf('/') + 1);
        } catch (BusinessException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("Failed to create Keycloak user '{}': {}", username, e.getStatusCode());
            throw new BusinessException("AUTH_KEYCLOAK_ERROR", "Failed to create user: " + e.getMessage());
        }
    }

    /**
     * Update a user's username in Keycloak.
     */
    public void updateUsername(String keycloakUserId, String newUsername) {
        String adminToken = getAdminToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        Map<String, Object> update = Map.of("username", newUsername);

        restTemplate.exchange(
                props.getAdminUserUrl(keycloakUserId),
                HttpMethod.PUT,
                new HttpEntity<>(update, headers),
                Void.class);
    }

    /**
     * Revoke all active sessions for the given Keycloak user UUID.
     * Called during logout to invalidate all tokens server-side.
     */
    public void revokeUserSessions(String keycloakUserId) {
        String adminToken = getAdminToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        try {
            restTemplate.exchange(
                    props.getAdminUserSessionsUrl(keycloakUserId),
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    Void.class);
        } catch (HttpClientErrorException e) {
            // 404 means no active sessions — treat as success
            log.warn("Revoke sessions for user {}: {}", keycloakUserId, e.getStatusCode());
        }
    }
}
