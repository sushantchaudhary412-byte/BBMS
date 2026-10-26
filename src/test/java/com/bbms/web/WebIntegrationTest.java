package com.bbms.web;

import com.bbms.domain.User;
import com.bbms.domain.BloodRequest;
import com.bbms.domain.BloodUnit;
import com.bbms.domain.DonationAppointment;
import com.bbms.repo.UserRepository;
import com.bbms.repo.BloodRequestRepository;
import com.bbms.repo.BloodUnitRepository;
import com.bbms.repo.DonationRecordRepository;
import com.bbms.repo.BloodIssueRepository;
import com.bbms.repo.DonationAppointmentRepository;
import com.bbms.repo.DonationAppointmentEventRepository;
import com.bbms.repo.DonationReminderRepository;
import com.bbms.repo.InventoryMovementRepository;
import com.bbms.service.DonationAppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@ContextConfiguration(initializers=com.bbms.config.TestDatabaseSafetyGuard.class)
class WebIntegrationTest {
 @Autowired MockMvc mvc; @Autowired UserRepository users; @Autowired BloodRequestRepository requests; @Autowired BloodUnitRepository units; @Autowired DonationRecordRepository donations; @Autowired BloodIssueRepository issues; @Autowired PasswordEncoder encoder;
 @Autowired DonationAppointmentRepository appointments; @Autowired DonationAppointmentEventRepository appointmentEvents; @Autowired DonationReminderRepository reminders; @Autowired InventoryMovementRepository movements; @Autowired DonationAppointmentService appointmentService;

