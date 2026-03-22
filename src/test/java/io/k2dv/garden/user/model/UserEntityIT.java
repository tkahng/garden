package io.k2dv.garden.user.model;

import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntityIT extends AbstractIntegrationTest {

    @Autowired
    UserRepository userRepo;

    @Test
    void savedUser_hasUuidV7AndTimestamps() {
        var user = new User();
        user.setEmail("test@example.com");
        user.setFirstName("Alice");
        user.setLastName("Smith");

        var saved = userRepo.saveAndFlush(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId().toString().charAt(14)).isEqualTo('7');
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(UserStatus.UNVERIFIED);
    }
}
