package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.RecoveryCompleteRequest;
import com.readdlytech.marketplaceapi.dto.RecoveryStartResponse;
import com.readdlytech.marketplaceapi.entity.PasswordRecoveryCode;
import com.readdlytech.marketplaceapi.entity.RecoveryChannel;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.repository.PasswordRecoveryCodeRepository;
import com.readdlytech.marketplaceapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private static final String GENERIC_MESSAGE = "If an account matches, a recovery code has been sent.";
    private final UserRepository userRepository;
    private final PasswordRecoveryCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.recovery.code-minutes:10}") private long codeMinutes;
    @Value("${app.recovery.email.enabled:false}") private boolean emailEnabled;
    @Value("${app.recovery.sms.enabled:false}") private boolean smsEnabled;
    @Value("${app.recovery.sms.account-sid:}") private String twilioSid;
    @Value("${app.recovery.sms.auth-token:}") private String twilioToken;
    @Value("${app.recovery.sms.from:}") private String twilioFrom;

    @Transactional
    public RecoveryStartResponse start(String identifier) {
        RecoveryChannel channel = channel(identifier);
        Optional<User> match = findUser(identifier, channel);
        if (match.isEmpty()) return new RecoveryStartResponse(GENERIC_MESSAGE, null);
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        PasswordRecoveryCode recovery = new PasswordRecoveryCode();
        recovery.setUser(match.get());
        recovery.setChannel(channel);
        recovery.setCodeHash(passwordEncoder.encode(code));
        recovery.setCreatedAt(LocalDateTime.now());
        recovery.setExpiresAt(LocalDateTime.now().plusMinutes(codeMinutes));
        codeRepository.save(recovery);
        deliver(match.get(), channel, code);
        String developmentCode = environment.acceptsProfiles(Profiles.of("dev")) ? code : null;
        return new RecoveryStartResponse(GENERIC_MESSAGE, developmentCode);
    }

    @Transactional
    public void complete(RecoveryCompleteRequest request) {
        RecoveryChannel channel = channel(request.identifier());
        User user = findUser(request.identifier(), channel)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired recovery code"));
        PasswordRecoveryCode recovery = codeRepository.findTopByUserAndChannelOrderByCreatedAtDesc(user, channel)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired recovery code"));
        if (recovery.getUsedAt() != null || recovery.getExpiresAt().isBefore(LocalDateTime.now()) || recovery.getAttempts() >= 5) {
            throw new IllegalArgumentException("Invalid or expired recovery code");
        }
        recovery.setAttempts(recovery.getAttempts() + 1);
        if (!passwordEncoder.matches(request.code(), recovery.getCodeHash())) {
            throw new IllegalArgumentException("Invalid or expired recovery code");
        }
        recovery.setUsedAt(LocalDateTime.now());
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private Optional<User> findUser(String identifier, RecoveryChannel channel) {
        if (channel == RecoveryChannel.EMAIL) return userRepository.findByEmailIgnoreCase(identifier.trim());
        String normalized = phone(identifier);
        return userRepository.findAll().stream().filter(user -> phone(user.getPhone()).equals(normalized)).findFirst();
    }

    private void deliver(User user, RecoveryChannel channel, String code) {
        String text = "Your E-biessa recovery code is " + code + ". It expires in " + codeMinutes + " minutes.";
        try {
            if (channel == RecoveryChannel.EMAIL && emailEnabled && mailSender.getIfAvailable() != null) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(user.getEmail());
                message.setSubject("Your E-biessa recovery code");
                message.setText(text);
                mailSender.getObject().send(message);
            } else if (channel == RecoveryChannel.PHONE && smsEnabled) {
                String body = "To=" + encode(user.getPhone()) + "&From=" + encode(twilioFrom) + "&Body=" + encode(text);
                RestClient.create().post()
                        .uri("https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json", twilioSid)
                        .headers(headers -> headers.setBasicAuth(twilioSid, twilioToken))
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body).retrieve().toBodilessEntity();
            } else {
                log.info("Development recovery code for user {} via {}: {}", user.getId(), channel, code);
            }
        } catch (RuntimeException deliveryFailure) {
            log.error("Could not deliver recovery code via {}", channel, deliveryFailure);
        }
    }

    private RecoveryChannel channel(String identifier) { return identifier.contains("@") ? RecoveryChannel.EMAIL : RecoveryChannel.PHONE; }
    private String phone(String value) { return value == null ? "" : value.replaceAll("\\D", ""); }
    private String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
}
