package com.bbms.service;

import com.bbms.domain.BloodUnit;
import com.bbms.domain.InventoryMovement;
import com.bbms.repo.BloodUnitRepository;
import com.bbms.repo.InventoryMovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryBatchStatusServiceTest {
    @Mock BloodUnitRepository units;
    @Mock InventoryMovementRepository movements;
    InventoryBatchStatusService service;
    BloodUnit batch;

    @BeforeEach void setUp() {
        Clock clock = Clock.fixed(LocalDate.of(2026, 10, 26).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant(), ZoneId.of("Asia/Kolkata"));
        service = new InventoryBatchStatusService(units, movements, clock);
        ReflectionTestUtils.setField(service, "trainingMode", false);
        batch = new BloodUnit(); batch.setBatchId("TESTING-BATCH"); batch.setBloodGroup("A+");
        batch.setComponent("Whole Blood"); batch.setCollectionDate(LocalDate.of(2026, 10, 20));
        batch.setExpiryDate(LocalDate.of(2026, 11, 20)); batch.setStatus("TESTING"); batch.setQuantity(3);
        when(units.findByIdForUpdate(10L)).thenReturn(Optional.of(batch));
    }

    @Test void releaseRequiresTestingStatusAndRecordsZeroQuantityAuditMovement() {
        when(units.save(batch)).thenReturn(batch);
        assertSame(batch, service.release(10L, "LAB-DECISION-10", "admin@example.test"));
        assertEquals("RELEASED", batch.getStatus());
        verify(units).findByIdForUpdate(10L);
        verify(movements).save(argThat(m -> m.getMovementType().equals("RELEASED")
                && m.getQuantityChange() == 0 && m.getQuantityAfter() == 3
                && m.getReference().contains("LAB-DECISION-10")));
    }

    @Test void quarantineMustHaveDocumentedClearanceBeforeTesting() {
        batch.setStatus("QUARANTINED");
        when(units.save(batch)).thenReturn(batch);
        service.beginTesting(10L, "QUARANTINE-REVIEW-10", "admin@example.test");
        assertEquals("TESTING", batch.getStatus());
        verify(movements).save(argThat(m -> m.getMovementType().equals("TESTING")
                && m.getQuantityChange() == 0 && m.getQuantityAfter() == 3
                && m.getReference().contains("QUARANTINE-REVIEW-10")));
    }

    @Test void expiredEmptyDemoOrUnreferencedBatchCannotBeReleased() {
        batch.setExpiryDate(LocalDate.of(2026, 10, 25));
        assertThrows(IllegalArgumentException.class, () -> service.release(10L, "LAB-DECISION-10", "admin@example.test"));
        batch.setExpiryDate(LocalDate.of(2026, 11, 20)); batch.setQuantity(0);
        assertThrows(IllegalArgumentException.class, () -> service.release(10L, "LAB-DECISION-10", "admin@example.test"));
        batch.setQuantity(3); batch.setDemoData(true);
        assertThrows(IllegalArgumentException.class, () -> service.release(10L, "LAB-DECISION-10", "admin@example.test"));
        batch.setDemoData(false);
        assertThrows(IllegalArgumentException.class, () -> service.release(10L, " ", "admin@example.test"));
        verify(units, never()).save(any());
        verifyNoInteractions(movements);
    }

    @Test void alreadyReleasedOrQuarantinedBatchCannotUseTestingReleaseTransition() {
        batch.setStatus("RELEASED");
        assertThrows(IllegalArgumentException.class, () -> service.release(10L, "LAB-DECISION-10", "admin@example.test"));
        batch.setStatus("QUARANTINED");
        assertThrows(IllegalArgumentException.class, () -> service.release(10L, "LAB-DECISION-10", "admin@example.test"));
        verify(units, never()).save(any());
        verifyNoInteractions(movements);
    }
}
