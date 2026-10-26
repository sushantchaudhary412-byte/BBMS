package com.bbms.domain;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
@Entity @Table(name="blood_units", uniqueConstraints=@UniqueConstraint(columnNames="batch_id"))
public class BloodUnit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @NotBlank @Column(name="batch_id",nullable=false) private String batchId;
 @NotBlank private String bloodGroup;
 @NotBlank private String component;
 @NotNull private LocalDate collectionDate;
 @NotNull private LocalDate expiryDate;
 @NotBlank private String status="RELEASED";
 @Min(0) private int quantity=1;
 @Column(nullable=false,columnDefinition="bit(1) default 0") private boolean demoData=false;
 @Column(length=80,unique=true) private String demoKey;
 public BloodUnit(){}
 public Long getId(){return id;} public String getBatchId(){return batchId;} public String getBloodGroup(){return bloodGroup;} public String getComponent(){return component;} public LocalDate getCollectionDate(){return collectionDate;} public LocalDate getExpiryDate(){return expiryDate;} public String getStatus(){return status;} public int getQuantity(){return quantity;} public boolean isDemoData(){return demoData;} public String getDemoKey(){return demoKey;}
 public void setBatchId(String v){batchId=v;} public void setBloodGroup(String v){bloodGroup=v;} public void setComponent(String v){component=v;} public void setCollectionDate(LocalDate v){collectionDate=v;} public void setExpiryDate(LocalDate v){expiryDate=v;} public void setStatus(String v){status=v;} public void setQuantity(int v){quantity=v;} public void setDemoData(boolean v){demoData=v;} public void setDemoKey(String v){demoKey=v;}
}
