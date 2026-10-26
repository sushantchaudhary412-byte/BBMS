package com.bbms.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Entity @Table(name="app_users")
public class User {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @NotBlank @Size(max=100) private String name;
    @Email @NotBlank @Column(unique=true) private String email;
    @NotBlank private String password;
    @NotBlank private String role;
    @Size(max=150) private String organization;
    @Size(max=40) private String phone;
    @Size(max=3) private String bloodGroup;
    private boolean enabled = true;
    @Column(nullable=false,columnDefinition="bit(1) default 0") private boolean demoData = false;
    @Column(length=80,unique=true) private String demoKey;
    private LocalDateTime createdAt = LocalDateTime.now();
    protected User() {}
    public User(String name,String email,String password,String role,String organization,String phone){this(name,email,password,role,organization,phone,null);}
    public User(String name,String email,String password,String role,String organization,String phone,String bloodGroup){this.name=name;this.email=email.toLowerCase();this.password=password;this.role=role;this.organization=organization;this.phone=phone;this.bloodGroup=bloodGroup;}
    public Long getId(){return id;} public String getName(){return name;} public String getEmail(){return email;} public String getPassword(){return password;} public String getRole(){return role;} public String getOrganization(){return organization;} public String getPhone(){return phone;} public String getBloodGroup(){return bloodGroup;} public LocalDateTime getCreatedAt(){return createdAt;} public boolean isEnabled(){return enabled;} public void toggleEnabled(){enabled=!enabled;} public boolean isDemoData(){return demoData;} public String getDemoKey(){return demoKey;} public void setDemoData(boolean v){demoData=v;} public void setDemoKey(String v){demoKey=v;}
    public void updateProfile(String name,String organization,String phone,String bloodGroup){this.name=name;this.organization=organization;this.phone=phone;this.bloodGroup=bloodGroup;}
}
