package com.bbms.service;

import com.bbms.domain.BloodUnit;
import com.bbms.domain.DonationAppointment;
import com.bbms.domain.DonationRecord;
import com.bbms.domain.User;
import com.bbms.repo.BloodUnitRepository;
import com.bbms.repo.DonationRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrainingDonationWorkflowServiceTest {
    private DonationRecordRepository donations;
    private BloodUnitRepository units;
    private InventoryReceiptService receipts;
    private TrainingDonationWorkflowService service;
    private DonationRecord donation;

    @BeforeEach void setUp() {
        donations=mock(DonationRecordRepository.class); units=mock(BloodUnitRepository.class); receipts=mock(InventoryReceiptService.class);
        Clock clock=Clock.fixed(LocalDate.of(2026,10,26).atStartOfDay(ZoneId.systemDefault()).toInstant(),ZoneId.systemDefault());
        service=new TrainingDonationWorkflowService(donations,units,receipts,clock);
        User donor=new User("TRAINING — Donor","donor@training.example.test","hash","DONOR","","","A+"); donor.setDemoData(true);
        DonationAppointment appointment=new DonationAppointment(donor,LocalDate.of(2026,10,25)); appointment.setDemoData(true); appointment.setStatus("COMPLETED");
        donation=new DonationRecord(donor,appointment,"Whole Blood",1,LocalDate.of(2026,10,25),null); donation.setDemoData(true);
    }

    @Test void simulationRequiresACompletedSyntheticVisitAndNeverActsAsRealScreening() {
        when(donations.findById(4L)).thenReturn(Optional.of(donation));
        when(donations.save(any(DonationRecord.class))).thenAnswer(inv->inv.getArgument(0));
        DonationRecord saved=service.simulateScreeningPass(4L);
        assertSame(donation,saved);
        assertEquals("TRAINING_SIMULATED_PASS",saved.getTestingStatus());
        verify(donations).save(donation);
    }

    @Test void inventoryReceiptRequiresExplicitSimulatedPassAndIsLinkedOnlyOnce() {
        when(donations.findById(4L)).thenReturn(Optional.of(donation));
        when(donations.save(any(DonationRecord.class))).thenAnswer(inv->inv.getArgument(0));
        assertThrows(IllegalArgumentException.class,()->service.receiveAfterSimulatedPass(4L,"TRAINING-DON-4","admin"));
        service.simulateScreeningPass(4L);
        when(units.findByBatchId("TRAINING-DON-4")).thenReturn(Optional.empty());
        when(receipts.receive(any(BloodUnit.class),any())).thenAnswer(inv->{BloodUnit unit=inv.getArgument(0);return unit;});
        BloodUnit unit=service.receiveAfterSimulatedPass(4L,"TRAINING-DON-4","admin");
        assertEquals("A+",unit.getBloodGroup()); assertEquals(1,unit.getQuantity()); assertTrue(unit.isDemoData());
        assertEquals("TRAINING-DON-4",donation.getInventoryBatchId());
        verify(receipts).receive(any(BloodUnit.class),eq("admin"));
        assertThrows(IllegalArgumentException.class,()->service.receiveAfterSimulatedPass(4L,"TRAINING-DON-4","admin"));
    }

    @Test void nonSyntheticOrUncompletedDonationsCannotEnterTheTrainingReceiptPath() {
        donation.setDemoData(false);
        when(donations.findById(4L)).thenReturn(Optional.of(donation));
        assertThrows(IllegalArgumentException.class,()->service.simulateScreeningPass(4L));
        verify(donations,never()).save(any()); verify(receipts,never()).receive(any(),any());
    }
}
