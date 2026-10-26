package com.bbms.service;

import com.bbms.domain.User;
import com.bbms.repo.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** One-time, explicit Admin creation for the isolated training database only. */
@Component
@Profile("training")
public class TrainingAdminBootstrapRunner implements ApplicationRunner {
    private final Environment env;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public TrainingAdminBootstrapRunner(Environment env, UserRepository users, PasswordEncoder encoder) {
        this.env = env; this.users = users; this.encoder = encoder;
    }

    @Override public void run(ApplicationArguments args) {
        if (!Boolean.parseBoolean(env.getProperty("bbms.training.provision-admin", "false"))) return;
        String email = required("TRAINING_ADMIN_EMAIL");
        String password = required("TRAINING_ADMIN_PASSWORD");
        if (!email.endsWith("@example.test") || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Training Admin credentials must use example.test and a 12–72 UTF-8-byte password.");
        }
        var existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            User account = existing.get();
            if (!"ADMIN".equals(account.getRole()) || !account.isEnabled() || !account.isDemoData())
                throw new IllegalStateException("The requested training Admin email is already used by a non-training or non-Admin account.");
            System.out.println("Training Admin already exists; no account fields were changed.");
            return;
        }
        User admin = new User("TRAINING — BBMS Administrator", email, encoder.encode(password), "ADMIN",
                "TRAINING — Synthetic demonstration", "");
        admin.setDemoData(true);
        admin.setDemoKey("TRAINING-ADMIN-" + UUID.randomUUID());
        users.save(admin);
        System.out.println("Training Admin provisioned in the isolated training database; credential values were not displayed.");
    }

    private String required(String name) {
        String value = env.getProperty(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for explicit training Admin provisioning.");
        return value.trim();
    }
}
