package io.k2dv.garden.iam.service;

import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;

class IamServiceIT extends AbstractIntegrationTest {

    @Autowired IamService iamService;
    @Autowired UserRepository userRepo;
    @Autowired CacheManager cacheManager;

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
    void loadPermissionsForUser_resultIsCached() {
        var user = savedUser("cached@test.com");
        iamService.assignRoleByName(user.getId(), "CUSTOMER");

        var first  = iamService.loadPermissionsForUser(user.getId());
        var second = iamService.loadPermissionsForUser(user.getId());

        assertThat(first).isSameAs(second); // same list instance from cache
        var cached = cacheManager.getCache("permissions").get(user.getId());
        assertThat(cached).isNotNull();
    }

    @Test
    void assignRoleByName_evictsPermissionCache() {
        var user = savedUser("evict@test.com");
        iamService.assignRoleByName(user.getId(), "CUSTOMER");
        iamService.loadPermissionsForUser(user.getId()); // populate cache

        iamService.assignRoleByName(user.getId(), "STAFF"); // should evict

        var cached = cacheManager.getCache("permissions").get(user.getId());
        assertThat(cached).isNull();
    }

    @Test
    void ownerRole_expandsToAllPermissions() {
        var user = savedUser("o@test.com");
        iamService.assignRoleByName(user.getId(), "OWNER");

        var perms = iamService.loadPermissionsForUser(user.getId());

        assertThat(perms).contains("product:read", "product:write", "iam:manage", "staff:manage");
        assertThat(perms).hasSize(52); // 46 existing + 3 webhook (V40) + 2 return (V56)
    }
}
