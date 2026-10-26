package com.bbms.service;

import com.bbms.domain.BloodUnit;
import com.bbms.domain.InventoryMovement;
import com.bbms.repo.BloodUnitRepository;
import com.bbms.repo.InventoryMovementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/** Records authorized, evidenced transitions for operational inventory batches. */
@Service
public class InventoryBatchStatusService {
    private final BloodUnitRepository units;
    private final InventoryMovementRepository movements;
    private final Clock clock;
    @Value("${bbms.training.mode:false}") private boolean trainingMode;

    public InventoryBatchStatusService(BloodUnitRepository units, InventoryMovementRepository movements, Clock clock) {
        this.units = units;
        this.movements = movements;
        this.clock = clock;
    }

    @Transactional
    public BloodUnit beginTesting(long batchId, String testingReference, String actor) {
        if (trainingMode) throw new IllegalStateException("Operational batch testing is unavailable in training mode.");
        BloodUnit unit = lockOperationalBatch(batchId);
        if (!"QUARANTINED".equals(unit.getStatus())) {
            throw new IllegalArgumentException("Only a quarantined batch can enter testing.");
        }
        String reference = requireReference(testingReference, "testing authorization");
        unit.setStatus("TESTING");
        BloodUnit saved = units.save(unit);
        movements.save(new InventoryMovement(saved, "TESTING", 0, saved.getQuantity(),
                "Testing authorization: " + reference, actor));
        return saved;
    }

    @Transactional
    public BloodUnit release(long batchId, String releaseDecisionReference, String actor) {
        if (trainingMode) throw new IllegalStateException("Operational batch release is unavailable in training mode.");
        BloodUnit unit = lockOperationalBatch(batchId);
        if (!"TESTING".equals(unit.getStatus())) {
            throw new IllegalArgumentException("Only a batch in testing can be released.");
        }
        if (unit.getQuantity() < 1 || unit.getExpiryDate() == null || unit.getExpiryDate().isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("An expired or empty batch cannot be released.");
        }
        String reference = requireReference(releaseDecisionReference, "release decision");

        unit.setStatus("RELEASED");
        BloodUnit saved = units.save(unit);
        movements.save(new InventoryMovement(saved, "RELEASED", 0, saved.getQuantity(),
                "Release decision: " + reference, actor));
        return saved;
    }

    private BloodUnit lockOperationalBatch(long batchId) {
        BloodUnit unit = units.findByIdForUpdate(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory batch not found."));
        if (unit.isDemoData()) throw new IllegalArgumentException("Synthetic inventory cannot be changed in operational mode.");
        return unit;
    }

    private String requireReference(String reference, String label) {
        if (reference == null || reference.isBlank() || reference.trim().length() > 80) {
            throw new IllegalArgumentException("Enter a verified " + label + " reference (up to 80 characters).");
        }
        return reference.trim();
    }
}
