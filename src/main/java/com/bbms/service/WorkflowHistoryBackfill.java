package com.bbms.service;

import com.bbms.domain.*;
import com.bbms.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adds an honest baseline for records created before audit tracking existed. */
@Service
public class WorkflowHistoryBackfill {
    private static final String BASELINE="Status when workflow history was introduced; earlier transitions are unavailable.";
    private final BloodRequestRepository requests; private final BloodRequestEventRepository requestEvents;
    private final DonationAppointmentRepository appointments; private final DonationAppointmentEventRepository appointmentEvents;
    private final BloodUnitRepository units; private final InventoryMovementRepository movements;

    public WorkflowHistoryBackfill(BloodRequestRepository requests,BloodRequestEventRepository requestEvents,
                                   DonationAppointmentRepository appointments,DonationAppointmentEventRepository appointmentEvents,
                                   BloodUnitRepository units,InventoryMovementRepository movements){
        this.requests=requests;this.requestEvents=requestEvents;this.appointments=appointments;this.appointmentEvents=appointmentEvents;this.units=units;this.movements=movements;
    }

    @Transactional
    public void backfill(){
        for(BloodRequest request:requests.findAll())if(!requestEvents.existsByRequest(request))requestEvents.save(new BloodRequestEvent(request,request.getStatus(),BASELINE,"system-migration"));
        for(DonationAppointment appointment:appointments.findAll())if(!appointmentEvents.existsByAppointment(appointment))appointmentEvents.save(new DonationAppointmentEvent(appointment,appointment.getStatus(),BASELINE,"system-migration"));
        for(BloodUnit unit:units.findAll())if(unit.getQuantity()>0&&movements.findByUnitIdOrderByOccurredAtDesc(unit.getId()).isEmpty())movements.save(new InventoryMovement(unit,"OPENING_BALANCE",unit.getQuantity(),unit.getQuantity(),"Quantity when movement history was introduced","system-migration"));
    }
}
