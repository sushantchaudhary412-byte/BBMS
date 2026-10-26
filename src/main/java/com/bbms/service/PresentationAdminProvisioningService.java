package com.bbms.service;

import com.bbms.domain.User;
import com.bbms.repo.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** One-time, explicit Admin provisioning for the isolated presentation database only. */
@Service
@Profile("presentation")
public class PresentationAdminProvisioningService {
    public enum Result { CREATED, ALREADY_EXISTS }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public PresentationAdminProvisioningService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Result provisionIfAbsent(String requestedEmail, String rawPassword) {
        if (requestedEmail == null || requestedEmail.isBlank() || requestedEmail.length() > 254) {
            throw new IllegalArgumentException("Provide a valid presentation Admin email.");
        }
        String email = requestedEmail.trim().toLowerCase(Locale.ROOT);
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("Provide a valid presentation Admin email.");
        }
        if (users.findByEmailIgnoreCase(email).isPresent()) return Result.ALREADY_EXISTS;

        if (rawPassword == null) throw new IllegalArgumentException("A presentation Admin password is required.");
        int passwordBytes = rawPassword.getBytes(StandardCharsets.UTF_8).length;
        if (passwordBytes < 12 || passwordBytes > 72) {
            throw new IllegalArgumentException("Presentation Admin password must be 12–72 UTF-8 bytes.");
        }

        User admin = new User("BBMS Presentation Administrator", email,
                passwordEncoder.encode(rawPassword), "ADMIN", "BBMS Presentation", "");
        users.saveAndFlush(admin);
        return Result.CREATED;
    }
}
