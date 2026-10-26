package com.bbms.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="donation_appointment_events")
public class DonationAppointmentEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private DonationAppointment appointment;
    @Column(nullable=false,length=24) private String status;
    @Column(length=400) private String detail;
    @Column(length=254) private String actorEmail;
    @Column(nullable=false) private LocalDateTime occurredAt=LocalDateTime.now();
    @Column(nullable=false,columnDefinition="bit(1) default 0") private boolean demoData=false;
    protected DonationAppointmentEvent() {}
    public DonationAppointmentEvent(DonationAppointment appointment,String status,String detail,String actorEmail){this.appointment=appointment;this.status=status;this.detail=detail;this.actorEmail=actorEmail;this.demoData=appointment.isDemoData();}
    public Long getId(){return id;} public DonationAppointment getAppointment(){return appointment;} public String getStatus(){return status;} public String getDetail(){return detail;} public String getActorEmail(){return actorEmail;} public LocalDateTime getOccurredAt(){return occurredAt;} public boolean isDemoData(){return demoData;}
}
