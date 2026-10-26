package com.bbms.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime; import java.time.LocalDate;
@Entity @Table(name="blood_requests")
public class BloodRequest {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) private User requester;
 @NotBlank private String bloodGroup; @NotBlank private String component;
 @Min(1) @Max(50) private int quantity;
 @NotBlank private String urgency;
 @NotBlank private String patientReference;
 @Column(length=500) private String notes;
 private String status="PENDING"; private LocalDateTime createdAt=LocalDateTime.now();
 private LocalDate requestDate=LocalDate.now();
 @Column(nullable=false,columnDefinition="bit(1) default 0") private boolean demoData=false;
 @Column(length=80,unique=true) private String demoKey;
 protected BloodRequest(){}
 public BloodRequest(User u,String bg,String comp,int qty,String urgency,String ref,String notes){requester=u;bloodGroup=bg;component=comp;quantity=qty;this.urgency=urgency;patientReference=ref;this.notes=notes;}
 public Long getId(){return id;} public User getRequester(){return requester;} public String getBloodGroup(){return bloodGroup;} public String getComponent(){return component;} public int getQuantity(){return quantity;} public String getUrgency(){return urgency;} public String getPatientReference(){return patientReference;} public String getNotes(){return notes;} public String getStatus(){return status;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDate getRequestDate(){return requestDate!=null?requestDate:(createdAt!=null?createdAt.toLocalDate():LocalDate.now());} public boolean isDemoData(){return demoData;} public String getDemoKey(){return demoKey;} public void setStatus(String s){status=s;} public void setRequestDate(LocalDate v){requestDate=v;} public void setDemoData(boolean v){demoData=v;} public void setDemoKey(String v){demoKey=v;}
 public String getPriorityLabel(){return priorityLabel(urgency);}
 public String getPriorityKey(){return getPriorityLabel().toLowerCase(java.util.Locale.ROOT);}
 public static String priorityLabel(String storedValue){return "EMERGENCY".equalsIgnoreCase(storedValue)||"URGENT".equalsIgnoreCase(storedValue)?"Urgent":"Routine";}
}
