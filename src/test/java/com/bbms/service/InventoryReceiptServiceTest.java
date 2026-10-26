package com.bbms.service;

import com.bbms.domain.BloodUnit;
import com.bbms.domain.InventoryMovement;
import com.bbms.repo.BloodUnitRepository;
import com.bbms.repo.InventoryMovementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryReceiptServiceTest {
    @Mock BloodUnitRepository units;
    @Mock InventoryMovementRepository movements;

    private InventoryReceiptService service(boolean training) {
        InventoryReceiptService service = new InventoryReceiptService(units, movements,
                Clock.fixed(LocalDate.of(2026, 10, 26).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant(), ZoneId.of("Asia/Kolkata")));
        ReflectionTestUtils.setField(service, "trainingMode", training);
        return service;
    }

    private BloodUnit testingBatch(String id) {
        BloodUnit unit = new BloodUnit();
        unit.setBatchId(id); unit.setBloodGroup("A+"); unit.setComponent("Whole Blood");
        unit.setCollectionDate(LocalDate.of(2026, 10, 20)); unit.setExpiryDate(LocalDate.of(2026, 11, 20));
        unit.setStatus("TESTING"); unit.setQuantity(4);
        return unit;
    }

    @Test
    void receivingBatchCreatesDocumentedMovementInsideTransactionalService() throws Exception {
        InventoryReceiptService service = service(false);
        BloodUnit unit = testingBatch("UNIT-TEST-BATCH");
        when(units.findByBatchId("UNIT-TEST-BATCH")).thenReturn(Optional.empty());
        when(units.save(unit)).thenReturn(unit);

        assertSame(unit, service.receive(unit, "RECEIPT-TEST-01", "admin@example.test"));

        ArgumentCaptor<InventoryMovement> movement = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movements).save(movement.capture());
        assertEquals("RECEIVED", movement.getValue().getMovementType());
        assertEquals(4, movement.getValue().getQuantityChange());
        assertEquals(4, movement.getValue().getQuantityAfter());
        assertEquals("RECEIPT-TEST-01", movement.getValue().getReference());
        assertTrue(InventoryReceiptService.class.getMethod("receive", BloodUnit.class, String.class, String.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void operationalReceiptRequiresSourceReference() {
        InventoryReceiptService service = service(false);
        assertThrows(IllegalArgumentException.class, () -> service.receive(testingBatch("NO-REF"), "admin@example.test"));
        verifyNoInteractions(units, movements);
    }

    @Test
    void invalidAndDuplicateReceiptsAreRejectedBeforeWriting() {
        InventoryReceiptService service = service(false);
        BloodUnit unit = testingBatch("DUPLICATE-BATCH");
        when(units.findByBatchId("DUPLICATE-BATCH")).thenReturn(Optional.of(testingBatch("DUPLICATE-BATCH")));
        assertThrows(IllegalArgumentException.class, () -> service.receive(unit, "RECEIPT-01", "admin@example.test"));
        unit.setQuantity(0);
        assertThrows(IllegalArgumentException.class, () -> service.receive(unit, "RECEIPT-02", "admin@example.test"));
        verify(units, never()).save(any());
        verifyNoInteractions(movements);
    }

    @Test
    void trainingReceiptKeepsSyntheticProvenance() {
        InventoryReceiptService service = service(true);
        BloodUnit unit = testingBatch("TRAINING-BATCH-01");
        unit.setStatus("RELEASED");
        when(units.findByBatchId(unit.getBatchId())).thenReturn(Optional.empty());
        when(units.save(unit)).thenReturn(unit);
        service.receive(unit, "training-admin@example.test");
        assertTrue(unit.isDemoData());
        verify(movements).save(argThat(m -> m.isDemoData() && m.getReference().contains("NOT CLINICAL STOCK")));
    }

    @Test
    void operationalReceiptCannotDirectlySetReleasedStatus() {
        InventoryReceiptService service = service(false);
        BloodUnit unit = testingBatch("DIRECT-RELEASE-BATCH");
        unit.setStatus("RELEASED");
        assertThrows(IllegalArgumentException.class, () -> service.receive(unit, "RECEIPT-01", "admin@example.test"));
        verifyNoInteractions(units, movements);
    }

    @Test
    void targetSampleReceiptRequiresNamespacedQuarantinedDemoBatchAndRecordsLabeledMovement() {
        InventoryReceiptService service = service(false);
        BloodUnit sample = testingBatch("BBMS-TARGET-SAMPLE-APOS");
        sample.setDemoData(true);
        sample.setDemoKey(sample.getBatchId());
        sample.setStatus("QUARANTINED");
        when(units.findByBatchId(sample.getBatchId())).thenReturn(Optional.empty());
        when(units.save(sample)).thenReturn(sample);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(sample, service.receiveUnverifiedTargetSample(sample, "inventory-admin@example.test"));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        ArgumentCaptor<InventoryMovement> movement = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movements).save(movement.capture());
        assertTrue(movement.getValue().isDemoData());
        assertEquals("SAMPLE_RECORDED", movement.getValue().getMovementType());
        assertTrue(movement.getValue().getReference().contains("UNVERIFIED SAMPLE"));
        assertEquals("QUARANTINED", sample.getStatus());
    }

    @Test
    void targetSampleReceiptRejectsReleasedOrUnmarkedRowsBeforeWriting() {
        InventoryReceiptService service = service(false);
        BloodUnit sample = testingBatch("BBMS-TARGET-SAMPLE-APOS");
        sample.setDemoData(true); sample.setDemoKey(sample.getBatchId()); sample.setStatus("RELEASED");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> service.receiveUnverifiedTargetSample(sample, "inventory-admin@example.test"));
            sample.setStatus("QUARANTINED"); sample.setDemoKey("OTHER-KEY");
            assertThrows(IllegalArgumentException.class,
                    () -> service.receiveUnverifiedTargetSample(sample, "inventory-admin@example.test"));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(units, movements);
    }
}
