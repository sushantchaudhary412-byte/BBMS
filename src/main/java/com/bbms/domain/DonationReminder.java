package com.bbms.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="donation_reminders",uniqueConstraints=@UniqueConstraint(columnNames={"appointment_id","days_before"}))
public class DonationReminder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private DonationAppointment appointment;
    @Column(nullable=false) private int daysBefore;
    @Column(nullable=false) private LocalDateTime dueAt;
    @Column(nullable=false,length=16) private String status="SCHEDULED";
    @Column(nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
    protected DonationReminder() {}
    public DonationReminder(DonationAppointment appointment,int daysBefore,LocalDateTime dueAt){this.appointment=appointment;this.daysBefore=daysBefore;this.dueAt=dueAt;}
    public Long getId(){return id;} public DonationAppointment getAppointment(){return appointment;} public int getDaysBefore(){return daysBefore;} public LocalDateTime getDueAt(){return dueAt;} public String getStatus(){return status;} public LocalDateTime getCreatedAt(){return createdAt;}
    public void reschedule(LocalDateTime value){dueAt=value;status="SCHEDULED";}
    public void markDue(){status="DUE";}
    public void cancel(){status="CANCELLED";}
}
