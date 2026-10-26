package com.bbms.web;
import jakarta.validation.constraints.*;

public class RegistrationForm {
 @NotBlank(message="Enter your name.") @Size(max=100,message="Name must be 100 characters or fewer.") private String name;
 @NotBlank(message="Enter your email address.") @Email(message="Enter a valid email address.") @Size(max=254) private String email;
 @NotBlank(message="Enter a password.") @Size(min=10,max=72,message="Use a password between 10 and 72 characters.") private String password;
 @Pattern(regexp="DONOR|HOSPITAL",message="Choose Donor or Hospital / Requester.") private String role="DONOR";
 @Size(max=150,message="Organization name must be 150 characters or fewer.") private String organization;
 @Size(max=40,message="Phone number must be 40 characters or fewer.") private String phone;
 @Pattern(regexp="|A\\+|A-|B\\+|B-|AB\\+|AB-|O\\+|O-",message="Choose a supported blood group.") private String bloodGroup;
 public String getName(){return name;} public void setName(String v){name=v;} public String getEmail(){return email;} public void setEmail(String v){email=v;} public String getPassword(){return password;} public void setPassword(String v){password=v;} public String getRole(){return role;} public void setRole(String v){role=v;} public String getOrganization(){return organization;} public void setOrganization(String v){organization=v;} public String getPhone(){return phone;} public void setPhone(String v){phone=v;} public String getBloodGroup(){return bloodGroup;} public void setBloodGroup(String v){bloodGroup=v;}
}
