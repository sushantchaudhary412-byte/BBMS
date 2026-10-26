package com.bbms.repo;

import com.bbms.domain.DonationAppointmentEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface DonationAppointmentEventRepository extends JpaRepository<DonationAppointmentEvent,Long> {
    boolean existsByAppointment(com.bbms.domain.DonationAppointment appointment);
    @Query("select e from DonationAppointmentEvent e where e.appointment.id in :ids order by e.occurredAt asc")
    List<DonationAppointmentEvent> findForAppointments(@Param("ids") Collection<Long> appointmentIds);
    long countByDemoDataTrue();
}
