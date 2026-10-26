package com.bbms.service;

import com.bbms.domain.*;
import com.bbms.repo.DonationReminderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class DonationReminderService {
    private final DonationReminderRepository reminders;
    public DonationReminderService(DonationReminderRepository reminders){this.reminders=reminders;}

    @Transactional
    public void synchronize(DonationAppointment appointment){
        LocalTime time=appointment.getRequestedTime()==null?LocalTime.of(9,0):appointment.getRequestedTime();
        LocalDateTime appointmentAt=LocalDateTime.of(appointment.getRequestedDate(),time);
        Map<Integer,Boolean> preferences=Map.of(3,appointment.isReminder3Days(),1,appointment.isReminder1Day(),0,appointment.isReminderDayOf());
        for(var preference:preferences.entrySet()){
            int offset=preference.getKey();
            DonationReminder reminder=reminders.findByAppointmentAndDaysBefore(appointment,offset).orElse(null);
            if(!preference.getValue()||terminal(appointment.getStatus())){
                if(reminder!=null)reminder.cancel();
                continue;
            }
            LocalDateTime due=appointmentAt.minusDays(offset);
            if(reminder==null)reminders.save(new DonationReminder(appointment,offset,due));
            else reminder.reschedule(due);
        }
    }

    @Transactional
    public void cancelFor(DonationAppointment appointment){
        reminders.findByAppointmentOrderByDueAtAsc(appointment).stream()
                .filter(r->!r.getStatus().equals("CANCELLED")).forEach(DonationReminder::cancel);
    }

    @Scheduled(fixedDelayString="60000")
    @Transactional
    public void promoteDueReminders(){
        LocalDateTime now=LocalDateTime.now();
        for(DonationReminder reminder:reminders.findAllByStatusAndDueAtLessThanEqual("SCHEDULED",now)){
            if(terminal(reminder.getAppointment().getStatus()))reminder.cancel();
            else reminder.markDue();
        }
    }

    private boolean terminal(String status){return List.of("ATTENDED","COMPLETED","CANCELLED","REJECTED").contains(status);}
}
