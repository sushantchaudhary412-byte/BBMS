package com.bbms.service;

import com.bbms.domain.*;
import com.bbms.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.Clock;
import java.util.*;

@Service
public class BloodRequestWorkflowService {
    private final BloodRequestRepository requests;
    private final BloodRequestEventRepository events;
    private final BloodUnitRepository units;
    @Value("${bbms.training.mode:false}") private boolean trainingMode;
    private Clock clock = Clock.systemDefaultZone();
    public BloodRequestWorkflowService(BloodRequestRepository requests,BloodRequestEventRepository events,BloodUnitRepository units){this.requests=requests;this.events=events;this.units=units;}
    @Autowired public void setClock(Clock clock) { this.clock = clock; }

    @Transactional
    public BloodRequest submit(User requester,String group,String component,int quantity,String urgency,String reference,String notes,LocalDate requestDate,String actor){
        if(!BloodBankService.GROUPS.contains(group)||!List.of("Whole Blood","Red Cells","Platelets","Plasma").contains(component)||quantity<1||quantity>50||!List.of("ROUTINE","URGENT").contains(urgency)||reference==null||reference.isBlank()||reference.length()>100||notes!=null&&notes.length()>500||requestDate==null||requestDate.isAfter(LocalDate.now(clock)))throw new IllegalArgumentException("Review the group, component, quantity (1–50), priority, reference, and request date.");
        BloodRequest r=new BloodRequest(requester,group,component,quantity,urgency,reference.trim(),notes==null?"":notes.trim());r.setRequestDate(requestDate);
        if (trainingMode) { r.setDemoData(true); r.setDemoKey("TRAINING-REQUEST-" + UUID.randomUUID()); }
        r=requests.save(r);events.save(new BloodRequestEvent(r,"PENDING","Request submitted for review.",actor));return r;
    }

    @Transactional
    public BloodRequest review(Long id,String next,String reason,String actor){
        BloodRequest r=requests.findByIdForUpdate(id).orElseThrow(()->new IllegalArgumentException("Request not found."));
        if(r.isDemoData()&&!trainingMode)throw new IllegalArgumentException("This archived placeholder request cannot be processed.");
        if(!"PENDING".equals(r.getStatus())||!List.of("APPROVED","REJECTED").contains(next))throw new IllegalArgumentException("Only pending requests can be approved or rejected.");
        if(reason==null||reason.isBlank()||reason.trim().length()>400)throw new IllegalArgumentException("Enter a review reason (up to 400 characters).");
        if("APPROVED".equals(next)){
            long available=units.stockBySource(r.getBloodGroup(),r.getComponent(),LocalDate.now(clock),r.isDemoData());
            if(available<r.getQuantity())throw new IllegalArgumentException("Insufficient eligible "+r.getBloodGroup()+" "+r.getComponent()+" stock: "+available+" units available; "+r.getQuantity()+" requested. No substitute is recommended.");
        }
        r.setStatus(next);events.save(new BloodRequestEvent(r,next,reason.trim(),actor));return r;
    }
}
