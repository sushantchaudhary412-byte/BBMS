package com.bbms.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="inventory_movements")
public class InventoryMovement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) private BloodUnit unit;
    @Column(nullable=false,length=24) private String movementType;
    @Column(nullable=false) private int quantityChange;
    @Column(nullable=false) private int quantityAfter;
    @Column(length=100) private String reference;
    @Column(length=254) private String actorEmail;
    @Column(nullable=false) private LocalDateTime occurredAt=LocalDateTime.now();
    @Column(nullable=false,columnDefinition="bit(1) default 0") private boolean demoData=false;
    protected InventoryMovement() {}
    public InventoryMovement(BloodUnit unit,String type,int change,int after,String reference,String actorEmail){this.unit=unit;this.movementType=type;this.quantityChange=change;this.quantityAfter=after;this.reference=reference;this.actorEmail=actorEmail;this.demoData=unit.isDemoData();}
    public Long getId(){return id;} public BloodUnit getUnit(){return unit;} public String getMovementType(){return movementType;} public int getQuantityChange(){return quantityChange;} public int getQuantityAfter(){return quantityAfter;} public String getReference(){return reference;} public String getActorEmail(){return actorEmail;} public LocalDateTime getOccurredAt(){return occurredAt;} public boolean isDemoData(){return demoData;}
}
