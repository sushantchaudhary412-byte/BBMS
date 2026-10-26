package com.bbms.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="blood_request_events")
public class BloodRequestEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private BloodRequest request;
    @Column(nullable=false,length=24) private String status;
    @Column(length=400) private String reason;
    @Column(length=254) private String actorEmail;
    @Column(nullable=false) private LocalDateTime occurredAt=LocalDateTime.now();
    @Column(nullable=false,columnDefinition="bit(1) default 0") private boolean demoData=false;
    protected BloodRequestEvent() {}
    public BloodRequestEvent(BloodRequest request,String status,String reason,String actorEmail){this.request=request;this.status=status;this.reason=reason;this.actorEmail=actorEmail;this.demoData=request.isDemoData();}
    public Long getId(){return id;} public BloodRequest getRequest(){return request;} public String getStatus(){return status;} public String getReason(){return reason;} public String getActorEmail(){return actorEmail;} public LocalDateTime getOccurredAt(){return occurredAt;} public boolean isDemoData(){return demoData;}
}
