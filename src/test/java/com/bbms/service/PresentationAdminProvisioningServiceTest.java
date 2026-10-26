package com.bbms.service;

import com.bbms.domain.User;
import com.bbms.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresentationAdminProvisioningServiceTest {
    @Mock UserRepository users;

    @Test
    void createsOnlyAnAdminWithBcryptEncodedPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        PresentationAdminProvisioningService service = new PresentationAdminProvisioningService(users, encoder);
        String password = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        when(users.findByEmailIgnoreCase("presentation.admin@example.test")).thenReturn(Optional.empty());
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(PresentationAdminProvisioningService.Result.CREATED,
                service.provisionIfAbsent("Presentation.Admin@example.test", password));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertEquals("ADMIN", saved.getRole());
        assertEquals("presentation.admin@example.test", saved.getEmail());
        assertTrue(saved.isEnabled());
        assertNotEquals(password, saved.getPassword());
        assertTrue(encoder.matches(password, saved.getPassword()));
    }

    @Test
    void secondInvocationLeavesAnExistingAccountUntouched() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        PresentationAdminProvisioningService service = new PresentationAdminProvisioningService(users, encoder);
        User existing = new User("Existing Donor", "presentation.admin@example.test", "existing-value", "DONOR", null, "", "O+");
        when(users.findByEmailIgnoreCase("presentation.admin@example.test")).thenReturn(Optional.of(existing));

        assertEquals(PresentationAdminProvisioningService.Result.ALREADY_EXISTS,
                service.provisionIfAbsent("Presentation.Admin@example.test", "unused-second-password"));

        assertEquals("DONOR", existing.getRole());
        assertEquals("existing-value", existing.getPassword());
        verify(users, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(encoder);
    }

    @Test
    void rejectsPasswordsOutsideTheExistingBcryptByteLimit() {
        var encoder = mock(PasswordEncoder.class);
        PresentationAdminProvisioningService service = new PresentationAdminProvisioningService(users, encoder);
        when(users.findByEmailIgnoreCase("presentation.admin@example.test")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.provisionIfAbsent("presentation.admin@example.test", "short"));
        verify(users, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(encoder);
    }

}
