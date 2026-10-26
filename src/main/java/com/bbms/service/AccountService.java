package com.bbms.service;

import com.bbms.domain.User;
import com.bbms.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
public class AccountService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    @Value("${bbms.training.mode:false}") private boolean trainingMode;

    public AccountService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Transactional
    public User register(String name, String email, String password, String role, String organization, String phone, String bloodGroup) {
        if (!role.equals("DONOR") && !role.equals("HOSPITAL")) throw new IllegalArgumentException("Choose Donor or Hospital / Requester.");
        if (role.equals("HOSPITAL") && (organization == null || organization.isBlank())) throw new IllegalArgumentException("Enter your hospital or organization name.");
        if (role.equals("DONOR") && !BloodBankService.GROUPS.contains(bloodGroup)) throw new IllegalArgumentException("Choose your blood group.");
        if (trainingMode && (email == null || !email.trim().toLowerCase(Locale.ROOT).endsWith("@training.example.test")))
            throw new IllegalArgumentException("Training accounts must use the reserved training.example.test domain.");
        if (role.equals("HOSPITAL")) bloodGroup = null;
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw new IllegalArgumentException("Password must be no longer than 72 UTF-8 bytes.");
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.findByEmailIgnoreCase(normalized).isPresent()) throw new IllegalArgumentException("An account with this email already exists.");
        String safeName = name.trim();
        String safeOrganization = organization == null ? null : organization.trim();
        if (trainingMode) {
            if (!safeName.startsWith("TRAINING — ")) safeName = "TRAINING — " + safeName;
            if (role.equals("HOSPITAL") && safeOrganization != null && !safeOrganization.startsWith("TRAINING — ")) safeOrganization = "TRAINING — " + safeOrganization;
        }
        User user = new User(safeName, normalized, encoder.encode(password), role,
                safeOrganization, trainingMode ? "" : (phone == null ? null : phone.trim()), bloodGroup);
        if (trainingMode) {
            user.setDemoData(true);
            user.setDemoKey("TRAINING-USER-" + UUID.randomUUID());
        }
        return users.save(user);
    }
}
