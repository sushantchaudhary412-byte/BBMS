package com.bbms.repo;

import com.bbms.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface DonationReminderRepository extends JpaRepository<DonationReminder,Long> {
    @Query("select r from DonationReminder r where r.appointment=:appointment and r.daysBefore=:days")
    Optional<DonationReminder> findByAppointmentAndDaysBefore(@Param("appointment") DonationAppointment appointment,@Param("days") int daysBefore);
    List<DonationReminder> findByAppointmentOrderByDueAtAsc(DonationAppointment appointment);
    List<DonationReminder> findAllByStatusAndDueAtLessThanEqual(String status,LocalDateTime now);
}
