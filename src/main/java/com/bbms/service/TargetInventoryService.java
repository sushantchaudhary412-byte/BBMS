package com.bbms.service;

import com.bbms.domain.BloodUnit;
import com.bbms.repo.BloodUnitRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Prepares a namespaced inventory planning dataset through the normal batch receipt path.
 * Every generated batch is unverified, demo-marked, and quarantined; it is never eligible stock.
 */
@Service
public class TargetInventoryService {
    public static final String SAMPLE_PREFIX = "BBMS-TARGET-SAMPLE-";
    public static final String SAMPLE_LABEL = "UNVERIFIED SAMPLE — NOT REAL OR CLINICALLY AVAILABLE";
    private static final String EXPECTED_DATABASE = "bbms_local";

    private final BloodUnitRepository units;
    private final InventoryReceiptService receipts;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Resource targetManifest;

    @Value("${bbms.inventory.target-initializer.enabled:false}")
    private boolean initializerEnabled;
    @Value("${server.port:8080}")
    private String serverPort;
    @Value("${bbms.training.mode:false}")
    private boolean trainingMode;
    @Value("${bbms.presentation.expected-database:}")
    private String presentationDatabase;

    public TargetInventoryService(BloodUnitRepository units, InventoryReceiptService receipts,
                                  JdbcTemplate jdbc, Clock clock,
                                  @Value("classpath:data/inventory-targets.csv") Resource targetManifest) {
        this.units = units;
        this.receipts = receipts;
        this.jdbc = jdbc;
        this.clock = clock;
        this.targetManifest = targetManifest;
    }

    /**
     * Explicit, repeatable initializer. It refuses non-operational profiles and checks the live
     * JDBC schema before its first write. Existing sample rows are only accepted when they exactly
     * match the expected quarantined target; this method never edits or deletes an existing row.
     */
    @Transactional
    public int initializeTargetSamples(String actor) {
        requireInitializationContext();
        requireOperationalDatabaseIdentity();
        List<Target> targets = readAndValidatePlan();
        LocalDate today = LocalDate.now(clock);
        int created = 0;

        for (Target target : targets) {
            long eligibleOperational = units.stock(target.bloodGroup(), target.component(), today);
            int sampleQuantity = (int) Math.max(0, (long) target.targetUnits() - eligibleOperational);
            String key = sampleKey(target.bloodGroup());
            var existing = units.findByDemoKey(key);
            if (existing.isPresent()) {
                requireExpectedExistingSample(existing.get(), target, sampleQuantity);
                continue;
            }
            if (sampleQuantity == 0) {
                continue;
            }

            String batchId = key;
            if (units.findByBatchId(batchId).isPresent()) {
                throw new IllegalStateException("A target sample batch ID is already in use without its sample key; review it manually.");
            }

            BloodUnit sample = new BloodUnit();
            sample.setBatchId(batchId);
            sample.setDemoKey(key);
            sample.setDemoData(true);
            sample.setBloodGroup(target.bloodGroup());
            sample.setComponent(target.component());
            // The entity requires dates. These are sample record dates, not physical collection or
            // clinical expiry evidence, and the batch remains quarantined and unavailable.
            sample.setCollectionDate(today);
            sample.setExpiryDate(today.plusDays(42));
            sample.setStatus("QUARANTINED");
            sample.setQuantity(sampleQuantity);
            receipts.receiveUnverifiedTargetSample(sample, actor);
            created++;
        }
        return created;
    }

    public List<Target> targetPlan() {
        return readAndValidatePlan();
    }

    private void requireInitializationContext() {
        if (!initializerEnabled || !"8080".equals(serverPort) || trainingMode
                || (presentationDatabase != null && !presentationDatabase.isBlank())) {
            throw new IllegalStateException("Target sample initialization is disabled outside the explicitly enabled operational port-8080 context.");
        }
    }

    private void requireOperationalDatabaseIdentity() {
        String database;
        try {
            database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Could not verify the connected database; no sample inventory was written.", ex);
        }
        if (!EXPECTED_DATABASE.equals(database)) {
            throw new IllegalStateException("Target sample initialization requires database bbms_local; no sample inventory was written.");
        }
    }

    private List<Target> readAndValidatePlan() {
        List<Target> targets = new ArrayList<>();
        Set<String> groups = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(targetManifest.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (!"blood_group,target_units,component".equals(header)) {
                throw new IllegalStateException("The inventory target manifest has an unexpected header.");
            }
            String row;
            while ((row = reader.readLine()) != null) {
                if (row.isBlank()) continue;
                String[] cells = row.split(",", -1);
                if (cells.length != 3) throw new IllegalStateException("The inventory target manifest contains an invalid row.");
                String group = cells[0].trim();
                int quantity = Integer.parseInt(cells[1].trim());
                String component = cells[2].trim();
                if (!BloodBankService.GROUPS.contains(group) || !groups.add(group) || quantity < 1
                        || !"Whole Blood".equals(component)) {
                    throw new IllegalStateException("The inventory target manifest contains an unsupported or duplicate group/component.");
                }
                targets.add(new Target(group, quantity, component));
            }
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("Could not load the inventory target manifest.", ex);
        }
        if (!groups.equals(new HashSet<>(BloodBankService.GROUPS))) {
            throw new IllegalStateException("The inventory target manifest must contain every ABO/Rh group exactly once.");
        }
        return List.copyOf(targets);
    }

    private void requireExpectedExistingSample(BloodUnit existing, Target target, int expectedQuantity) {
        if (!existing.isDemoData() || !sampleKey(target.bloodGroup()).equals(existing.getDemoKey())
                || !sampleKey(target.bloodGroup()).equals(existing.getBatchId())
                || !target.bloodGroup().equals(existing.getBloodGroup())
                || !target.component().equals(existing.getComponent())
                || !"QUARANTINED".equals(existing.getStatus())
                || existing.getQuantity() != expectedQuantity) {
            throw new IllegalStateException("An existing target sample differs from the current plan; it was preserved and requires manual review.");
        }
    }

    private static String sampleKey(String group) {
        return SAMPLE_PREFIX + group.toUpperCase(Locale.ROOT).replace("+", "POS").replace("-", "NEG");
    }

    public record Target(String bloodGroup, int targetUnits, String component) { }
}
