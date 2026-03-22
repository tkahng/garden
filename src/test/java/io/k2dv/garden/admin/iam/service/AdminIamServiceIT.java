package io.k2dv.garden.admin.iam.service;

import io.k2dv.garden.admin.iam.dto.CreateRoleRequest;
import io.k2dv.garden.admin.iam.dto.AssignPermissionRequest;
import io.k2dv.garden.iam.repository.PermissionRepository;
import io.k2dv.garden.iam.repository.RoleRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminIamServiceIT extends AbstractIntegrationTest {

    @Autowired AdminIamService adminIamService;
    @Autowired RoleRepository roleRepo;
    @Autowired PermissionRepository permissionRepo;

    @Test
    void listRoles_returnsAllSeededRoles() {
        var roles = adminIamService.listRoles();
        assertThat(roles).extracting("name")
            .contains("CUSTOMER", "STAFF", "MANAGER", "OWNER");
    }

    @Test
    void createRole_thenListContainsIt() {
        adminIamService.createRole(new CreateRoleRequest("ANALYST", "Read-only analyst"));
        var roles = adminIamService.listRoles();
        assertThat(roles).extracting("name").contains("ANALYST");
    }

    @Test
    void createRole_duplicateName_throwsConflict() {
        adminIamService.createRole(new CreateRoleRequest("TESTER", null));
        assertThatThrownBy(() -> adminIamService.createRole(new CreateRoleRequest("TESTER", null)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteRole_predefined_throwsForbidden() {
        var owner = roleRepo.findByName("OWNER").orElseThrow();
        assertThatThrownBy(() -> adminIamService.deleteRole(owner.getId()))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteRole_custom_succeeds() {
        var role = adminIamService.createRole(new CreateRoleRequest("CUSTOM_DELETE", null));
        adminIamService.deleteRole(role.id());
        assertThat(roleRepo.findByName("CUSTOM_DELETE")).isEmpty();
    }

    @Test
    void assignAndRemovePermission() {
        var role = adminIamService.createRole(new CreateRoleRequest("CUSTOM_PERMS", null));
        var perm = permissionRepo.findAll().stream()
            .filter(p -> p.getName().equals("product:read"))
            .findFirst().orElseThrow();

        var updated = adminIamService.assignPermission(role.id(), new AssignPermissionRequest(perm.getId()));
        assertThat(updated.permissions()).extracting("name").contains("product:read");

        adminIamService.removePermission(role.id(), perm.getId());
        var afterRemove = adminIamService.listRoles().stream()
            .filter(r -> r.name().equals("CUSTOM_PERMS")).findFirst().orElseThrow();
        assertThat(afterRemove.permissions()).extracting("name").doesNotContain("product:read");
    }

    @Test
    void listPermissions_returns14() {
        var perms = adminIamService.listPermissions();
        assertThat(perms).hasSize(14);
    }
}
