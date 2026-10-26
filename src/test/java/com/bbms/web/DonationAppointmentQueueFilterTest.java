package com.bbms.web;

import com.bbms.domain.DonationAppointment;
import com.bbms.domain.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DonationAppointmentQueueFilterTest {
    private final LocalDate today = LocalDate.of(2026, 10, 26);
    private final User donor = new User("Test Donor", "queue.test@example.test", "unused", "DONOR", "", "", "O+");

    @Test void futureAttendedAppointmentIsNotOfferedForDonationRecording() {
        DonationAppointment appointment = appointment(today.plusDays(1), "ATTENDED");
        assertFalse(WebController.isDonationEligibleAppointment(appointment, today));
    }

    @Test void attendedAppointmentTodayOrEarlierRemainsEligible() {
        assertTrue(WebController.isDonationEligibleAppointment(appointment(today, "ATTENDED"), today));
        assertTrue(WebController.isDonationEligibleAppointment(appointment(today.minusDays(1), "ATTENDED"), today));
    }

    @Test void nonAttendedAndDemoAppointmentsAreNotOffered() {
        assertFalse(WebController.isDonationEligibleAppointment(appointment(today, "CONFIRMED"), today));
        DonationAppointment demoAppointment = appointment(today, "ATTENDED");
        demoAppointment.setDemoData(true);
        assertFalse(WebController.isDonationEligibleAppointment(demoAppointment, today));
    }

    private DonationAppointment appointment(LocalDate date, String status) {
        DonationAppointment appointment = new DonationAppointment(donor, date);
        appointment.setStatus(status);
        return appointment;
    }
}
