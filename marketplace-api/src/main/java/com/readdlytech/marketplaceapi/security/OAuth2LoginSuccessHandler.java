package com.readdlytech.marketplaceapi.security;

import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.service.AuthExchangeService;
import com.readdlytech.marketplaceapi.service.SocialIdentityService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final SocialIdentityService socialIdentityService;
    private final AuthExchangeService exchangeService;
    @Value("${app.frontend-url:http://localhost:4200}") private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        try {
            OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
            OAuth2User principal = token.getPrincipal();
            String provider = token.getAuthorizedClientRegistrationId();
            String subject = attribute(principal, provider.equals("facebook") ? "id" : "sub");
            String email = first(principal, "email", "preferred_username");
            String name = first(principal, "name", "given_name", "email");
            User user = socialIdentityService.findOrCreate(provider, subject, email, name);
            String exchangeCode = exchangeService.create(user);
            if (request.getSession(false) != null) request.getSession(false).invalidate();
            response.sendRedirect(callback("code", exchangeCode));
        } catch (RuntimeException failure) {
            response.sendRedirect(callback("error", "The provider did not return a usable verified email address"));
        }
    }

    private String callback(String name, String value) {
        return UriComponentsBuilder.fromUriString(frontendUrl + "/auth/callback")
                .queryParam(name, value).build().encode().toUriString();
    }

    private String first(OAuth2User user, String... keys) {
        for (String key : keys) {
            String value = attribute(user, key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String attribute(OAuth2User user, String key) {
        Object value = user.getAttributes().get(key);
        return value == null ? null : value.toString();
    }
}
