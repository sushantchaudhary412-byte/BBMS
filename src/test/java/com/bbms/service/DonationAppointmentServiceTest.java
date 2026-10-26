package com.bbms.service;

import com.bbms.domain.DonationAppointment;
import com.bbms.domain.DonationAppointmentEvent;
import com.bbms.domain.DonationRecord;
import com.bbms.domain.User;
import com.bbms.repo.DonationAppointmentEventRepository;
import com.bbms.repo.DonationAppointmentRepository;
import com.bbms.repo.DonationRecordRepository;
import com.bbms.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class DonationAppointmentServiceTest {
    private DonationAppointmentRepository appointments;
    private DonationAppointmentEventRepository events;
    private DonationReminderService reminders;
    private DonationRecordRepository donations;
    private UserRepository users;
    private DonationAppointmentService service;
    private User donor;
    private DonationAppointment appointment;

    @BeforeEach
    void setUp() {
        appointments = mock(DonationAppointmentRepository.class);
        events = mock(DonationAppointmentEventRepository.class);
        reminders = mock(DonationReminderService.class);
        donations = mock(DonationRecordRepository.class);
        users = mock(UserRepository.class);
        service = new DonationAppointmentService(appointments, events, reminders, donations, users);
        Clock fixedClock=Clock.fixed(LocalDate.now().atTime(10,0).atZone(ZoneId.systemDefault()).toInstant(),ZoneId.systemDefault());
        ReflectionTestUtils.setField(service,"clock",fixedClock);
        donor = mock(User.class);
        when(donor.getId()).thenReturn(7L);
        when(donor.getBloodGroup()).thenReturn("A+");
        when(donor.isDemoData()).thenReturn(false);
        appointment = new DonationAppointment(donor, LocalDate.now());
        ReflectionTestUtils.setField(appointment, "id", 3L);
        appointment.setStatus("ATTENDED");
    }

    @Test
    void recordingAttendedAppointmentLinksRecordAndCompletesStatusWithHistory() {
        when(appointments.findByIdForUpdate(3L)).thenReturn(Optional.of(appointment));
        when(donations.existsByAppointment(appointment)).thenReturn(false);
        when(donations.existsByAppointmentAndDonorAndDemoData(appointment, donor, false)).thenReturn(true);
        when(donations.save(any(DonationRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DonationRecord saved = service.recordDonation(3L, donor, "Whole Blood", 1, LocalDate.now(), null, "admin@example.test");

        assertSame(appointment, saved.getAppointment());
        assertSame(donor, saved.getDonor());
        assertEquals("COMPLETED", appointment.getStatus());
        verify(events).save(argThat(event -> event.getStatus().equals("COMPLETED") && event.getAppointment() == appointment));
        verify(reminders).cancelFor(appointment);
    }

    @Test
    void repeatedOrAlreadyLinkedRecordingDoesNotCreateAnotherDonation() {
        when(appointments.findByIdForUpdate(3L)).thenReturn(Optional.of(appointment));
        when(donations.existsByAppointment(appointment)).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.recordDonation(3L, donor, "Whole Blood", 1, LocalDate.now(), null, "admin@example.test"));

        verify(donations, never()).save(any());
        verify(events, never()).save(any());
    }

    @Test
    void appointmentForDifferentDonorCannotBeLinked() {
        User otherDonor = mock(User.class);
        when(otherDonor.getId()).thenReturn(8L);
        DonationAppointment otherAppointment = new DonationAppointment(otherDonor, LocalDate.now());
        otherAppointment.setStatus("ATTENDED");
        when(appointments.findByIdForUpdate(3L)).thenReturn(Optional.of(otherAppointment));

        assertThrows(IllegalArgumentException.class, () -> service.recordDonation(3L, donor, "Whole Blood", 1, LocalDate.now(), null, "admin@example.test"));

        verify(donations, never()).save(any());
    }

    @Test
    void futureAppointmentCannotBeMarkedAttendedOrRecorded() {
        DonationAppointment future = new DonationAppointment(donor, LocalDate.now().plusDays(1));
        future.setStatus("CONFIRMED");
        when(appointments.findByIdForUpdate(3L)).thenReturn(Optional.of(future));

        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(3L, "ATTENDED", "Arrived", "admin@example.test"));
        future.setStatus("ATTENDED");
        assertThrows(IllegalArgumentException.class, () -> service.recordDonation(3L, donor, "Whole Blood", 1, LocalDate.now(), null, "admin@example.test"));
        verify(donations, never()).save(any());
    }

    @Test
    void sameDayAppointmentInTheFutureCannotBeMarkedAttendedOrRecorded() {
        LocalDate today=LocalDate.now();
        DonationAppointment futureToday=new DonationAppointment(donor,today);
        futureToday.setSchedule(today,LocalTime.of(11,0),"Test center");
        futureToday.setStatus("CONFIRMED");
        when(appointments.findByIdForUpdate(3L)).thenReturn(Optional.of(futureToday));

        assertThrows(IllegalArgumentException.class,()->service.updateStatus(3L,"ATTENDED","Arrived","admin@example.test"));
        futureToday.setStatus("ATTENDED");
        assertThrows(IllegalArgumentException.class,()->service.recordDonation(3L,donor,"Whole Blood",1,today,null,"admin@example.test"));
        verify(donations,never()).save(any());
        verify(events,never()).save(any());
    }

    @Test
    void duplicateActiveAppointmentForSameDonorAndSlotIsRejected() {
        LocalDate today=LocalDate.now();
        LocalTime slot=LocalTime.of(12,0);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(donor));
        when(appointments.existsByDonorAndRequestedDateAndRequestedTimeAndStatusInAndDemoData(
                donor,today,slot,List.of("PENDING","CONFIRMED"),false)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,()->service.request(donor,today,slot,"Test center",false,false,false,"donor@example.test"));

        verify(appointments,never()).save(any());
        verify(events,never()).save(any());
        verify(reminders,never()).synchronize(any());
    }

    @Test
    void reschedulingIntoAnExistingActiveSlotIsRejectedWithoutChangingTheAppointment() {
        LocalDate today=LocalDate.now();
        LocalTime existingTime=LocalTime.of(12,0);
        appointment.setStatus("CONFIRMED");
        appointment.setSchedule(today,LocalTime.of(13,0),"Original center");
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(donor));
        when(appointments.findByIdForUpdate(3L)).thenReturn(Optional.of(appointment));
        when(appointments.existsByDonorAndRequestedDateAndRequestedTimeAndStatusInAndDemoDataAndIdNot(
                donor,today,existingTime,List.of("PENDING","CONFIRMED"),false,3L)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,()->service.reschedule(3L,donor,today,existingTime,"New center","donor@example.test"));

        assertEquals(LocalTime.of(13,0),appointment.getRequestedTime());
        assertEquals("Original center",appointment.getLocation());
        verify(events,never()).save(any());
    }

    @Test
    void completedTransitionRequiresDonationForSameDonor() {
        when(appointments.findByIdForUpdate(3L)).thenReturn(Optional.of(appointment));
        when(donations.existsByAppointmentAndDonorAndDemoData(appointment, donor, false)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(3L, "COMPLETED", "Done", "admin@example.test"));

        assertEquals("ATTENDED", appointment.getStatus());
        verify(events, never()).save(any());
    }

    @Test
    void trackerIdsComeOnlyFromPersistedNonDemoRecordsForThisDonor() {
        when(donations.findRecordedAppointmentIds(List.of(appointment), donor, false)).thenReturn(Set.of(12L));

        assertEquals(Set.of(12L), service.recordedAppointmentIds(donor, List.of(appointment)));
        assertEquals(Set.of(), service.recordedAppointmentIds(donor, List.of()));
        verify(donations, times(1)).findRecordedAppointmentIds(List.of(appointment), donor, false);
    }
}
