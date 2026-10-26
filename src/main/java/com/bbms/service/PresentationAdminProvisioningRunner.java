package com.bbms.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Runs only when an operator explicitly supplies --provision-presentation-admin=true. */
@Component
@Profile("presentation")
public class PresentationAdminProvisioningRunner implements ApplicationRunner {
    private final PresentationAdminProvisioningService provisioner;
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    public PresentationAdminProvisioningRunner(PresentationAdminProvisioningService provisioner,
                                                Environment environment,
                                                ConfigurableApplicationContext context) {
        this.provisioner = provisioner;
        this.environment = environment;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("provision-presentation-admin")) return;
        if (!"true".equalsIgnoreCase(environment.getProperty("provision-presentation-admin"))) {
            throw new IllegalStateException("Use --provision-presentation-admin=true for explicit Admin provisioning.");
        }

        String email = environment.getProperty("BBMS_PRESENTATION_ADMIN_EMAIL");
        String password = environment.getProperty("BBMS_PRESENTATION_ADMIN_PASSWORD");
        PresentationAdminProvisioningService.Result result = provisioner.provisionIfAbsent(email, password);
        if (result == PresentationAdminProvisioningService.Result.CREATED) {
            System.out.println("Presentation Admin provisioned; credential values were not displayed.");
        } else {
            System.out.println("Presentation Admin email already exists; no account fields were changed.");
        }
        context.close();
    }
}
