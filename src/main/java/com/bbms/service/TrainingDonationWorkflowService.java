package com.bbms.service;

import com.bbms.domain.BloodUnit;
import com.bbms.domain.DonationRecord;
import com.bbms.repo.BloodUnitRepository;
import com.bbms.repo.DonationRecordRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/** Explicitly simulated screening and receipt, available only in the isolated training profile. */
@Service
@Profile("training")
public class TrainingDonationWorkflowService {
    private final DonationRecordRepository donations;
    private final BloodUnitRepository units;
    private final InventoryReceiptService receipts;
    private final Clock clock;

    public TrainingDonationWorkflowService(DonationRecordRepository donations, BloodUnitRepository units,
                                           InventoryReceiptService receipts, Clock clock) {
        this.donations = donations;
        this.units = units;
        this.receipts = receipts;
        this.clock = clock;
    }

    @Transactional
    public DonationRecord simulateScreeningPass(long donationId) {
        DonationRecord donation = donations.findById(donationId)
                .orElseThrow(() -> new IllegalArgumentException("Training donation not found."));
        if (!donation.isDemoData() || donation.getAppointment() == null
                || !java.util.Objects.equals(donation.getDonor().getId(), donation.getAppointment().getDonor().getId())
                || !"COMPLETED".equals(donation.getAppointment().getStatus())
                || donation.getDonationDate().isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("Only a completed synthetic training visit can use simulated screening.");
        }
        if (!"COLLECTED".equals(donation.getTestingStatus()) && !"TRAINING_SIMULATED_PASS".equals(donation.getTestingStatus())) {
            throw new IllegalArgumentException("Only a collected training donation can be screened.");
        }
        donation.setTestingStatus("TRAINING_SIMULATED_PASS");
        return donations.save(donation);
    }

    @Transactional
    public BloodUnit receiveAfterSimulatedPass(long donationId, String batchId, String actor) {
        DonationRecord donation = donations.findById(donationId)
                .orElseThrow(() -> new IllegalArgumentException("Training donation not found."));
        if (!donation.isDemoData() || !"TRAINING_SIMULATED_PASS".equals(donation.getTestingStatus())) {
            throw new IllegalArgumentException("A training-only simulated pass is required before inventory receipt.");
        }
        if (donation.getInventoryBatchId() != null && !donation.getInventoryBatchId().isBlank()) {
            throw new IllegalArgumentException("This training donation has already been received into inventory.");
        }
        if (batchId == null || !batchId.matches("TRAINING-DON-[A-Za-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("Use a unique TRAINING-DON- batch identifier.");
        }
        if (units.findByBatchId(batchId).isPresent()) throw new IllegalArgumentException("That training batch identifier already exists.");

        LocalDate collected = donation.getDonationDate();
        LocalDate expiry = collected.plusDays("Platelets".equals(donation.getComponent()) ? 5 : 365);
        if (!expiry.isAfter(LocalDate.now(clock))) throw new IllegalArgumentException("The simulated product is expired and cannot be received.");
        BloodUnit unit = new BloodUnit();
        unit.setBatchId(batchId);
        unit.setBloodGroup(donation.getBloodGroup());
        unit.setComponent(donation.getComponent());
        unit.setCollectionDate(collected);
        unit.setExpiryDate(expiry);
        unit.setStatus("RELEASED");
        unit.setQuantity(donation.getUnits());
        unit.setDemoData(true);
        unit.setDemoKey("TRAINING-DONATION-" + donationId);
        receipts.receive(unit, actor);
        donation.setInventoryBatchId(batchId);
        donations.save(donation);
        return unit;
    }
}
