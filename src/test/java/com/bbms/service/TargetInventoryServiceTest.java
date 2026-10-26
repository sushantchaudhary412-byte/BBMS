package com.bbms.service;

import com.bbms.domain.BloodUnit;
import com.bbms.repo.BloodUnitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class TargetInventoryServiceTest {
    private BloodUnitRepository units;
    private InventoryReceiptService receipts;
    private JdbcTemplate jdbc;
    private TargetInventoryService service;
    private final Map<String, BloodUnit> samples = new HashMap<>();

    @BeforeEach
    void setUp() {
        units = mock(BloodUnitRepository.class);
        receipts = mock(InventoryReceiptService.class);
        jdbc = mock(JdbcTemplate.class);
        service = new TargetInventoryService(units, receipts, jdbc,
                Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneOffset.UTC),
                new ClassPathResource("data/inventory-targets.csv"));
        ReflectionTestUtils.setField(service, "initializerEnabled", true);
        ReflectionTestUtils.setField(service, "serverPort", "8080");
        ReflectionTestUtils.setField(service, "trainingMode", false);
        ReflectionTestUtils.setField(service, "presentationDatabase", "");
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("bbms_local");
        when(units.stock(anyString(), anyString(), any())).thenAnswer(call ->
                "A+".equals(call.getArgument(0)) && "Whole Blood".equals(call.getArgument(1)) ? 3L : 0L);
        when(units.findByDemoKey(anyString())).thenAnswer(call -> Optional.ofNullable(samples.get(call.getArgument(0))));
        when(units.findByBatchId(anyString())).thenReturn(Optional.empty());
        doAnswer(call -> {
            BloodUnit sample = call.getArgument(0);
            samples.put(sample.getDemoKey(), sample);
            return sample;
        }).when(receipts).receiveUnverifiedTargetSample(any(BloodUnit.class), anyString());
    }

    @Test
    void manifestDefinesExactlyFortyTargetUnitsAcrossAllEightBloodGroups() {
        Map<String, Integer> breakdown = service.targetPlan().stream().collect(
                java.util.stream.Collectors.toMap(TargetInventoryService.Target::bloodGroup,
                        TargetInventoryService.Target::targetUnits));
        assertEquals(Map.of("A+", 8, "A-", 5, "B+", 7, "B-", 2,
                "AB+", 6, "AB-", 1, "O+", 6, "O-", 5), breakdown);
        assertEquals(40, breakdown.values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void recordsOnlyTheMissingTargetAsNamespacedQuarantinedSamplesAndIsIdempotent() {
        assertEquals(8, service.initializeTargetSamples("inventory-admin@example.test"));
        assertEquals(8, samples.size());
        assertEquals(37, samples.values().stream().mapToInt(BloodUnit::getQuantity).sum());
        assertEquals(5, samples.get("BBMS-TARGET-SAMPLE-APOS").getQuantity());
        assertTrue(samples.values().stream().allMatch(sample -> sample.isDemoData()
                && sample.getDemoKey().startsWith(TargetInventoryService.SAMPLE_PREFIX)
                && "QUARANTINED".equals(sample.getStatus())));
        assertEquals(0, samples.values().stream().filter(sample -> "RELEASED".equals(sample.getStatus())).count());

        assertEquals(0, service.initializeTargetSamples("inventory-admin@example.test"));
        verify(receipts, times(8)).receiveUnverifiedTargetSample(any(BloodUnit.class), anyString());
        verify(units, never()).save(any(BloodUnit.class));
    }

    @Test
    void refusesAnyDatabaseOtherThanOperationalWithoutWriting() {
        when(jdbc.queryForObject("SELECT DATABASE()", String.class)).thenReturn("bbms_training");
        assertThrows(IllegalStateException.class, () -> service.initializeTargetSamples("inventory-admin@example.test"));
        verifyNoInteractions(receipts);
        verify(units, never()).findByDemoKey(anyString());
    }

    @Test
    void refusesWhenInitializerIsDisabledWithoutCheckingOrWritingDatabase() {
        ReflectionTestUtils.setField(service, "initializerEnabled", false);
        assertThrows(IllegalStateException.class, () -> service.initializeTargetSamples("inventory-admin@example.test"));
        verifyNoInteractions(jdbc, receipts);
    }

    @Test
    void preservesMismatchedExistingSampleAndFailsClosed() {
        BloodUnit existing = new BloodUnit();
        existing.setBatchId("BBMS-TARGET-SAMPLE-APOS"); existing.setDemoKey(existing.getBatchId());
        existing.setDemoData(true); existing.setBloodGroup("A+"); existing.setComponent("Whole Blood");
        existing.setStatus("RELEASED"); existing.setQuantity(5);
        samples.put(existing.getDemoKey(), existing);

        assertThrows(IllegalStateException.class, () -> service.initializeTargetSamples("inventory-admin@example.test"));
        verify(receipts, never()).receiveUnverifiedTargetSample(any(BloodUnit.class), anyString());
        assertEquals("RELEASED", existing.getStatus());
    }
}
