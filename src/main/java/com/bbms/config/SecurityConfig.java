package com.bbms.config;

import com.bbms.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean UserDetailsService userDetailsService(UserRepository users,
                                                 @Value("${bbms.training.mode:false}") boolean trainingMode) {
        return username -> users.findByEmailIgnoreCase(username)
                .filter(account -> trainingMode || !account.isDemoData())
                .map(account -> User.withUsername(account.getEmail()).password(account.getPassword())
                        .roles(account.getRole()).disabled(!account.isEnabled()).build())
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }

    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.requestMatchers("/", "/login", "/register", "/css/**", "/js/**", "/error/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/donor/**").hasRole("DONOR")
                        .requestMatchers("/hospital/**").hasRole("HOSPITAL")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.accessDeniedPage("/error/403"))
                .formLogin(f -> f.loginPage("/login").defaultSuccessUrl("/dashboard", true).permitAll())
                .logout(l -> l.logoutSuccessUrl("/").permitAll()).build();
    }
}
