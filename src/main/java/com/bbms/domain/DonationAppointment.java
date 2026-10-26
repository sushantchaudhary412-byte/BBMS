package com.bbms.domain;
import jakarta.persistence.*; import jakarta.validation.constraints.*; import java.time.LocalDate; import java.time.LocalDateTime; import java.time.LocalTime;
@Entity @Table(name="donation_appointments")
public class DonationAppointment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) private User donor;
 @NotNull private LocalDate requestedDate;
 private LocalTime requestedTime;
 @Column(length=150) private String location;
 private boolean reminder3Days; private boolean reminder1Day; private boolean reminderDayOf;
 private String status="PENDING"; private LocalDateTime createdAt=LocalDateTime.now();
 @Column(nullable=false,columnDefinition="bit(1) default 0") private boolean demoData=false;
 @Column(length=80,unique=true) private String demoKey;
 protected DonationAppointment(){} public DonationAppointment(User d,LocalDate date){donor=d;requestedDate=date;}
 public Long getId(){return id;} public User getDonor(){return donor;} public LocalDate getRequestedDate(){return requestedDate;} public LocalTime getRequestedTime(){return requestedTime;} public String getLocation(){return location;} public boolean isReminder3Days(){return reminder3Days;} public boolean isReminder1Day(){return reminder1Day;} public boolean isReminderDayOf(){return reminderDayOf;} public String getStatus(){return status;} public LocalDateTime getCreatedAt(){return createdAt;} public boolean isDemoData(){return demoData;} public String getDemoKey(){return demoKey;}
 public void setStatus(String s){status=s;} public void setSchedule(LocalDate date,LocalTime time,String place){requestedDate=date;requestedTime=time;location=place;} public void setReminderPreferences(boolean three,boolean one,boolean day){reminder3Days=three;reminder1Day=one;reminderDayOf=day;} public void setDemoData(boolean v){demoData=v;} public void setDemoKey(String v){demoKey=v;}
}
