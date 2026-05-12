package io.k2dv.garden.b2b.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.b2b.dto.CompanyAddressRequest;
import io.k2dv.garden.b2b.dto.CompanyAddressResponse;
import io.k2dv.garden.b2b.dto.CreateCompanyRequest;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyShippingAddressServiceIT extends AbstractIntegrationTest {

    @Autowired CompanyShippingAddressService addressService;
    @Autowired CompanyService companyService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID ownerUserId;
    private UUID memberUserId;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String ownerEmail  = "addr-owner-"  + n + "-" + UUID.randomUUID() + "@example.com";
        String memberEmail = "addr-member-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(ownerEmail,  "password1", "Owner",  "User"));
        authService.register(new RegisterRequest(memberEmail, "password1", "Member", "User"));
        ownerUserId  = userRepo.findByEmail(ownerEmail).orElseThrow().getId();
        memberUserId = userRepo.findByEmail(memberEmail).orElseThrow().getId();

        companyId = companyService.create(ownerUserId,
            new CreateCompanyRequest("Acme Corp", null, null, null, null, null, null, null, null)).id();
        companyService.addMember(companyId, ownerUserId,
            new io.k2dv.garden.b2b.dto.AddMemberRequest(memberEmail, null));
    }

    private CompanyAddressRequest req(String label, boolean isDefault) {
        return new CompanyAddressRequest(label, "Jane", "Doe", "Acme",
            "123 Main St", null, "Springfield", "IL", "62701", "US", isDefault);
    }

    @Test
    void add_thenList_containsAddress() {
        addressService.add(companyId, ownerUserId, req("HQ", false));

        List<CompanyAddressResponse> list = addressService.list(companyId, ownerUserId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).label()).isEqualTo("HQ");
        assertThat(list.get(0).city()).isEqualTo("Springfield");
        assertThat(list.get(0).isDefault()).isFalse();
    }

    @Test
    void add_withDefault_setsIsDefault() {
        CompanyAddressResponse addr = addressService.add(companyId, ownerUserId, req("Warehouse", true));
        assertThat(addr.isDefault()).isTrue();
    }

    @Test
    void add_normalizesCountryCode() {
        CompanyAddressResponse addr = addressService.add(companyId, ownerUserId,
            new CompanyAddressRequest("HQ", "Jane", "Doe", "Acme",
                "123 Main St", null, "Springfield", "IL", "62701", " us ", false));

        assertThat(addr.country()).isEqualTo("US");
    }

    @Test
    void update_rejectsIso3CountryCode() {
        CompanyAddressResponse addr = addressService.add(companyId, ownerUserId, req("HQ", false));

        assertThatThrownBy(() -> addressService.update(companyId, addr.id(), ownerUserId,
            new CompanyAddressRequest("HQ", "Jane", "Doe", "Acme",
                "123 Main St", null, "Springfield", "IL", "62701", "USA", false)))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode")
            .isEqualTo("INVALID_COUNTRY_CODE");
    }

    @Test
    void add_secondDefault_clearsFirstDefault() {
        addressService.add(companyId, ownerUserId, req("First", true));
        addressService.add(companyId, ownerUserId, req("Second", true));

        List<CompanyAddressResponse> list = addressService.list(companyId, ownerUserId);
        assertThat(list).hasSize(2);
        long defaultCount = list.stream().filter(CompanyAddressResponse::isDefault).count();
        assertThat(defaultCount).isEqualTo(1);
        assertThat(list.stream().filter(CompanyAddressResponse::isDefault)
            .map(CompanyAddressResponse::label).findFirst()).contains("Second");
    }

    @Test
    void list_memberCanRead() {
        addressService.add(companyId, ownerUserId, req("HQ", false));
        List<CompanyAddressResponse> list = addressService.list(companyId, memberUserId);
        assertThat(list).hasSize(1);
    }

    @Test
    void add_memberCannotAdd_throws403() {
        assertThatThrownBy(() -> addressService.add(companyId, memberUserId, req("HQ", false)))
            .isInstanceOf(ForbiddenException.class)
            .hasMessageContaining("owner or manager");
    }

    @Test
    void update_changesFields() {
        CompanyAddressResponse addr = addressService.add(companyId, ownerUserId, req("Old", false));
        CompanyAddressRequest updated = new CompanyAddressRequest(
            "New", "John", "Smith", "Acme", "456 Oak Ave", null, "Chicago", "IL", "60601", "US", false);

        CompanyAddressResponse result = addressService.update(companyId, addr.id(), ownerUserId, updated);

        assertThat(result.label()).isEqualTo("New");
        assertThat(result.firstName()).isEqualTo("John");
        assertThat(result.address1()).isEqualTo("456 Oak Ave");
    }

    @Test
    void update_wrongCompany_throws404() {
        UUID otherCompanyId = companyService.create(ownerUserId,
            new CreateCompanyRequest("Other Co", null, null, null, null, null, null, null, null)).id();
        CompanyAddressResponse addr = addressService.add(companyId, ownerUserId, req("HQ", false));

        // owner passes member+role check for otherCompany, but address doesn't belong to it
        assertThatThrownBy(() -> addressService.update(otherCompanyId, addr.id(), ownerUserId, req("HQ", false)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_removesAddress() {
        CompanyAddressResponse addr = addressService.add(companyId, ownerUserId, req("HQ", false));
        addressService.delete(companyId, addr.id(), ownerUserId);
        assertThat(addressService.list(companyId, ownerUserId)).isEmpty();
    }

    @Test
    void delete_unknownAddress_throws404() {
        assertThatThrownBy(() -> addressService.delete(companyId, UUID.randomUUID(), ownerUserId))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void setDefault_promotesAddress() {
        CompanyAddressResponse first  = addressService.add(companyId, ownerUserId, req("First",  true));
        CompanyAddressResponse second = addressService.add(companyId, ownerUserId, req("Second", false));

        addressService.setDefault(companyId, second.id(), ownerUserId);

        List<CompanyAddressResponse> list = addressService.list(companyId, ownerUserId);
        assertThat(list.stream().filter(CompanyAddressResponse::isDefault).map(CompanyAddressResponse::label).findFirst())
            .contains("Second");
        assertThat(list.stream().filter(a -> a.label().equals("First")).findFirst().orElseThrow().isDefault())
            .isFalse();
    }

    @Test
    void list_defaultAddressReturnedFirst() {
        addressService.add(companyId, ownerUserId, req("Non-default", false));
        addressService.add(companyId, ownerUserId, req("Default", true));

        List<CompanyAddressResponse> list = addressService.list(companyId, ownerUserId);
        assertThat(list.get(0).isDefault()).isTrue();
    }
}
