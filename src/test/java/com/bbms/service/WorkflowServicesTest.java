package com.bbms.service;

import com.bbms.domain.*;
import com.bbms.repo.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowServicesTest {
    @Mock BloodRequestRepository requests;
    @Mock BloodRequestEventRepository requestEvents;
    @Mock BloodUnitRepository units;
    @InjectMocks BloodRequestWorkflowService workflow;

    @Test void requestApprovalRequiresReasonAndEnoughMatchingEligibleStock(){
        User hospital=new User("Clinic","clinic@example.test","hash","HOSPITAL","Clinic","");
        BloodRequest request=new BloodRequest(hospital,"A+","Platelets",2,"ROUTINE","REF-1","");
        when(requests.findByIdForUpdate(1L)).thenReturn(Optional.of(request));
        assertThrows(IllegalArgumentException.class,()->workflow.review(1L,"APPROVED"," ","admin@example.test"));
        when(units.stockBySource("A+","Platelets",LocalDate.now(),false)).thenReturn(1L);
        IllegalArgumentException ex=assertThrows(IllegalArgumentException.class,()->workflow.review(1L,"APPROVED","Reviewed","admin@example.test"));
        assertTrue(ex.getMessage().contains("1 units available"));
        assertEquals("PENDING",request.getStatus());
        verify(requestEvents,never()).save(any());
    }

    @Test void approvalRecordsReasonAndStatusEvent(){
        User hospital=new User("Clinic","clinic@example.test","hash","HOSPITAL","Clinic","");
        BloodRequest request=new BloodRequest(hospital,"A+","Platelets",1,"ROUTINE","REF-2","");
        when(requests.findByIdForUpdate(2L)).thenReturn(Optional.of(request));
        when(units.stockBySource("A+","Platelets",LocalDate.now(),false)).thenReturn(1L);
        workflow.review(2L,"APPROVED","Matching released stock is available.","admin@example.test");
        assertEquals("APPROVED",request.getStatus());
        verify(requestEvents).save(argThat(event->event.getStatus().equals("APPROVED")&&event.getReason().contains("Matching released")));
    }


    @Test void archivedPlaceholderRequestCannotBeReviewed(){
        User hospital=new User("Clinic","clinic@example.test","hash","HOSPITAL","Clinic","");
        BloodRequest request=new BloodRequest(hospital,"A+","Platelets",1,"ROUTINE","ARCHIVE-1","");request.setDemoData(true);
        when(requests.findByIdForUpdate(9L)).thenReturn(Optional.of(request));
        assertThrows(IllegalArgumentException.class,()->workflow.review(9L,"APPROVED","Reviewed","admin@example.test"));
        verify(units,never()).stockBySource(anyString(),anyString(),any(),anyBoolean());verify(requestEvents,never()).save(any());
    }

    @Test void appointmentCannotBeCompletedBeforeARecordedDonation(){
        User donor=new User("Donor","donor@example.test","hash","DONOR","","","A+");
        DonationAppointment appointment=new DonationAppointment(donor,LocalDate.now().plusDays(1));
        appointment.setStatus("ATTENDED");
        DonationAppointmentRepository appointmentRepo=mock(DonationAppointmentRepository.class);
        DonationAppointmentEventRepository eventRepo=mock(DonationAppointmentEventRepository.class);
        DonationReminderService reminderService=mock(DonationReminderService.class);
        DonationRecordRepository donationRepo=mock(DonationRecordRepository.class);
        UserRepository userRepo=mock(UserRepository.class);
        when(appointmentRepo.findByIdForUpdate(3L)).thenReturn(Optional.of(appointment));
        when(donationRepo.existsByAppointmentAndDonorAndDemoData(appointment,donor,false)).thenReturn(false);
        DonationAppointmentService service=new DonationAppointmentService(appointmentRepo,eventRepo,reminderService,donationRepo,userRepo);
        assertThrows(IllegalArgumentException.class,()->service.updateStatus(3L,"COMPLETED","Done","admin@example.test"));
        assertEquals("ATTENDED",appointment.getStatus());
        verify(eventRepo,never()).save(any());
    }

    @Test void reminderSynchronizationReusesExistingPreferenceRows(){
        User donor=new User("Donor","donor@example.test","hash","DONOR","","","O+");
        DonationAppointment appointment=new DonationAppointment(donor,LocalDate.now().plusDays(3));
        appointment.setSchedule(appointment.getRequestedDate(),LocalTime.of(10,0),"Demo center");
        appointment.setReminderPreferences(true,true,false);
        DonationReminderRepository repo=mock(DonationReminderRepository.class);
        DonationReminder threeDay=new DonationReminder(appointment,3,LocalDateTime.now());
        DonationReminder oneDay=new DonationReminder(appointment,1,LocalDateTime.now());
        when(repo.findByAppointmentAndDaysBefore(appointment,3)).thenReturn(Optional.of(threeDay));
        when(repo.findByAppointmentAndDaysBefore(appointment,1)).thenReturn(Optional.of(oneDay));
        when(repo.findByAppointmentAndDaysBefore(appointment,0)).thenReturn(Optional.empty());
        new DonationReminderService(repo).synchronize(appointment);
        verify(repo,never()).save(any());
        verify(repo).findByAppointmentAndDaysBefore(appointment,3);
        verify(repo).findByAppointmentAndDaysBefore(appointment,1);
    }
}
