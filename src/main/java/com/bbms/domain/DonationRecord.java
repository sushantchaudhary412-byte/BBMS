package com.bbms.domain;
import jakarta.persistence.*; import jakarta.validation.constraints.*; import java.time.LocalDate;
@Entity @Table(name="donation_records")
public class DonationRecord {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(optional=false) private User donor;
 @ManyToOne private DonationAppointment appointment;
 @NotBlank private String bloodGroup;
 @NotBlank private String component;
 @Min(1) @Max(10) private int units;
 @NotNull private LocalDate donationDate;
 @Size(max=80) private String inventoryBatchId;
 @Column(length=24) private String testingStatus="COLLECTED";
 @Column(nullable=false,columnDefinition="bit(1) default 0") private boolean demoData=false;
 @Column(length=80,unique=true) private String demoKey;
 protected DonationRecord(){}
 public DonationRecord(User donor,String component,int units,LocalDate donationDate,String batchId){this(donor,null,component,units,donationDate,batchId);}
 public DonationRecord(User donor,DonationAppointment appointment,String component,int units,LocalDate donationDate,String batchId){this.donor=donor;this.appointment=appointment;this.bloodGroup=donor.getBloodGroup();this.component=component;this.units=units;this.donationDate=donationDate;this.inventoryBatchId=batchId;this.demoData=donor.isDemoData()||(appointment!=null&&appointment.isDemoData());}
 public Long getId(){return id;} public User getDonor(){return donor;} public DonationAppointment getAppointment(){return appointment;} public String getBloodGroup(){return bloodGroup;} public String getComponent(){return component;} public int getUnits(){return units;} public LocalDate getDonationDate(){return donationDate;} public String getInventoryBatchId(){return inventoryBatchId;} public String getTestingStatus(){return testingStatus==null?"COLLECTED":testingStatus;} public boolean isDemoData(){return demoData;} public String getDemoKey(){return demoKey;} public void setTestingStatus(String v){testingStatus=v;} public void setInventoryBatchId(String v){inventoryBatchId=v;} public void setDemoData(boolean v){demoData=v;} public void setDemoKey(String v){demoKey=v;}
}
