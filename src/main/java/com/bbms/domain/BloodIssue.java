package com.bbms.domain;
import jakarta.persistence.*; import java.time.LocalDateTime;
@Entity @Table(name="blood_issues")
public class BloodIssue {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @OneToOne(optional=false) private BloodRequest request;
 private String batchId; private int quantity; private LocalDateTime issuedAt=LocalDateTime.now();
 @Column(nullable=false,columnDefinition="bit(1) default 0") private boolean demoData=false;
 protected BloodIssue(){} public BloodIssue(BloodRequest r,String batch,int qty){request=r;batchId=batch;quantity=qty;demoData=r.isDemoData();}
 public Long getId(){return id;} public BloodRequest getRequest(){return request;} public String getBatchId(){return batchId;} public int getQuantity(){return quantity;} public LocalDateTime getIssuedAt(){return issuedAt;} public boolean isDemoData(){return demoData;}
}
