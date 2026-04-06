package io.k2dv.garden.account.service;

import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.account.dto.AddressRequest;
import io.k2dv.garden.account.dto.UpdateAccountRequest;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountServiceIT extends AbstractIntegrationTest {

    @Autowired AccountService accountService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @MockitoBean EmailService emailService;

    UUID userId;

    @BeforeEach
    void setup() {
        authService.register(new RegisterRequest("acct@example.com", "password1", "Jane", "Doe"));
        userId = userRepo.findByEmail("acct@example.com").orElseThrow().getId();
    }

    @Test
    void getAccount_returnsProfile() {
        var resp = accountService.getAccount(userId);
        assertThat(resp.email()).isEqualTo("acct@example.com");
        assertThat(resp.firstName()).isEqualTo("Jane");
    }

    @Test
    void updateAccount_changesFields() {
        var req = new UpdateAccountRequest("Janet", "Smith", "+1234567890");
        var resp = accountService.updateAccount(userId, req);
        assertThat(resp.firstName()).isEqualTo("Janet");
        assertThat(resp.lastName()).isEqualTo("Smith");
        assertThat(resp.phone()).isEqualTo("+1234567890");
    }

    @Test
    void createAddress_setsDefault_clearsOld() {
        accountService.createAddress(userId,
            new AddressRequest("Jane", "Doe", null, "1 Main St", null, "Portland", null, "97201", "US", true));
        accountService.createAddress(userId,
            new AddressRequest("Jane", "Doe", null, "2 Oak Ave", null, "Portland", null, "97202", "US", true));

        var addresses = accountService.listAddresses(userId);
        long defaultCount = addresses.stream().filter(a -> a.isDefault()).count();
        assertThat(defaultCount).isEqualTo(1);
        assertThat(addresses.stream().filter(a -> a.isDefault()).findFirst().get().address1())
            .isEqualTo("2 Oak Ave");
    }

    @Test
    void deleteAddress_ownershipCheck() {
        var other = new io.k2dv.garden.user.model.User();
        other.setEmail("other@example.com");
        other.setFirstName("X");
        other.setLastName("Y");
        var otherId = userRepo.saveAndFlush(other).getId();

        var addr = accountService.createAddress(userId,
            new AddressRequest("Jane", "Doe", null, "1 Main St", null, "Portland", null, "97201", "US", false));

        assertThatThrownBy(() -> accountService.deleteAddress(otherId, addr.id()))
            .isInstanceOf(ForbiddenException.class);
    }
}