 @Test void publicPagesAndRoleRestrictedTemplatesRender() throws Exception {
  mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Available by blood group")));
  mvc.perform(get("/login")).andExpect(status().isOk()).andExpect(content().string(containsString("Sign in")));
  mvc.perform(get("/js/app.js")).andExpect(status().isOk()).andExpect(content().string(containsString("accountRole")));
  mvc.perform(get("/register")).andExpect(status().isOk()).andExpect(content().string(containsString("Blood Donor"))).andExpect(content().string(containsString("Hospital / Requester"))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("value=\"ADMIN\""))));
  mvc.perform(get("/admin/dashboard").with(user("audit-admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(view().name("admin"));
  mvc.perform(get("/admin/inventory").with(user("audit-admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(view().name("inventory"));
  MvcResult inventoryPage=mvc.perform(get("/admin/inventory").with(user("audit-admin").roles("ADMIN"))).andExpect(status().isOk()).andReturn();
  org.junit.jupiter.api.Assertions.assertTrue(inventoryPage.getResponse().getContentAsString().contains("Inventory planning targets"));
  org.junit.jupiter.api.Assertions.assertTrue(inventoryPage.getResponse().getContentAsString().contains("Planning targets are not current stock"));
  org.junit.jupiter.api.Assertions.assertFalse(inventoryPage.getResponse().getContentAsString().contains("Add unverified target samples"));
  org.junit.jupiter.api.Assertions.assertFalse(inventoryPage.getResponse().getContentAsString().contains("academic-simulation"));
  mvc.perform(get("/admin/accounts").with(user("audit-admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(view().name("accounts"));
  mvc.perform(get("/admin/requests").with(user("audit-admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(view().name("requests"));
  mvc.perform(get("/admin/donations").with(user("audit-admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(view().name("donations"));
  mvc.perform(get("/admin/reports").with(user("audit-admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(view().name("reports"));
 }

 @Test void missingInventoryBatchReturnsNotFoundInsteadOfAnInternalError() throws Exception {
  mvc.perform(get("/admin/inventory/{id}", Long.MAX_VALUE).with(user("missing-batch-admin").roles("ADMIN")))
    .andExpect(status().isNotFound());
 }

 @Test void targetSampleInitializerIsDisabledByDefaultAndAdminOnly() throws Exception {
  mvc.perform(post("/admin/inventory/target-samples/initialize").with(user("audit-admin").roles("ADMIN")).with(csrf()))
    .andExpect(status().isNotFound());
  mvc.perform(post("/admin/inventory/target-samples/initialize").with(user("audit-donor").roles("DONOR")).with(csrf()))
    .andExpect(status().isForbidden());
 }

 @Test void authenticatedLoginPageAndDashboardRouteToTheUsersRole() throws Exception {
  User admin=users.save(new User("Routing Admin","routing.admin@example.test",encoder.encode("local-audit-password"),"ADMIN","Test admin",""));
  User hospital=users.save(new User("Routing Hospital","routing.hospital@example.test",encoder.encode("local-audit-password"),"HOSPITAL","Routing Clinic",""));
  mvc.perform(get("/login").with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"));
  mvc.perform(get("/dashboard").with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/dashboard"));
  mvc.perform(get("/login").with(user(hospital.getEmail()).roles("HOSPITAL"))).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"));
  mvc.perform(get("/dashboard").with(user(hospital.getEmail()).roles("HOSPITAL"))).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/hospital/dashboard"));
 }

 @Test void donorRegistrationStoresCorrectFieldsAndOnlyPasswordHash() throws Exception {
  mvc.perform(post("/register").with(csrf()).param("name","Audit Donor").param("email","Audit.Donor@example.test").param("password","local-audit-password").param("role","DONOR").param("bloodGroup","O+").param("phone","555-0101"))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?registered"));
  User saved=users.findByEmailIgnoreCase("audit.donor@example.test").orElseThrow();
  org.junit.jupiter.api.Assertions.assertEquals("Audit Donor",saved.getName());
  org.junit.jupiter.api.Assertions.assertEquals("audit.donor@example.test",saved.getEmail());
  org.junit.jupiter.api.Assertions.assertEquals("O+",saved.getBloodGroup());
  org.junit.jupiter.api.Assertions.assertTrue(encoder.matches("local-audit-password",saved.getPassword()));
  org.junit.jupiter.api.Assertions.assertNotEquals("local-audit-password",saved.getPassword());
 }

 @Test void hospitalRegistrationRequiresOrganizationAndPersistsRequesterRole() throws Exception {
  mvc.perform(post("/register").with(csrf()).param("name","Audit Hospital").param("email","audit.hospital@example.test").param("password","local-audit-password").param("role","HOSPITAL").param("organization","Audit Community Clinic"))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?registered"));
  User saved=users.findByEmailIgnoreCase("audit.hospital@example.test").orElseThrow();
  org.junit.jupiter.api.Assertions.assertEquals("HOSPITAL",saved.getRole());
  org.junit.jupiter.api.Assertions.assertEquals("Audit Community Clinic",saved.getOrganization());
  org.junit.jupiter.api.Assertions.assertNull(saved.getBloodGroup());
 }

 @Test void publicRegistrationCannotCreateAdminAndInvalidInputKeepsSafeValues() throws Exception {
  mvc.perform(post("/register").with(csrf()).param("name","Not Admin").param("email","not.admin@example.test").param("password","local-audit-password").param("role","ADMIN").param("bloodGroup","A+"))
    .andExpect(status().isOk()).andExpect(view().name("register")).andExpect(content().string(containsString("Not Admin")));
  org.junit.jupiter.api.Assertions.assertTrue(users.findByEmailIgnoreCase("not.admin@example.test").isEmpty());
  mvc.perform(post("/register").with(csrf()).param("name","Missing Group").param("email","missing.group@example.test").param("password","short").param("role","DONOR"))
    .andExpect(status().isOk()).andExpect(view().name("register"));
  org.junit.jupiter.api.Assertions.assertTrue(users.findByEmailIgnoreCase("missing.group@example.test").isEmpty());
 }

 @Test void duplicateEmailsAreRejectedWithoutCreatingAnAdditionalAccount() throws Exception {
  users.save(new User("First","duplicate@example.test",encoder.encode("existing-password"),"DONOR","", "", "A+"));
  mvc.perform(post("/register").with(csrf()).param("name","Second").param("email","DUPLICATE@example.test").param("password","another-local-password").param("role","DONOR").param("bloodGroup","B+"))
    .andExpect(status().isOk()).andExpect(view().name("register")).andExpect(content().string(containsString("already exists")));
 }

 @Test void roleGuardsBlockCrossRoleAccessAndHospitalPageShowsOnlyTwoPriorities() throws Exception {
  User hospital=users.save(new User("Scoped Hospital","scoped.hospital@example.test",encoder.encode("local-audit-password"),"HOSPITAL","Scoped Clinic",""));
  mvc.perform(get("/admin/dashboard").with(user("donor").roles("DONOR"))).andExpect(status().isForbidden()).andExpect(forwardedUrl("/error/403"));
  for(String path:java.util.List.of("/admin/accounts","/admin/inventory","/admin/requests","/admin/donations","/admin/reports"))
   mvc.perform(get(path).with(user("donor").roles("DONOR"))).andExpect(status().isForbidden());
  for(String path:java.util.List.of("/admin/dashboard","/admin/accounts","/admin/inventory","/admin/requests","/admin/donations","/admin/reports"))
   mvc.perform(get(path).with(user("hospital").roles("HOSPITAL"))).andExpect(status().isForbidden());
  mvc.perform(get("/hospital/dashboard").with(user("donor").roles("DONOR"))).andExpect(status().isForbidden());
  mvc.perform(get("/hospital/dashboard").with(user(hospital.getEmail()).roles("HOSPITAL"))).andExpect(status().isOk()).andExpect(content().string(containsString("Routine"))).andExpect(content().string(containsString("Urgent"))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("EMERGENCY"))));
 }

 @Test void allRolesAuthenticateAndReachOnlyTheirRoleDashboard() throws Exception {
  String raw="isolated-login-test-password";
  User admin=users.save(new User("Login Admin","login.admin@example.test",encoder.encode(raw),"ADMIN","Test administration",""));
  User donor=users.save(new User("Login Donor","login.donor@example.test",encoder.encode(raw),"DONOR","","","A+"));
  User hospital=users.save(new User("Login Hospital","login.hospital@example.test",encoder.encode(raw),"HOSPITAL","Test clinic",""));
  for (var account : java.util.List.of(new Object[]{admin,"/admin/dashboard"},new Object[]{donor,"/donor/dashboard"},new Object[]{hospital,"/hospital/dashboard"})) {
   User accountUser=(User)account[0]; String destination=(String)account[1];
   MvcResult login=mvc.perform(post("/login").with(csrf()).param("username",accountUser.getEmail()).param("password",raw))
     .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard")).andReturn();
   MockHttpSession session=(MockHttpSession)login.getRequest().getSession(false);
   org.junit.jupiter.api.Assertions.assertNotNull(session);
   mvc.perform(get("/dashboard").session(session)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(destination));
   mvc.perform(get(destination).session(session)).andExpect(status().isOk());
  }
 }

 @Test void securityAndNotFoundErrorsUseBrandedPagesAndPreserveStatus() throws Exception {
  mvc.perform(get("/error/403")).andExpect(status().isForbidden()).andExpect(content().string(containsString("not available to your account")));
  mvc.perform(get("/error/404")).andExpect(status().isNotFound()).andExpect(content().string(containsString("could not find that page")));
  mvc.perform(get("/error/500")).andExpect(status().isInternalServerError()).andExpect(content().string(containsString("could not complete that request")));
  mvc.perform(get("/admin/inventory/999999999").with(user("missing-record-admin").roles("ADMIN")))
    .andExpect(status().isNotFound());
  mvc.perform(post("/admin/accounts/1/toggle").with(user("csrf-admin").roles("ADMIN")))
    .andExpect(status().isForbidden()).andExpect(forwardedUrl("/error/403"));
 }

 @Test void hospitalRequestAndAdminInventoryFormsPersistValidatedData() throws Exception {
  User hospital=users.save(new User("Requesting Clinic","requester@example.test",encoder.encode("local-audit-password"),"HOSPITAL","Test Clinic",""));
  mvc.perform(post("/hospital/requests").with(csrf()).with(user(hospital.getEmail()).roles("HOSPITAL")).param("bloodGroup","O+").param("component","Whole Blood").param("quantity","2").param("urgency","URGENT").param("patientReference","TEST-REF-01"))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/hospital/dashboard"));
  org.junit.jupiter.api.Assertions.assertEquals(1,requests.findByRequesterOrderByCreatedAtDesc(hospital).size());
  org.junit.jupiter.api.Assertions.assertEquals("URGENT",requests.findByRequesterOrderByCreatedAtDesc(hospital).get(0).getUrgency());
  mvc.perform(post("/hospital/requests").with(csrf()).with(user(hospital.getEmail()).roles("HOSPITAL")).param("bloodGroup","O+").param("component","Whole Blood").param("quantity","2").param("urgency","EMERGENCY").param("patientReference","TEST-REF-02"))
    .andExpect(status().is3xxRedirection());
  org.junit.jupiter.api.Assertions.assertEquals(1,requests.findByRequesterOrderByCreatedAtDesc(hospital).size());
  mvc.perform(post("/admin/inventory").with(csrf()).with(user("audit-admin").roles("ADMIN")).param("batchId","AUDIT-BATCH-01").param("bloodGroup","O+").param("component","Whole Blood").param("collectionDate",java.time.LocalDate.now().toString()).param("expiryDate",java.time.LocalDate.now().plusDays(35).toString()).param("status","RELEASED").param("quantity","4").param("receiptReference","TEST-RECEIPT-AUDIT"))
    .andExpect(status().is3xxRedirection());
  org.junit.jupiter.api.Assertions.assertTrue(units.findByBatchId("AUDIT-BATCH-01").isEmpty());
  mvc.perform(post("/admin/inventory").with(csrf()).with(user("audit-admin").roles("ADMIN")).param("batchId","AUDIT-BATCH-01").param("bloodGroup","O+").param("component","Whole Blood").param("collectionDate",java.time.LocalDate.now().toString()).param("expiryDate",java.time.LocalDate.now().plusDays(35).toString()).param("status","TESTING").param("quantity","4").param("receiptReference","TEST-RECEIPT-AUDIT"))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/inventory"));
  org.junit.jupiter.api.Assertions.assertEquals(4,units.findAllByOrderByExpiryDateAsc().stream().filter(u->u.getBatchId().equals("AUDIT-BATCH-01")).findFirst().orElseThrow().getQuantity());
  var savedBatch=units.findAllByOrderByExpiryDateAsc().stream().filter(u->u.getBatchId().equals("AUDIT-BATCH-01")).findFirst().orElseThrow();
  org.junit.jupiter.api.Assertions.assertEquals("TESTING",savedBatch.getStatus());
  mvc.perform(get("/admin/inventory/{id}",savedBatch.getId()).with(user("audit-admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(content().string(containsString("Stock movement history"))).andExpect(content().string(containsString("TEST-RECEIPT-AUDIT")));
  mvc.perform(post("/admin/inventory/{id}/release",savedBatch.getId()).with(csrf()).with(user("audit-admin").roles("ADMIN")).param("releaseDecisionReference","TEST-LAB-RELEASE-AUDIT"))
    .andExpect(status().is3xxRedirection());
  org.junit.jupiter.api.Assertions.assertEquals("RELEASED",units.findById(savedBatch.getId()).orElseThrow().getStatus());
 }

 @Test void adminDonationRecordIsVisibleToTheDonor() throws Exception {
  User donor=users.save(new User("Donation Test","donation.test@example.test",encoder.encode("local-audit-password"),"DONOR","","","A+"));
  mvc.perform(post("/admin/donations").with(csrf()).with(user("audit-admin").roles("ADMIN")).param("donorId",donor.getId().toString()).param("component","Whole Blood").param("units","1").param("donationDate",java.time.LocalDate.now().toString()))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/donations"));
  org.junit.jupiter.api.Assertions.assertEquals(1,donations.findByDonorOrderByDonationDateDesc(donor).size());
  mvc.perform(get("/donor/dashboard").with(user(donor.getEmail()).roles("DONOR"))).andExpect(status().isOk()).andExpect(content().string(containsString("Donation history"))).andExpect(content().string(containsString("donation on record"))).andExpect(content().string(containsString("A+"))).andExpect(content().string(containsString("Donation Test")));
 }

 @Test void releasedDonationReceiptLinksBatchAndMovementExactlyOnce() throws Exception {
  User donor=users.save(new User("Receipt Workflow Donor","receipt.workflow@example.test","test-only-placeholder","DONOR","","","B+"));
  var record=donations.save(new com.bbms.domain.DonationRecord(donor,"Red Cells",2,java.time.LocalDate.now().minusDays(1),null));
  record.setTestingStatus("TESTING"); record=donations.save(record);
  Long donationId=record.getId();
  mvc.perform(post("/admin/donations/{id}/receive",donationId).with(csrf()).with(user("receipt-admin").roles("ADMIN"))
      .param("batchId","DONATION-RECEIPT-TEST-01").param("expiryDate",java.time.LocalDate.now().plusDays(20).toString())
      .param("releaseDecisionReference","TEST-LAB-RELEASE-41").param("physicalReceiptReference","TEST-PHYSICAL-RECEIPT-41"))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/donations"));
  record=donations.findById(donationId).orElseThrow();
  org.junit.jupiter.api.Assertions.assertEquals("DONATION-RECEIPT-TEST-01",record.getInventoryBatchId());
  org.junit.jupiter.api.Assertions.assertEquals("RELEASED",record.getTestingStatus());
  var batch=units.findByBatchId(record.getInventoryBatchId()).orElseThrow();
  org.junit.jupiter.api.Assertions.assertEquals("B+",batch.getBloodGroup());
  org.junit.jupiter.api.Assertions.assertEquals("Red Cells",batch.getComponent());
  org.junit.jupiter.api.Assertions.assertEquals(2,batch.getQuantity());
  org.junit.jupiter.api.Assertions.assertEquals("RELEASED",batch.getStatus());
  org.junit.jupiter.api.Assertions.assertEquals(2L,units.stock("B+","Red Cells",java.time.LocalDate.now()));
  org.junit.jupiter.api.Assertions.assertEquals(1,movements.findByUnitIdOrderByOccurredAtDesc(batch.getId()).size());
  org.junit.jupiter.api.Assertions.assertEquals("release=TEST-LAB-RELEASE-41; receipt=TEST-PHYSICAL-RECEIPT-41",movements.findByUnitIdOrderByOccurredAtDesc(batch.getId()).get(0).getReference());
  mvc.perform(post("/admin/donations/{id}/receive",donationId).with(csrf()).with(user("receipt-admin").roles("ADMIN"))
      .param("batchId","DONATION-RECEIPT-TEST-02").param("expiryDate",java.time.LocalDate.now().plusDays(20).toString())
      .param("releaseDecisionReference","TEST-LAB-RELEASE-42").param("physicalReceiptReference","TEST-PHYSICAL-RECEIPT-42"))
    .andExpect(status().is3xxRedirection());
  org.junit.jupiter.api.Assertions.assertTrue(units.findByBatchId("DONATION-RECEIPT-TEST-02").isEmpty());
  org.junit.jupiter.api.Assertions.assertEquals(1,movements.findByUnitIdOrderByOccurredAtDesc(batch.getId()).size());
 }

 @Test void attendedAppointmentDonationLifecycleIsLinkedOnceAndDoesNotCreateInventory() throws Exception {
  User donor=users.save(new User("Lifecycle Donor","lifecycle.donor@example.test",encoder.encode("test-password-only"),"DONOR","","","O+"));
  java.time.LocalDate yesterday=java.time.LocalDate.now().minusDays(1);
  DonationAppointment appointment=new DonationAppointment(donor,yesterday);
  appointment.setSchedule(yesterday,java.time.LocalTime.of(10,0),"Test donation center");
  appointment.setStatus("CONFIRMED"); appointment=appointments.save(appointment);
  Long appointmentId=appointment.getId();
  mvc.perform(post("/admin/appointments/{id}/status",appointmentId).with(csrf()).with(user("lifecycle-admin").roles("ADMIN"))
      .param("status","ATTENDED").param("detail","Attended in isolated integration test"))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/donations"));
  appointment=appointments.findById(appointmentId).orElseThrow();
  org.junit.jupiter.api.Assertions.assertEquals("ATTENDED",appointment.getStatus());
  long inventoryBefore=units.count(); long movementsBefore=movements.count();
  mvc.perform(post("/admin/donations").with(csrf()).with(user("lifecycle-admin").roles("ADMIN"))
      .param("donorId",donor.getId().toString()).param("appointmentId",appointmentId.toString())
      .param("component","Whole Blood").param("units","1").param("donationDate",java.time.LocalDate.now().toString()))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/donations"));
  org.junit.jupiter.api.Assertions.assertEquals(1,donations.findByDonorOrderByDonationDateDesc(donor).size());
  var record=donations.findByAppointment(appointment).orElseThrow();
  org.junit.jupiter.api.Assertions.assertEquals(donor.getId(),record.getDonor().getId());
  org.junit.jupiter.api.Assertions.assertEquals("COMPLETED",appointments.findById(appointmentId).orElseThrow().getStatus());
  org.junit.jupiter.api.Assertions.assertTrue(appointmentService.recordedAppointmentIds(donor,java.util.List.of(appointment)).contains(appointmentId));
  var history=appointmentEvents.findForAppointments(java.util.List.of(appointmentId)).stream().map(e->e.getStatus()).toList();
  org.junit.jupiter.api.Assertions.assertTrue(history.contains("ATTENDED")); org.junit.jupiter.api.Assertions.assertTrue(history.contains("COMPLETED"));
  mvc.perform(post("/admin/donations").with(csrf()).with(user("lifecycle-admin").roles("ADMIN"))
      .param("donorId",donor.getId().toString()).param("appointmentId",appointmentId.toString())
      .param("component","Whole Blood").param("units","1").param("donationDate",java.time.LocalDate.now().toString()))
    .andExpect(status().is3xxRedirection());
  org.junit.jupiter.api.Assertions.assertEquals(1,donations.findByDonorOrderByDonationDateDesc(donor).size());
  mvc.perform(post("/admin/donations/{id}/testing",record.getId()).with(csrf()).with(user("lifecycle-admin").roles("ADMIN")).param("status","TESTING"))
    .andExpect(status().is3xxRedirection());
  mvc.perform(post("/admin/donations/{id}/testing",record.getId()).with(csrf()).with(user("lifecycle-admin").roles("ADMIN")).param("status","RELEASED"))
    .andExpect(status().is3xxRedirection());
  org.junit.jupiter.api.Assertions.assertEquals("TESTING",donations.findById(record.getId()).orElseThrow().getTestingStatus());
  org.junit.jupiter.api.Assertions.assertEquals(inventoryBefore,units.count());
  org.junit.jupiter.api.Assertions.assertEquals(movementsBefore,movements.count());
 }

 @Test void hospitalRequestCanBeReviewedAndIssuedAgainstSameEligibleStock() throws Exception {
  User hospital=users.save(new User("End-to-end Clinic","e2e.clinic@example.test",encoder.encode("test-password-only"),"HOSPITAL","E2E Test Clinic",""));
  mvc.perform(post("/admin/inventory").with(csrf()).with(user("inventory-admin").roles("ADMIN"))
      .param("batchId","E2E-FEFO-A").param("bloodGroup","A+").param("component","Whole Blood")
      .param("collectionDate",java.time.LocalDate.now().minusDays(2).toString()).param("expiryDate",java.time.LocalDate.now().plusDays(5).toString())
      .param("status","TESTING").param("quantity","1").param("receiptReference","TEST-RECEIPT-FEFO-A"))
    .andExpect(status().is3xxRedirection());
  var fefoA=units.findByBatchId("E2E-FEFO-A").orElseThrow();
  mvc.perform(post("/admin/inventory/{id}/release",fefoA.getId()).with(csrf()).with(user("inventory-admin").roles("ADMIN")).param("releaseDecisionReference","TEST-RELEASE-FEFO-A"))
    .andExpect(status().is3xxRedirection());
  mvc.perform(post("/admin/inventory").with(csrf()).with(user("inventory-admin").roles("ADMIN"))
      .param("batchId","E2E-FEFO-B").param("bloodGroup","A+").param("component","Whole Blood")
      .param("collectionDate",java.time.LocalDate.now().minusDays(1).toString()).param("expiryDate",java.time.LocalDate.now().plusDays(10).toString())
      .param("status","TESTING").param("quantity","2").param("receiptReference","TEST-RECEIPT-FEFO-B"))
    .andExpect(status().is3xxRedirection());
  var fefoB=units.findByBatchId("E2E-FEFO-B").orElseThrow();
  mvc.perform(post("/admin/inventory/{id}/release",fefoB.getId()).with(csrf()).with(user("inventory-admin").roles("ADMIN")).param("releaseDecisionReference","TEST-RELEASE-FEFO-B"))
    .andExpect(status().is3xxRedirection());
  mvc.perform(post("/admin/inventory").with(csrf()).with(user("inventory-admin").roles("ADMIN"))
      .param("batchId","E2E-EXPIRED").param("bloodGroup","A+").param("component","Whole Blood")
      .param("collectionDate",java.time.LocalDate.now().minusDays(50).toString()).param("expiryDate",java.time.LocalDate.now().minusDays(1).toString())
      .param("status","TESTING").param("quantity","9").param("receiptReference","TEST-RECEIPT-EXPIRED"))
    .andExpect(status().is3xxRedirection());
  mvc.perform(post("/admin/inventory").with(csrf()).with(user("inventory-admin").roles("ADMIN"))
      .param("batchId","E2E-QUARANTINED").param("bloodGroup","A+").param("component","Whole Blood")
      .param("collectionDate",java.time.LocalDate.now().minusDays(1).toString()).param("expiryDate",java.time.LocalDate.now().plusDays(15).toString())
      .param("status","QUARANTINED").param("quantity","8").param("receiptReference","TEST-RECEIPT-QUARANTINED"))
    .andExpect(status().is3xxRedirection());
  org.junit.jupiter.api.Assertions.assertEquals(3L,units.stock("A+","Whole Blood",java.time.LocalDate.now()));
  mvc.perform(post("/hospital/requests").with(csrf()).with(user(hospital.getEmail()).roles("HOSPITAL"))
      .param("bloodGroup","A+").param("component","Whole Blood").param("quantity","2").param("urgency","URGENT").param("patientReference","E2E-REQUEST-01"))
    .andExpect(status().is3xxRedirection());
  BloodRequest request=requests.findByRequesterOrderByCreatedAtDesc(hospital).get(0);
  mvc.perform(post("/admin/requests/{id}/status",request.getId()).with(csrf()).with(user("review-admin").roles("ADMIN"))
      .param("status","APPROVED").param("reason","Two released, unexpired units are available."))
    .andExpect(status().is3xxRedirection());
  mvc.perform(post("/admin/requests/{id}/issue",request.getId()).with(csrf()).with(user("review-admin").roles("ADMIN")))
    .andExpect(status().is3xxRedirection());
  org.junit.jupiter.api.Assertions.assertEquals("ISSUED",requests.findById(request.getId()).orElseThrow().getStatus());
  org.junit.jupiter.api.Assertions.assertEquals(0,units.findByBatchId("E2E-FEFO-A").orElseThrow().getQuantity());
  org.junit.jupiter.api.Assertions.assertEquals(1,units.findByBatchId("E2E-FEFO-B").orElseThrow().getQuantity());
  org.junit.jupiter.api.Assertions.assertEquals(9,units.findByBatchId("E2E-EXPIRED").orElseThrow().getQuantity());
  org.junit.jupiter.api.Assertions.assertEquals(8,units.findByBatchId("E2E-QUARANTINED").orElseThrow().getQuantity());
  org.junit.jupiter.api.Assertions.assertEquals("E2E-FEFO-A × 1, E2E-FEFO-B × 1",issues.findByRequest(request).orElseThrow().getBatchId());
  org.junit.jupiter.api.Assertions.assertEquals(1L,units.stock("A+","Whole Blood",java.time.LocalDate.now()));
 }

 @Test void dashboardAndReportStockTotalsComeFromEligiblePersistedRows() throws Exception {
  BloodUnit released=new BloodUnit(); released.setBatchId("REPORT-RELEASED"); released.setBloodGroup("B+"); released.setComponent("Plasma");
  released.setCollectionDate(java.time.LocalDate.now().minusDays(1)); released.setExpiryDate(java.time.LocalDate.now().plusDays(7)); released.setStatus("RELEASED"); released.setQuantity(2); units.save(released);
  BloodUnit expired=new BloodUnit(); expired.setBatchId("REPORT-EXPIRED"); expired.setBloodGroup("B+"); expired.setComponent("Plasma");
  expired.setCollectionDate(java.time.LocalDate.now().minusDays(40)); expired.setExpiryDate(java.time.LocalDate.now().minusDays(1)); expired.setStatus("RELEASED"); expired.setQuantity(9); units.save(expired);
  BloodUnit testing=new BloodUnit(); testing.setBatchId("REPORT-TESTING"); testing.setBloodGroup("B+"); testing.setComponent("Plasma");
  testing.setCollectionDate(java.time.LocalDate.now().minusDays(1)); testing.setExpiryDate(java.time.LocalDate.now().plusDays(7)); testing.setStatus("TESTING"); testing.setQuantity(8); units.save(testing);

  var dashboard=mvc.perform(get("/admin/dashboard").with(user("report-admin").roles("ADMIN"))).andExpect(status().isOk()).andReturn();
  org.junit.jupiter.api.Assertions.assertEquals(2L,dashboard.getModelAndView().getModel().get("eligibleTotal"));
  var report=mvc.perform(get("/admin/reports").with(user("report-admin").roles("ADMIN"))).andExpect(status().isOk()).andReturn();
  @SuppressWarnings("unchecked") java.util.Map<String,Long> stock=(java.util.Map<String,Long>)report.getModelAndView().getModel().get("stock");
  org.junit.jupiter.api.Assertions.assertEquals(2L,stock.values().stream().mapToLong(Long::longValue).sum());
 }

 @Test void adminCanAuthenticateAndUseAdminRoute() throws Exception {
  String raw="local-test-admin-password";
  users.save(new User("Audit Admin","audit.admin@example.test",encoder.encode(raw),"ADMIN","Test admin",""));
  mvc.perform(post("/login").with(csrf()).param("username","audit.admin@example.test").param("password",raw))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"));
  mvc.perform(get("/admin/dashboard").with(user("audit.admin@example.test").roles("ADMIN"))).andExpect(status().isOk());
 }

 @Test void donorAppointmentSchedulesInAppRemindersAndCancelsThem() throws Exception {
  User donor=users.save(new User("Reminder Donor","reminder.donor@example.test",encoder.encode("local-audit-password"),"DONOR","","","O+"));
  donor.setDemoData(true);users.save(donor);
  String date=java.time.LocalDate.now().plusDays(5).toString();
  mvc.perform(post("/donor/appointments").with(csrf()).with(user(donor.getEmail()).roles("DONOR"))
      .param("requestedDate",date).param("requestedTime","10:30").param("location","Test donation center")
      .param("reminder3Days","true").param("reminder1Day","true"))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/donor/appointments"));
  var appointment=appointments.findByDonorOrderByRequestedDateDesc(donor).get(0);
  org.junit.jupiter.api.Assertions.assertTrue(appointment.isDemoData());
  org.junit.jupiter.api.Assertions.assertEquals(2,reminders.findByAppointmentOrderByDueAtAsc(appointment).size());
  String rescheduledDate=java.time.LocalDate.now().plusDays(7).toString();
  mvc.perform(post("/donor/appointments/{id}/reschedule",appointment.getId()).with(csrf()).with(user(donor.getEmail()).roles("DONOR"))
      .param("requestedDate",rescheduledDate).param("requestedTime","11:15").param("location","Updated donation center"))
    .andExpect(status().is3xxRedirection());
  appointment=appointments.findById(appointment.getId()).orElseThrow();
  org.junit.jupiter.api.Assertions.assertEquals(java.time.LocalDate.parse(rescheduledDate),appointment.getRequestedDate());
  org.junit.jupiter.api.Assertions.assertEquals("PENDING",appointment.getStatus());
  org.junit.jupiter.api.Assertions.assertTrue(reminders.findByAppointmentOrderByDueAtAsc(appointment).stream().allMatch(r->r.getDueAt().toLocalDate().equals(java.time.LocalDate.parse(rescheduledDate).minusDays(r.getDaysBefore()))));
  mvc.perform(post("/donor/appointments/{id}/cancel",appointment.getId()).with(csrf()).with(user(donor.getEmail()).roles("DONOR")))
    .andExpect(status().is3xxRedirection());
  org.junit.jupiter.api.Assertions.assertTrue(reminders.findByAppointmentOrderByDueAtAsc(appointment).stream().allMatch(r->r.getStatus().equals("CANCELLED")));
 }

 @Test void donorDashboardShowsOnlyTheAuthenticatedDonorsRecords() throws Exception {
  User donor=users.save(new User("Private Donor","private.donor@example.test",encoder.encode("local-audit-password"),"DONOR","","","A+"));
  User other=users.save(new User("Other Donor","other.donor@example.test",encoder.encode("local-audit-password"),"DONOR","","","O-"));
  DonationAppointment appointment=new DonationAppointment(other,java.time.LocalDate.now().plusDays(4));
  appointment.setSchedule(appointment.getRequestedDate(),java.time.LocalTime.of(11,0),"Other donor private location");appointments.save(appointment);
  donations.save(new com.bbms.domain.DonationRecord(other,"Plasma",1,java.time.LocalDate.now(),null));
  donations.save(new com.bbms.domain.DonationRecord(donor,"Whole Blood",1,java.time.LocalDate.now(),null));
  mvc.perform(get("/donor/dashboard").with(user(donor.getEmail()).roles("DONOR")))
    .andExpect(status().isOk()).andExpect(content().string(containsString("Private Donor")))
    .andExpect(content().string(containsString("Whole Blood")))
    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Other donor private location"))))
    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Plasma"))));
 }


 @Test void donorDestinationsRenderSeparateOwnerScopedPages() throws Exception {
  User donor=users.save(new User("Separate Pages Donor","separate.pages@example.test",encoder.encode("local-audit-password"),"DONOR","","","A+"));
  User other=users.save(new User("Another Donor","another.pages@example.test",encoder.encode("local-audit-password"),"DONOR","","","O-"));
  DonationAppointment own=new DonationAppointment(donor,java.time.LocalDate.now().plusDays(5));own.setSchedule(own.getRequestedDate(),java.time.LocalTime.of(10,0),"My private center");appointments.save(own);
  DonationAppointment foreign=new DonationAppointment(other,java.time.LocalDate.now().plusDays(6));foreign.setSchedule(foreign.getRequestedDate(),java.time.LocalTime.of(10,0),"Other private center");appointments.save(foreign);
  donations.save(new com.bbms.domain.DonationRecord(donor,"Whole Blood",1,java.time.LocalDate.now(),null));
  donations.save(new com.bbms.domain.DonationRecord(other,"Plasma",1,java.time.LocalDate.now(),null));
  mvc.perform(get("/donor/appointments").with(user(donor.getEmail()).roles("DONOR")))
    .andExpect(status().isOk()).andExpect(content().string(containsString("My private center")))
    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Other private center"))))
    .andExpect(content().string(containsString("href=\"/donor/history\"")));
  mvc.perform(get("/donor/history").with(user(donor.getEmail()).roles("DONOR")))
    .andExpect(status().isOk()).andExpect(content().string(containsString("Whole Blood")))
    .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Plasma"))));
 }

 @Test void donorCannotCancelAnotherDonorsAppointment() throws Exception {
  User owner=users.save(new User("Appointment Owner","appointment.owner@example.test",encoder.encode("local-audit-password"),"DONOR","","","B+"));
  User attacker=users.save(new User("Different Donor","different.donor@example.test",encoder.encode("local-audit-password"),"DONOR","","","A-"));
  DonationAppointment appointment=appointments.save(new DonationAppointment(owner,java.time.LocalDate.now().plusDays(2)));
  mvc.perform(post("/donor/appointments/{id}/cancel",appointment.getId()).with(csrf()).with(user(attacker.getEmail()).roles("DONOR")))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/donor/appointments"));
  org.junit.jupiter.api.Assertions.assertEquals("PENDING",appointments.findById(appointment.getId()).orElseThrow().getStatus());
 }

 @Test void donorProfileUpdatesOnlyTheAuthenticatedUsersSupportedFields() throws Exception {
  User donor=users.save(new User("Profile Donor","profile.donor@example.test",encoder.encode("local-audit-password"),"DONOR","","","A+"));
  mvc.perform(get("/donor/profile").with(user(donor.getEmail()).roles("DONOR"))).andExpect(status().isOk()).andExpect(content().string(containsString("Contact and donor details")));
  mvc.perform(post("/donor/profile").with(csrf()).with(user(donor.getEmail()).roles("DONOR"))
      .param("name","Updated Donor").param("bloodGroup","AB+").param("phone","555-0112"))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/profile"));
  User updated=users.findByEmailIgnoreCase(donor.getEmail()).orElseThrow();
  org.junit.jupiter.api.Assertions.assertEquals("Updated Donor",updated.getName());
  org.junit.jupiter.api.Assertions.assertEquals("AB+",updated.getBloodGroup());
  org.junit.jupiter.api.Assertions.assertEquals("555-0112",updated.getPhone());
 }

 @Test void donorBookingRejectsPastDatesWithoutCreatingAnAppointment() throws Exception {
  User donor=users.save(new User("Date Check Donor","date.check@example.test",encoder.encode("local-audit-password"),"DONOR","","","O+"));
  mvc.perform(post("/donor/appointments").with(csrf()).with(user(donor.getEmail()).roles("DONOR"))
      .param("requestedDate",java.time.LocalDate.now().minusDays(1).toString()).param("requestedTime","10:00").param("location","Test center"))
    .andExpect(status().is3xxRedirection());
  org.junit.jupiter.api.Assertions.assertTrue(appointments.findByDonorOrderByRequestedDateDesc(donor).isEmpty());
 }

 @Test void adminIssueRouteConsumesReleasedStockAndRecordsTransaction() throws Exception {
  User hospital=users.save(new User("Issue Route Clinic","issue-route@example.test",encoder.encode("local-audit-password"),"HOSPITAL","Issue Route Clinic",""));
  BloodRequest request=new BloodRequest(hospital,"O+","Whole Blood",1,"ROUTINE","ISSUE-ROUTE-TEST","fixture");request.setStatus("APPROVED");requests.save(request);
  BloodUnit unit=new BloodUnit();unit.setBatchId("ISSUE-ROUTE-BATCH");unit.setBloodGroup("O+");unit.setComponent("Whole Blood");unit.setCollectionDate(java.time.LocalDate.now());unit.setExpiryDate(java.time.LocalDate.now().plusDays(20));unit.setStatus("RELEASED");unit.setQuantity(1);units.save(unit);
  mvc.perform(post("/admin/requests/{id}/issue",request.getId()).with(csrf()).with(user("audit-admin").roles("ADMIN")))
    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/requests")).andExpect(flash().attribute("success","Blood issued and inventory updated."));
  org.junit.jupiter.api.Assertions.assertEquals("ISSUED",requests.findById(request.getId()).orElseThrow().getStatus());
  org.junit.jupiter.api.Assertions.assertEquals(0,units.findAllByOrderByExpiryDateAsc().stream().filter(u->u.getBatchId().equals("ISSUE-ROUTE-BATCH")).findFirst().orElseThrow().getQuantity());
  org.junit.jupiter.api.Assertions.assertEquals(1,issues.findByRequest(request).orElseThrow().getQuantity());
 }
}
