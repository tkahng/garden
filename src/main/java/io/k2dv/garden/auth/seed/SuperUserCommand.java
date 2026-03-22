package io.k2dv.garden.auth.seed;

import io.k2dv.garden.auth.model.Identity;
import io.k2dv.garden.auth.model.IdentityProvider;
import io.k2dv.garden.auth.repository.IdentityRepository;
import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.iam.service.IamService;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.model.UserStatus;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@Profile("!test | integration-seed")
@RequiredArgsConstructor
@Slf4j
public class SuperUserCommand implements ApplicationRunner {

    private final UserRepository userRepo;
    private final IdentityRepository identityRepo;
    private final IamService iamService;
    private final AppProperties props;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        run();
    }

    @Transactional
    public void run() {
        String email = props.getSuperuser().getEmail();
        if (userRepo.existsByEmail(email)) {
            log.debug("Superuser {} already exists — skipping seed", email);
            return;
        }

        log.info("Seeding OWNER account: {}", email);

        var user = new User();
        user.setEmail(email);
        user.setFirstName("Owner");
        user.setLastName("Admin");
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now());
        user = userRepo.save(user);

        var identity = new Identity();
        identity.setUserId(user.getId());
        identity.setProvider(IdentityProvider.CREDENTIALS);
        identity.setAccountId(user.getId().toString());
        identity.setPasswordHash(passwordEncoder.encode(props.getSuperuser().getPassword()));
        identityRepo.save(identity);

        iamService.assignRoleByName(user.getId(), "OWNER");
        log.info("OWNER account seeded successfully");
    }
}
