package io.k2dv.garden.auth.seed;

import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.user.model.UserStatus;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// Opt out of the test profile so the runner executes in this specific test
@ActiveProfiles("integration-seed")
class SuperUserCommandIT extends AbstractIntegrationTest {

    @Autowired UserRepository userRepo;
    @Autowired SuperUserCommand superUserCommand;

    @Test
    void run_createsOwnerUserIfAbsent() throws Exception {
        superUserCommand.run();

        var owner = userRepo.findByEmail("owner@test.local");
        assertThat(owner).isPresent();
        assertThat(owner.get().getStatus()).isEqualTo(UserStatus.ACTIVE);

        var roleNames = userRepo.findRoleNamesByUserId(owner.get().getId());
        assertThat(roleNames).contains("OWNER");
    }

    @Test
    void run_isIdempotent() throws Exception {
        superUserCommand.run();
        superUserCommand.run();

        long count = userRepo.findAll().stream()
            .filter(u -> u.getEmail().equals("owner@test.local"))
            .count();
        assertThat(count).isEqualTo(1);
    }
}
