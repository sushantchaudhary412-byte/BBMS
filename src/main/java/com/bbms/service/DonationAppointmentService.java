package com.bbms.service;

import com.bbms.domain.*;
import com.bbms.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class DonationAppointmentService {
    private final DonationAppointmentRepository appointments;
    private final DonationAppointmentEventRepository events;
    private final DonationReminderService reminders;
    private final DonationRecordRepository donations;
    private final UserRepository users;
    private Clock clock=Clock.systemDefaultZone();

    public DonationAppointmentService(DonationAppointmentRepository appointments,DonationAppointmentEventRepository events,
                                      DonationReminderService reminders,DonationRecordRepository donations,UserRepository users){
        this.appointments=appointments;this.events=events;this.reminders=reminders;this.donations=donations;this.users=users;
    }

    @Transactional
    public DonationAppointment request(User donor,LocalDate date,LocalTime time,String location,boolean remind3,boolean remind1,boolean remind0,String actor){
        User lockedDonor=users.findByIdForUpdate(donor.getId()).orElseThrow(()->new IllegalArgumentException("Donor account not found."));
        validateSchedule(date,time,location);
        if(appointments.existsByDonorAndRequestedDateAndRequestedTimeAndStatusInAndDemoData(lockedDonor,date,time,List.of("PENDING","CONFIRMED"),lockedDonor.isDemoData()))
            throw new IllegalArgumentException("An active appointment already exists for this date and time.");
        DonationAppointment a=new DonationAppointment(lockedDonor,date);a.setSchedule(date,time,location);a.setReminderPreferences(remind3,remind1,remind0);
        a.setDemoData(lockedDonor.isDemoData());
        a=appointments.save(a);events.save(new DonationAppointmentEvent(a,"PENDING","Appointment requested",actor));reminders.synchronize(a);return a;
    }

    @Transactional
    public DonationAppointment updateStatus(Long id,String next,String detail,String actor){
        DonationAppointment a=appointments.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("Appointment not found."));
        String current=a.getStatus();
        if("ATTENDED".equals(next)&&isAppointmentTimeInFuture(a,LocalDateTime.now(clock)))throw new IllegalArgumentException("A future appointment cannot be marked attended.");
        boolean allowed=switch(current){case "PENDING" -> List.of("CONFIRMED","REJECTED","CANCELLED").contains(next);case "CONFIRMED" -> List.of("ATTENDED","CANCELLED").contains(next);case "ATTENDED" -> "COMPLETED".equals(next)&&donations.existsByAppointmentAndDonorAndDemoData(a,a.getDonor(),a.isDemoData());default -> false;};
        if(!allowed)throw new IllegalArgumentException("That appointment status transition is not allowed.");
        a.setStatus(next);events.save(new DonationAppointmentEvent(a,next,detail,actor));
        if(List.of("ATTENDED","COMPLETED","CANCELLED","REJECTED").contains(next))reminders.cancelFor(a);
        else reminders.synchronize(a);
        return a;
    }

    @Transactional
    public DonationAppointment reschedule(Long id,User donor,LocalDate date,LocalTime time,String location,String actor){
        users.findByIdForUpdate(donor.getId()).orElseThrow(()->new IllegalArgumentException("Donor account not found."));
        DonationAppointment a=appointments.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("Appointment not found."));
        if(!a.getDonor().getId().equals(donor.getId()))throw new IllegalArgumentException("Appointment not found.");
        if(!List.of("PENDING","CONFIRMED").contains(a.getStatus()))throw new IllegalArgumentException("Only a pending or confirmed appointment can be rescheduled.");
        validateSchedule(date,time,location);
        if(appointments.existsByDonorAndRequestedDateAndRequestedTimeAndStatusInAndDemoDataAndIdNot(a.getDonor(),date,time,List.of("PENDING","CONFIRMED"),a.isDemoData(),a.getId()))
            throw new IllegalArgumentException("An active appointment already exists for this date and time.");
        a.setSchedule(date,time,location);a.setStatus("PENDING");
        events.save(new DonationAppointmentEvent(a,"RESCHEDULED","Donor changed the appointment schedule; bank confirmation is required again.",actor));
        reminders.synchronize(a);return a;
    }

    @Transactional
    public DonationRecord recordDonation(Long appointmentId,User donor,String component,int units,LocalDate date,String batchId,String actor){
        LocalDateTime now=LocalDateTime.now(clock);
        if(date==null||date.isAfter(now.toLocalDate()))throw new IllegalArgumentException("Donation date cannot be in the future.");
        DonationAppointment appointment=null;
        if(appointmentId!=null){
            appointment=appointments.findByIdForUpdate(appointmentId).orElseThrow(()->new IllegalArgumentException("Appointment not found."));
            if(!appointment.getDonor().getId().equals(donor.getId())||!"ATTENDED".equals(appointment.getStatus()))throw new IllegalArgumentException("Select an attended appointment for this donor.");
            if(isAppointmentTimeInFuture(appointment,now)||date.isBefore(appointment.getRequestedDate()))throw new IllegalArgumentException("The appointment must have occurred before recording its donation.");
            if(donations.existsByAppointment(appointment))throw new IllegalArgumentException("A donation is already recorded for this appointment.");
        }
        DonationRecord record=new DonationRecord(donor,appointment,component,units,date,batchId);
        DonationRecord saved=donations.save(record);
        if(appointment!=null)updateStatus(appointment.getId(),"COMPLETED","Donation record saved; testing/release status is tracked separately.",actor);
        return saved;
    }

    @Transactional(readOnly=true)
    public Set<Long> recordedAppointmentIds(User donor,List<DonationAppointment> appointments){
        if(appointments==null||appointments.isEmpty())return Set.of();
        return donations.findRecordedAppointmentIds(appointments,donor,donor.isDemoData());
    }

    @Autowired public void setClock(Clock clock){this.clock=clock;}

    private void validateSchedule(LocalDate date,LocalTime time,String location){
        LocalDateTime now=LocalDateTime.now(clock);
        if(date==null||date.isBefore(now.toLocalDate()))throw new IllegalArgumentException("Choose today or a future appointment date.");
        if(time==null||time.isBefore(LocalTime.of(6,0))||time.isAfter(LocalTime.of(20,0))||(date.equals(now.toLocalDate())&&!time.isAfter(now.toLocalTime())))throw new IllegalArgumentException("Choose a future appointment time between 06:00 and 20:00.");
        if(location==null||location.isBlank()||location.length()>150)throw new IllegalArgumentException("Choose a valid donation location.");
    }

    static boolean isAppointmentTimeInFuture(DonationAppointment appointment,LocalDateTime now){
        LocalDate date=appointment.getRequestedDate();
        return date.isAfter(now.toLocalDate())||(date.equals(now.toLocalDate())&&appointment.getRequestedTime()!=null&&appointment.getRequestedTime().isAfter(now.toLocalTime()));
    }
}
