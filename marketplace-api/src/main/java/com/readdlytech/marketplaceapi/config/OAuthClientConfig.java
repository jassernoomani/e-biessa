package com.readdlytech.marketplaceapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "app.oauth.enabled", havingValue = "true")
public class OAuthClientConfig {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${app.oauth.google.client-id:}") String googleId,
            @Value("${app.oauth.google.client-secret:}") String googleSecret,
            @Value("${app.oauth.facebook.client-id:}") String facebookId,
            @Value("${app.oauth.facebook.client-secret:}") String facebookSecret,
            @Value("${app.oauth.microsoft.client-id:}") String microsoftId,
            @Value("${app.oauth.microsoft.client-secret:}") String microsoftSecret) {
        List<ClientRegistration> registrations = new ArrayList<>();
        if (configured(googleId, googleSecret)) {
            registrations.add(ClientRegistration.withRegistrationId("google")
                    .clientId(googleId).clientSecret(googleSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
                    .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                    .tokenUri("https://oauth2.googleapis.com/token")
                    .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                    .userNameAttributeName("sub")
                    .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs").clientName("Google").build());
        }
        if (configured(facebookId, facebookSecret)) {
            registrations.add(ClientRegistration.withRegistrationId("facebook")
                    .clientId(facebookId).clientSecret(facebookSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("email", "public_profile")
                    .authorizationUri("https://www.facebook.com/dialog/oauth")
                    .tokenUri("https://graph.facebook.com/oauth/access_token")
                    .userInfoUri("https://graph.facebook.com/me?fields=id,name,email")
                    .userNameAttributeName("id").clientName("Facebook").build());
        }
        if (configured(microsoftId, microsoftSecret)) {
            registrations.add(ClientRegistration.withRegistrationId("microsoft")
                    .clientId(microsoftId).clientSecret(microsoftSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
                    .authorizationUri("https://login.microsoftonline.com/common/oauth2/v2.0/authorize")
                    .tokenUri("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                    .userInfoUri("https://graph.microsoft.com/oidc/userinfo")
                    .userNameAttributeName("sub")
                    .jwkSetUri("https://login.microsoftonline.com/common/discovery/v2.0/keys")
                    .clientName("Microsoft").build());
        }
        if (registrations.isEmpty()) throw new IllegalStateException("OAuth is enabled but no provider credentials are configured");
        return new InMemoryClientRegistrationRepository(registrations);
    }

    private boolean configured(String id, String secret) {
        return id != null && !id.isBlank() && secret != null && !secret.isBlank();
    }
}
