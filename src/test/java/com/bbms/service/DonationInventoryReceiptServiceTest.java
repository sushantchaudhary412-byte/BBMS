package com.bbms.service;

import com.bbms.domain.BloodUnit;
import com.bbms.domain.DonationRecord;
import com.bbms.domain.User;
import com.bbms.repo.BloodUnitRepository;
import com.bbms.repo.DonationRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DonationInventoryReceiptServiceTest {
    @Mock DonationRecordRepository donations;
    @Mock BloodUnitRepository units;
    @Mock InventoryReceiptService receipts;
    DonationInventoryReceiptService service;
    DonationRecord donation;

    @BeforeEach void setUp() {
        Clock clock = Clock.fixed(LocalDate.of(2026, 10, 26).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant(), ZoneId.of("Asia/Kolkata"));
        service = new DonationInventoryReceiptService(donations, units, receipts, clock);
        User donor = new User("Synthetic Unit Donor", "unit.donor@example.test", "unused-test-credential", "DONOR", "", "", "O+");
        donation = new DonationRecord(donor, "Whole Blood", 2, LocalDate.of(2026, 10, 20), null);
        donation.setTestingStatus("TESTING");
        when(donations.findByIdForUpdate(41L)).thenReturn(Optional.of(donation));
    }

    @Test void releasedDonationIsReceivedLinkedAndAudited() {
        when(units.findByBatchId("DON-41-BATCH")).thenReturn(Optional.empty());
        when(donations.existsByInventoryBatchId("DON-41-BATCH")).thenReturn(false);
        when(receipts.receiveReleasedDonation(any(BloodUnit.class), eq("LAB-RELEASE-41"), eq("PHYSICAL-RECEIPT-41"), eq("admin@example.test")))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BloodUnit saved = service.receiveReleasedDonation(41L, "DON-41-BATCH", LocalDate.of(2026, 11, 20), "LAB-RELEASE-41", "PHYSICAL-RECEIPT-41", "admin@example.test");

        assertEquals("O+", saved.getBloodGroup());
        assertEquals("Whole Blood", saved.getComponent());
        assertEquals(2, saved.getQuantity());
        assertEquals("RELEASED", saved.getStatus());
        assertEquals(LocalDate.of(2026, 10, 20), saved.getCollectionDate());
        assertEquals("DON-41-BATCH", donation.getInventoryBatchId());
        assertEquals("RELEASED", donation.getTestingStatus());
        verify(receipts).receiveReleasedDonation(saved, "LAB-RELEASE-41", "PHYSICAL-RECEIPT-41", "admin@example.test");
        verify(donations).save(donation);
    }

    @Test void collectedDonationCannotEnterStock() {
        donation.setTestingStatus("COLLECTED");
        assertThrows(IllegalArgumentException.class, () -> receive());
        verifyNoInteractions(receipts);
        verify(donations, never()).save(any());
    }

    @Test void alreadyLinkedDonationCannotBeReceivedAgain() {
        donation.setInventoryBatchId("EXISTING-BATCH");
        assertThrows(IllegalArgumentException.class, () -> receive());
        verifyNoInteractions(receipts);
    }

    @Test void expiredOrInvalidDateIsRejectedBeforeReceipt() {
        assertThrows(IllegalArgumentException.class, () -> service.receiveReleasedDonation(41L, "DON-41-BATCH", LocalDate.of(2026, 10, 25), "LAB-RELEASE-41", "PHYSICAL-RECEIPT-41", "admin@example.test"));
        verifyNoInteractions(receipts);
    }

    @Test void syntheticDonationCannotEnterOperationalStock() {
        donation.setDemoData(true);
        assertThrows(IllegalArgumentException.class, () -> receive());
        verifyNoInteractions(receipts);
    }

    private BloodUnit receive() {
        return service.receiveReleasedDonation(41L, "DON-41-BATCH", LocalDate.of(2026, 11, 20), "LAB-RELEASE-41", "PHYSICAL-RECEIPT-41", "admin@example.test");
    }
}
