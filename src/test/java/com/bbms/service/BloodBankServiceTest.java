package com.bbms.service;

import com.bbms.domain.*;
import com.bbms.repo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BloodBankServiceTest {
 @Mock BloodUnitRepository units; @Mock BloodRequestRepository requests; @Mock BloodIssueRepository issues;
 @Mock InventoryMovementRepository movements; @Mock BloodRequestEventRepository events;
 @InjectMocks BloodBankService service;
 private BloodRequest approvedRequest(int qty){BloodRequest r=new BloodRequest(new User("Hospital","h@example.test","hash","HOSPITAL","Clinic",""),"O+","Whole Blood",qty,"URGENT","REF-1","");r.setStatus("APPROVED");return r;}
 @Test void refusesInsufficientStockWithoutCreatingIssue(){BloodRequest r=approvedRequest(3);when(requests.findByIdForUpdate(7L)).thenReturn(Optional.of(r));when(units.availableForIssue(eq("O+"),eq("Whole Blood"),any(LocalDate.class),anyBoolean())).thenReturn(List.of());IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->service.issue(7L));assertTrue(ex.getMessage().contains("Insufficient"));verify(issues,never()).save(any());assertEquals("APPROVED",r.getStatus());}
 @Test void consumesEarliestAvailableBatchesAndRecordsIssue(){BloodRequest r=approvedRequest(3);when(requests.findByIdForUpdate(7L)).thenReturn(Optional.of(r));BloodUnit first=new BloodUnit();first.setBatchId("EARLY");first.setQuantity(2);BloodUnit second=new BloodUnit();second.setBatchId("LATE");second.setQuantity(2);when(units.availableForIssue(eq("O+"),eq("Whole Blood"),any(LocalDate.class),anyBoolean())).thenReturn(List.of(first,second));service.issue(7L);assertEquals(0,first.getQuantity());assertEquals(1,second.getQuantity());assertEquals("ISSUED",r.getStatus());verify(issues).save(argThat(issue->issue.getQuantity()==3&&issue.getBatchId().contains("EARLY")&&issue.getBatchId().contains("LATE")));}
 @Test void rejectsIssuingTheSameRequestTwice(){BloodRequest r=approvedRequest(1);when(requests.findByIdForUpdate(7L)).thenReturn(Optional.of(r));BloodUnit unit=new BloodUnit();unit.setBatchId("BATCH");unit.setQuantity(1);when(units.availableForIssue(eq("O+"),eq("Whole Blood"),any(LocalDate.class),anyBoolean())).thenReturn(List.of(unit));service.issue(7L);assertThrows(IllegalArgumentException.class,()->service.issue(7L));verify(issues,times(1)).save(any());}

 @Test void archivedPlaceholderRequestCannotBeIssued(){BloodRequest r=approvedRequest(1);r.setDemoData(true);when(requests.findByIdForUpdate(11L)).thenReturn(Optional.of(r));assertThrows(IllegalArgumentException.class,()->service.issue(11L));verify(units,never()).availableForIssue(anyString(),anyString(),any(LocalDate.class),anyBoolean());verify(issues,never()).save(any());}
 @Test void legacyEmergencyRowsDisplayAsUrgentWithoutChangingStoredData(){assertEquals("Urgent",BloodRequest.priorityLabel("EMERGENCY"));assertEquals("Urgent",BloodRequest.priorityLabel("URGENT"));assertEquals("Routine",BloodRequest.priorityLabel("ROUTINE"));}
}
