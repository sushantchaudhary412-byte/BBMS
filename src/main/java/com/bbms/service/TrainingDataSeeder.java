package com.bbms.service;

import com.bbms.domain.BloodUnit;
import com.bbms.domain.User;
import com.bbms.repo.BloodRequestRepository;
import com.bbms.repo.BloodUnitRepository;
import com.bbms.repo.DonationAppointmentRepository;
import com.bbms.repo.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

/** Idempotent seed, activated only by the explicit one-time training seed command. */
@Component
@Profile("training")
public class TrainingDataSeeder implements ApplicationRunner {
    private static final String[] DONORS = {"Aditi Shah", "Kavya Patel", "Meera Joshi", "Ananya Desai", "Riya Mehta", "Sneha Verma", "Ishita Gupta", "Tanvi Shah", "Simran Kaur", "Nisha Rao"};
    private static final String[] GROUPS = {"A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"};
    private static final String[] HOSPITALS = {"Riddhi Hospitals", "Lifecare Hospital", "City Care Hospital", "Sunrise Multispeciality Hospital"};
    private static final String[] COMPONENTS = {"Whole Blood", "Red Cells", "Platelets", "Plasma"};

    private final Environment env;
    private final UserRepository users;
    private final BloodUnitRepository units;
    private final DonationAppointmentRepository appointments;
    private final BloodRequestRepository requests;
    private final AccountService accounts;
    private final InventoryReceiptService receipts;
    private final DonationAppointmentService appointmentService;
    private final BloodRequestWorkflowService requestWorkflow;
    private final Clock clock;

    public TrainingDataSeeder(Environment env, UserRepository users, BloodUnitRepository units,
                              DonationAppointmentRepository appointments, BloodRequestRepository requests,
                              AccountService accounts, InventoryReceiptService receipts,
                              DonationAppointmentService appointmentService,
                              BloodRequestWorkflowService requestWorkflow, Clock clock) {
        this.env=env; this.users=users; this.units=units; this.appointments=appointments; this.requests=requests;
        this.accounts=accounts; this.receipts=receipts; this.appointmentService=appointmentService;
        this.requestWorkflow=requestWorkflow; this.clock=clock;
    }

    @Override @Transactional public void run(ApplicationArguments args) {
        if (!Boolean.parseBoolean(env.getProperty("bbms.training.seed", "false"))) return;
        seedAccountsAndProfiles();
        seedInventory();
        seedRequests();
        System.out.println("Training seed complete: synthetic accounts, future appointments, four pending requests, and a 300-unit synthetic opening stock target are ready.");
    }

    private void seedAccountsAndProfiles() {
        User[] donors = new User[DONORS.length];
        for (int i=0;i<DONORS.length;i++) {
            String email = requiredEmail("TRAINING_DONOR_"+(i+1)+"_EMAIL");
            donors[i] = ensureAccount(DONORS[i], email, "DONOR", "", GROUPS[i % GROUPS.length], "TRAINING_DONOR_"+(i+1)+"_PASSWORD");
            String key="TRAINING-APPT-DONOR-"+(i+1);
            if (appointments.findByDemoKey(key).isEmpty()) {
                LocalDate date=LocalDate.now(clock).plusDays(5+i);
                var appt=appointmentService.request(donors[i], date, LocalTime.of(9+(i%8), 0), "TRAINING — Simulation center", false,false,false,"training-seed@example.test");
                appt.setDemoKey(key); appointments.save(appt);
            }
        }
        for(int i=0;i<HOSPITALS.length;i++)
            ensureAccount(HOSPITALS[i],requiredEmail("TRAINING_HOSPITAL_"+(i+1)+"_EMAIL"),"HOSPITAL","TRAINING — "+HOSPITALS[i],null,"TRAINING_HOSPITAL_"+(i+1)+"_PASSWORD");
    }

    private String requiredEmail(String key) {
        String email=env.getProperty(key);
        if(email==null || !email.toLowerCase(Locale.ROOT).endsWith("@training.example.test")) throw new IllegalStateException(key+" must use the reserved training.example.test domain.");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private User ensureAccount(String name,String email,String role,String organization,String group,String envPassword) {
        var existing=users.findByEmailIgnoreCase(email);
        if(existing.isPresent()) {
            User account=existing.get();
            if(!account.isDemoData() || !role.equals(account.getRole())) throw new IllegalStateException("Training email is occupied by an account that is not matching synthetic training data: "+email);
            return account;
        }
        String password=env.getProperty(envPassword);
        if(password==null||password.length()<10||password.length()>72) throw new IllegalStateException(envPassword+" must be configured with a 10–72 character training password.");
        return accounts.register("TRAINING — "+name,email,password,role,organization,"",group);
    }

    private void seedInventory() {
        LocalDate today=LocalDate.now(clock);
        for(String group:GROUPS) for(int c=0;c<COMPONENTS.length;c++) {
            String g=group.replace("+","POS").replace("-","NEG");
            String component=COMPONENTS[c];
            String comp=component.toUpperCase(Locale.ROOT).replace(" ","-");
            String key="TRAINING-OPEN-"+g+"-"+comp;
            if(units.findByDemoKey(key).isPresent()) continue;
            BloodUnit batch=new BloodUnit();
            batch.setBatchId(key); batch.setDemoKey(key); batch.setBloodGroup(group); batch.setComponent(component);
            batch.setCollectionDate(today.minusDays(2)); batch.setExpiryDate(today.plusDays("Platelets".equals(component)?5:365));
            batch.setStatus("RELEASED");
            int quantity=new int[]{11,10,8,8}[c];
            if(c==0 && group.endsWith("+")) quantity++;
            batch.setQuantity(quantity); batch.setDemoData(true);
            receipts.receive(batch,"training-seed@example.test");
        }
    }

    private void seedRequests() {
        String[][] requestsToSeed={{"0","A+","Red Cells","4"},{"1","O-","Whole Blood","2"},{"2","B+","Platelets","3"},{"3","AB-","Plasma","2"}};
        for(int i=0;i<requestsToSeed.length;i++) {
            String key="TRAINING-REQUEST-"+(i+1);
            if(requests.findByDemoKey(key).isPresent()) continue;
            int hospitalIndex=Integer.parseInt(requestsToSeed[i][0]);
            User hospital=users.findByEmailIgnoreCase(requiredEmail("TRAINING_HOSPITAL_"+(hospitalIndex+1)+"_EMAIL")).orElseThrow();
            var request=requestWorkflow.submit(hospital,requestsToSeed[i][1],requestsToSeed[i][2],Integer.parseInt(requestsToSeed[i][3]),"ROUTINE",
                    "TRAINING-CASE-"+(1001+i),"SYNTHETIC TRAINING REQUEST — no real patient or clinical need.",LocalDate.now(clock),"training-seed@example.test");
            request.setDemoKey(key); requests.save(request);
        }
    }
}
