package com.bbms.service;

import com.bbms.domain.BloodIssue;
import com.bbms.domain.BloodRequest;
import com.bbms.domain.BloodUnit;
import com.bbms.domain.User;
import com.bbms.repo.BloodIssueRepository;
import com.bbms.repo.BloodRequestRepository;
import com.bbms.repo.BloodUnitRepository;
import com.bbms.repo.UserRepository;
import com.bbms.repo.BloodRequestEventRepository;
import com.bbms.repo.InventoryMovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers=com.bbms.config.TestDatabaseSafetyGuard.class)
@Transactional
class BloodIssuePersistenceIntegrationTest {
    @Autowired BloodBankService bank;
    @Autowired UserRepository users;
    @Autowired BloodRequestRepository requests;
    @Autowired BloodUnitRepository units;
    @Autowired BloodIssueRepository issues;
    @Autowired PasswordEncoder encoder;
    @Autowired BloodRequestEventRepository requestEvents;
    @Autowired InventoryMovementRepository movements;

    @Test
    void issueCommitsStockAndIssueRecordToIsolatedTestDatabase() {
        String token = UUID.randomUUID().toString();
        User requester = users.saveAndFlush(new User("Issue persistence test", "issue." + token + "@example.test",
                encoder.encode("test-only-password"), "HOSPITAL", "Issue persistence fixture", ""));
        BloodUnit unit = new BloodUnit();
        unit.setBatchId("ISSUE-TEST-" + token);
        unit.setBloodGroup("O+");
        unit.setComponent("Whole Blood");
        unit.setCollectionDate(LocalDate.now());
        unit.setExpiryDate(LocalDate.now().plusDays(30));
        unit.setStatus("RELEASED");
        unit.setQuantity(1);
        unit = units.saveAndFlush(unit);

        BloodRequest request = new BloodRequest(requester, "O+", "Whole Blood", 1, "URGENT", "ISSUE-TEST-" + token, "");
        request.setStatus("APPROVED");
        request = requests.saveAndFlush(request);

        bank.issue(request.getId());
        assertEquals("ISSUED", requests.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(0, units.findById(unit.getId()).orElseThrow().getQuantity());
        BloodIssue issue = issues.findByRequest(requests.findById(request.getId()).orElseThrow()).orElseThrow();
        assertEquals(1, issue.getQuantity());
        assertTrue(issue.getBatchId().contains(unit.getBatchId()));
    }

    @Test
    void stockEligibilityExcludesExpiredAndUnreleasedUnitsAndOrdersFefo() {
        User requester = users.saveAndFlush(new User("Stock eligibility test", "stock.eligibility@example.test",
                encoder.encode("test-only-password"), "HOSPITAL", "Stock eligibility clinic", ""));
        BloodUnit later = unit("FEFO-LATER", LocalDate.now().minusDays(1), LocalDate.now().plusDays(20), "RELEASED", 2);
        BloodUnit first = unit("FEFO-FIRST", LocalDate.now().minusDays(2), LocalDate.now().plusDays(5), "RELEASED", 1);
        BloodUnit expired = unit("FEFO-EXPIRED", LocalDate.now().minusDays(40), LocalDate.now().minusDays(1), "RELEASED", 7);
        BloodUnit quarantined = unit("FEFO-QUARANTINED", LocalDate.now().minusDays(1), LocalDate.now().plusDays(10), "QUARANTINED", 9);
        BloodUnit testing = unit("FEFO-TESTING", LocalDate.now().minusDays(1), LocalDate.now().plusDays(10), "TESTING", 6);

        assertEquals(3L, units.stock("O+", "Whole Blood", LocalDate.now()));
        assertEquals(java.util.List.of("FEFO-FIRST", "FEFO-LATER"), units.availableForIssue("O+", "Whole Blood", LocalDate.now(), false)
                .stream().map(BloodUnit::getBatchId).toList());
        assertEquals(7, units.findById(expired.getId()).orElseThrow().getQuantity());
        assertEquals(9, units.findById(quarantined.getId()).orElseThrow().getQuantity());
        assertEquals(6, units.findById(testing.getId()).orElseThrow().getQuantity());
        assertNotNull(requester);
    }

    @Test
    void insufficientEligibleStockLeavesRequestAndBatchesUnchanged() {
        User requester = users.saveAndFlush(new User("Insufficient stock test", "insufficient.stock@example.test",
                encoder.encode("test-only-password"), "HOSPITAL", "Insufficient stock clinic", ""));
        BloodUnit released = unit("SHORT-RELEASED", LocalDate.now().minusDays(1), LocalDate.now().plusDays(10), "RELEASED", 2);
        unit("SHORT-EXPIRED", LocalDate.now().minusDays(40), LocalDate.now().minusDays(1), "RELEASED", 8);
        unit("SHORT-TESTING", LocalDate.now().minusDays(1), LocalDate.now().plusDays(10), "TESTING", 8);
        BloodRequest request = new BloodRequest(requester, "O+", "Whole Blood", 3, "ROUTINE", "SHORT-REF", "");
        request.setStatus("APPROVED"); request = requests.saveAndFlush(request);
        Long requestId = request.getId();

        assertThrows(IllegalArgumentException.class, () -> bank.issue(requestId));
        assertEquals("APPROVED", requests.findById(requestId).orElseThrow().getStatus());
        assertEquals(2, units.findById(released.getId()).orElseThrow().getQuantity());
        assertTrue(issues.findByRequest(request).isEmpty());
        assertTrue(movements.findByUnitIdOrderByOccurredAtDesc(released.getId()).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aDatabaseFailureDuringIssueRollsBackStockAndRequestChanges() {
        String token = UUID.randomUUID().toString();
        User requester = users.saveAndFlush(new User("Rollback test", "rollback." + token + "@example.test",
                encoder.encode("test-only-password"), "HOSPITAL", "Rollback test clinic", ""));
        BloodUnit unit = unit("ROLLBACK-" + token, LocalDate.now(), LocalDate.now().plusDays(15), "RELEASED", 2);
        BloodRequest request = new BloodRequest(requester, "O+", "Whole Blood", 1, "ROUTINE", "ROLLBACK-" + token, "");
        request.setStatus("APPROVED"); request = requests.saveAndFlush(request);
        issues.saveAndFlush(new BloodIssue(request, "EXISTING-ISSUE", 1));
        Long requestId = request.getId(); Long unitId = unit.getId();

        assertThrows(DataIntegrityViolationException.class, () -> bank.issue(requestId));
        assertEquals("APPROVED", requests.findById(requestId).orElseThrow().getStatus());
        assertEquals(2, units.findById(unitId).orElseThrow().getQuantity());
        assertEquals(1, issues.findAll().stream().filter(i -> i.getRequest().getId().equals(requestId)).count());
        assertTrue(movements.findByUnitIdOrderByOccurredAtDesc(unitId).isEmpty());

        issues.findByRequest(request).ifPresent(issues::delete);
        requestEvents.findForRequests(java.util.List.of(requestId)).forEach(requestEvents::delete);
        movements.findByUnitIdOrderByOccurredAtDesc(unitId).forEach(movements::delete);
        requests.deleteById(requestId); units.deleteById(unitId); users.deleteById(requester.getId());
    }

    private BloodUnit unit(String batch, LocalDate collection, LocalDate expiry, String status, int quantity) {
        BloodUnit unit = new BloodUnit(); unit.setBatchId(batch); unit.setBloodGroup("O+");
        unit.setComponent("Whole Blood"); unit.setCollectionDate(collection); unit.setExpiryDate(expiry);
        unit.setStatus(status); unit.setQuantity(quantity); return units.saveAndFlush(unit);
    }
}
