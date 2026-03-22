package io.k2dv.garden.admin.user.service;

import io.k2dv.garden.admin.user.dto.UserFilter;
import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.user.model.UserStatus;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUserServiceIT extends AbstractIntegrationTest {

    @Autowired AdminUserService adminUserService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @MockitoBean EmailService emailService;

    UUID userId;

    @BeforeEach
    void setup() {
        authService.register(new RegisterRequest("admin-test@example.com", "password1", "Test", "User"));
        userId = userRepo.findByEmail("admin-test@example.com").orElseThrow().getId();
    }

    @Test
    void listUsers_returnsPage() {
        var page = adminUserService.listUsers(new UserFilter(null, null), PageRequest.of(0, 20));
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getMeta().getTotal()).isGreaterThan(0);
    }

    @Test
    void listUsers_filterByEmail_returnsMatch() {
        var page = adminUserService.listUsers(new UserFilter(null, "admin-test"), PageRequest.of(0, 20));
        assertThat(page.getContent()).anyMatch(u -> u.email().equals("admin-test@example.com"));
    }

    @Test
    void getUser_returnsWithRoles() {
        var user = adminUserService.getUser(userId);
        assertThat(user.email()).isEqualTo("admin-test@example.com");
        assertThat(user.roles()).contains("CUSTOMER");
    }

    @Test
    void suspendAndReactivate_changesStatus() {
        adminUserService.suspendUser(userId);
        assertThat(userRepo.findById(userId).get().getStatus()).isEqualTo(UserStatus.SUSPENDED);

        adminUserService.reactivateUser(userId);
        assertThat(userRepo.findById(userId).get().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void assignAndRemoveRole() {
        adminUserService.assignRole(userId, "STAFF");
        var roles = userRepo.findRoleNamesByUserId(userId);
        assertThat(roles).contains("STAFF");

        adminUserService.removeRole(userId, "STAFF");
        var rolesAfter = userRepo.findRoleNamesByUserId(userId);
        assertThat(rolesAfter).doesNotContain("STAFF");
    }
}
