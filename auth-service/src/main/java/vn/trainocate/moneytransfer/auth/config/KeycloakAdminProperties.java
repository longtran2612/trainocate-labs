package vn.trainocate.moneytransfer.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakAdminProperties {

    private String serverUrl;
    private String realm;
    private String clientId;
    private String clientSecret;

    public String getTokenUrl() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    public String getAdminUsersUrl() {
        return serverUrl + "/admin/realms/" + realm + "/users";
    }

    public String getAdminUserUrl(String userId) {
        return serverUrl + "/admin/realms/" + realm + "/users/" + userId;
    }

    public String getAdminUserSessionsUrl(String userId) {
        return serverUrl + "/admin/realms/" + realm + "/users/" + userId + "/sessions";
    }
}
