package com.bbms.service;

import com.bbms.domain.BloodUnit;
import com.bbms.domain.InventoryMovement;
import com.bbms.repo.BloodUnitRepository;
import com.bbms.repo.InventoryMovementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class InventoryReceiptService {
    private final BloodUnitRepository units;
    private final InventoryMovementRepository movements;
    private final Clock clock;
    @Value("${bbms.training.mode:false}") private boolean trainingMode;

    public InventoryReceiptService(BloodUnitRepository units, InventoryMovementRepository movements, Clock clock) {
        this.units = units;
        this.movements = movements;
        this.clock = clock;
    }

    @Transactional
    public BloodUnit receive(BloodUnit unit, String actor) {
        if (!trainingMode) {
            throw new IllegalArgumentException("A documented receipt reference is required for operational inventory.");
        }
        return receive(unit, "TRAINING SYNTHETIC RECEIPT — NOT CLINICAL STOCK", actor);
    }

    @Transactional
    public BloodUnit receive(BloodUnit unit, String receiptReference, String actor) {
        if (!trainingMode && unit != null && "RELEASED".equals(unit.getStatus())) {
            throw new IllegalArgumentException("Operational batches must be received in testing or quarantine before release.");
        }
        return persistReceipt(unit, receiptReference, actor, trainingMode, false);
    }

    /** Internal path for explicitly marked planning samples; these can only enter quarantine. */
    @Transactional(propagation = Propagation.MANDATORY)
    public BloodUnit receiveUnverifiedTargetSample(BloodUnit unit, String actor) {
        if (trainingMode || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Unverified target samples require the normal operational inventory transaction.");
        }
        if (unit == null || !unit.isDemoData() || unit.getDemoKey() == null
                || !unit.getDemoKey().startsWith(TargetInventoryService.SAMPLE_PREFIX)
                || !unit.getDemoKey().equals(unit.getBatchId()) || !"QUARANTINED".equals(unit.getStatus())) {
            throw new IllegalArgumentException("Only namespaced, unverified target samples may use this receipt path.");
        }
        return persistReceipt(unit, TargetInventoryService.SAMPLE_LABEL, actor, false, true);
    }

    /** Called only from the transactional donation receipt workflow after its evidence checks. */
    BloodUnit receiveReleasedDonation(BloodUnit unit, String releaseDecisionReference,
                                     String physicalReceiptReference, String actor) {
        if (trainingMode || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("A released donation receipt requires the operational transaction workflow.");
        }
        String releaseRef = normalizeEvidenceReference(releaseDecisionReference, "release decision");
        String receiptRef = normalizeEvidenceReference(physicalReceiptReference, "physical receipt");
        if (releaseRef.equalsIgnoreCase(receiptRef)) {
            throw new IllegalArgumentException("Use distinct release-decision and physical-receipt references.");
        }
        String auditReference = "release=" + releaseRef + "; receipt=" + receiptRef;
        if (auditReference.length() > 100) {
            throw new IllegalArgumentException("The combined release and receipt references must fit within 100 characters.");
        }
        if (unit == null || !"RELEASED".equals(unit.getStatus()) || unit.isDemoData()) {
            throw new IllegalArgumentException("Only a genuine donation approved for release can enter operational stock.");
        }
        return persistReceipt(unit, auditReference, actor, true, false);
    }

    private BloodUnit persistReceipt(BloodUnit unit, String receiptReference, String actor, boolean allowReleased, boolean allowUnverifiedSample) {
        validateReceipt(unit, receiptReference, allowReleased);
        unit.setBatchId(unit.getBatchId().trim());
        if (trainingMode) {
            if (!unit.getBatchId().startsWith("TRAINING-")) {
                throw new IllegalArgumentException("Training inventory batch IDs must begin with TRAINING-.");
            }
            unit.setDemoData(true);
            if (unit.getDemoKey() == null || unit.getDemoKey().isBlank()) {
                unit.setDemoKey("TRAINING-BATCH-" + java.util.UUID.randomUUID());
            }
        } else if (unit.isDemoData() && !(allowUnverifiedSample
                && unit.getDemoKey() != null
                && unit.getDemoKey().startsWith(TargetInventoryService.SAMPLE_PREFIX)
                && "QUARANTINED".equals(unit.getStatus()))) {
            throw new IllegalArgumentException("Synthetic stock cannot be received into operational inventory.");
        }
        if (units.findByBatchId(unit.getBatchId()).isPresent()) {
            throw new IllegalArgumentException("That batch identifier already exists.");
        }
        BloodUnit saved = units.save(unit);
        String reference = trainingMode ? "TRAINING SYNTHETIC RECEIPT — NOT CLINICAL STOCK" : receiptReference.trim();
        String movementType = allowUnverifiedSample ? "SAMPLE_RECORDED" : "RECEIVED";
        movements.save(new InventoryMovement(saved, movementType, saved.getQuantity(), saved.getQuantity(), reference, actor));
        return saved;
    }

    private void validateReceipt(BloodUnit unit, String receiptReference, boolean allowReleased) {
        List<String> acceptedStatuses = allowReleased
                ? List.of("RELEASED")
                : trainingMode ? List.of("RELEASED", "QUARANTINED", "TESTING") : List.of("QUARANTINED", "TESTING");
        if (unit == null || unit.getBatchId() == null || unit.getBatchId().isBlank() || unit.getBatchId().trim().length() > 80
                || !com.bbms.service.BloodBankService.GROUPS.contains(unit.getBloodGroup())
                || !List.of("Whole Blood", "Red Cells", "Platelets", "Plasma").contains(unit.getComponent())
                || unit.getCollectionDate() == null || unit.getExpiryDate() == null
                || unit.getCollectionDate().isAfter(LocalDate.now(clock)) || !unit.getExpiryDate().isAfter(unit.getCollectionDate())
                || !acceptedStatuses.contains(unit.getStatus()) || unit.getQuantity() < 1) {
            throw new IllegalArgumentException("Check batch ID, group, component, positive quantity, status, and collection/expiry dates.");
        }
        if (receiptReference == null || receiptReference.isBlank() || receiptReference.trim().length() > 100) {
            throw new IllegalArgumentException("Enter a documented receipt reference (up to 100 characters).");
        }
    }

    private String normalizeEvidenceReference(String reference, String label) {
        if (reference == null || reference.isBlank() || reference.trim().length() > 40) {
            throw new IllegalArgumentException("Enter a verified " + label + " reference (up to 40 characters).");
        }
        return reference.trim();
    }
}
