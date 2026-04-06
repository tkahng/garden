package io.k2dv.garden.iam.service;

import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class IamServiceIT extends AbstractIntegrationTest {

    @Autowired IamService iamService;
    @Autowired UserRepository userRepo;

    private User savedUser(String email) {
        var u = new User();
        u.setEmail(email);
        u.setFirstName("Test");
        u.setLastName("User");
        return userRepo.saveAndFlush(u);
    }

    @Test
    void customerRole_hasProductReadAndContentRead() {
        var user = savedUser("c@test.com");
        iamService.assignRoleByName(user.getId(), "CUSTOMER");

        var perms = iamService.loadPermissionsForUser(user.getId());

        assertThat(perms).contains("product:read", "content:read");
        assertThat(perms).doesNotContain("product:write", "iam:manage");
    }

    @Test
    void ownerRole_expandsToAllPermissions() {
        var user = savedUser("o@test.com");
        iamService.assignRoleByName(user.getId(), "OWNER");

        var perms = iamService.loadPermissionsForUser(user.getId());

        assertThat(perms).contains("product:read", "product:write", "iam:manage", "staff:manage");
        assertThat(perms).hasSize(22); // all 22 seeded permissions (16 base + 2 location from V12 + 4 collection from V13)
    }
}
