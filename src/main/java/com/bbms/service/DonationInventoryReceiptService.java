package com.bbms.service;

import com.bbms.domain.BloodUnit;
import com.bbms.domain.DonationRecord;
import com.bbms.repo.BloodUnitRepository;
import com.bbms.repo.DonationRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Explicitly receives a released operational donation into stock, linking both records atomically. */
@Service
public class DonationInventoryReceiptService {
    private final DonationRecordRepository donations;
    private final BloodUnitRepository units;
    private final InventoryReceiptService receipts;
    private final Clock clock;

    public DonationInventoryReceiptService(DonationRecordRepository donations, BloodUnitRepository units,
                                           InventoryReceiptService receipts, Clock clock) {
        this.donations = donations;
        this.units = units;
        this.receipts = receipts;
        this.clock = clock;
    }

    @Transactional
    public BloodUnit receiveReleasedDonation(long donationId, String batchId, LocalDate expiryDate,
                                            String releaseDecisionReference, String physicalReceiptReference, String actor) {
        DonationRecord donation = donations.findByIdForUpdate(donationId)
                .orElseThrow(() -> new IllegalArgumentException("Donation record not found."));
        if (donation.isDemoData()) {
            throw new IllegalArgumentException("Synthetic donations cannot enter operational inventory.");
        }
        if (!"TESTING".equals(donation.getTestingStatus())) {
            throw new IllegalArgumentException("Only a donation in testing can be released and received through this workflow.");
        }
        if (donation.getInventoryBatchId() != null && !donation.getInventoryBatchId().isBlank()) {
            throw new IllegalArgumentException("This donation is already linked to an inventory batch.");
        }

        String normalizedBatchId = batchId == null ? "" : batchId.trim();
        LocalDate today = LocalDate.now(clock);
        if (normalizedBatchId.isBlank() || normalizedBatchId.length() > 80
                || !BloodBankService.GROUPS.contains(donation.getBloodGroup())
                || !List.of("Whole Blood", "Red Cells", "Platelets", "Plasma").contains(donation.getComponent())
                || donation.getUnits() < 1 || donation.getUnits() > 10
                || donation.getDonationDate() == null || donation.getDonationDate().isAfter(today)
                || expiryDate == null || !expiryDate.isAfter(donation.getDonationDate()) || expiryDate.isBefore(today)) {
            throw new IllegalArgumentException("Check batch ID, donation details, and a valid unexpired batch expiry date.");
        }
        if (units.findByBatchId(normalizedBatchId).isPresent() || donations.existsByInventoryBatchId(normalizedBatchId)) {
            throw new IllegalArgumentException("That batch identifier is already linked to a stock or donation record.");
        }

        BloodUnit unit = new BloodUnit();
        unit.setBatchId(normalizedBatchId);
        unit.setBloodGroup(donation.getBloodGroup());
        unit.setComponent(donation.getComponent());
        unit.setCollectionDate(donation.getDonationDate());
        unit.setExpiryDate(expiryDate);
        unit.setStatus("RELEASED");
        unit.setQuantity(donation.getUnits());
        unit.setDemoData(false);

        BloodUnit saved = receipts.receiveReleasedDonation(unit, releaseDecisionReference, physicalReceiptReference, actor);
        donation.setInventoryBatchId(saved.getBatchId());
        donation.setTestingStatus("RELEASED");
        donations.save(donation);
        return saved;
    }
}
