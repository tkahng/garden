package io.k2dv.garden.b2b.service;

import io.k2dv.garden.b2b.dto.CompanyAddressRequest;
import io.k2dv.garden.b2b.dto.CompanyAddressResponse;
import io.k2dv.garden.b2b.model.CompanyShippingAddress;
import io.k2dv.garden.b2b.repository.CompanyRepository;
import io.k2dv.garden.b2b.repository.CompanyShippingAddressRepository;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyShippingAddressService {

    private final CompanyShippingAddressRepository addressRepo;
    private final CompanyRepository companyRepo;
    private final CompanyService companyService;

    @Transactional(readOnly = true)
    public List<CompanyAddressResponse> list(UUID companyId, UUID userId) {
        companyService.requireMemberAccess(companyId, userId);
        return addressRepo.findByCompanyIdOrderByIsDefaultDescCreatedAtAsc(companyId)
            .stream().map(CompanyAddressResponse::from).toList();
    }

    @Transactional
    public CompanyAddressResponse add(UUID companyId, UUID userId, CompanyAddressRequest req) {
        requireOwnerOrManager(companyId, userId);
        assertCompanyExists(companyId);

        if (req.isDefault()) {
            addressRepo.clearDefaultForCompany(companyId);
        }

        CompanyShippingAddress address = new CompanyShippingAddress();
        apply(address, companyId, req);
        return CompanyAddressResponse.from(addressRepo.save(address));
    }

    @Transactional
    public CompanyAddressResponse update(UUID companyId, UUID addressId, UUID userId, CompanyAddressRequest req) {
        requireOwnerOrManager(companyId, userId);
        CompanyShippingAddress address = requireAddress(addressId, companyId);

        if (req.isDefault() && !address.isDefault()) {
            addressRepo.clearDefaultForCompany(companyId);
        }

        apply(address, companyId, req);
        return CompanyAddressResponse.from(addressRepo.save(address));
    }

    @Transactional
    public void delete(UUID companyId, UUID addressId, UUID userId) {
        requireOwnerOrManager(companyId, userId);
        CompanyShippingAddress address = requireAddress(addressId, companyId);
        addressRepo.delete(address);
    }

    @Transactional
    public CompanyAddressResponse setDefault(UUID companyId, UUID addressId, UUID userId) {
        requireOwnerOrManager(companyId, userId);
        // validate address belongs to company before clearing — throws 404 if not found
        requireAddress(addressId, companyId);
        addressRepo.clearDefaultForCompany(companyId); // clears JPA cache via clearAutomatically
        CompanyShippingAddress address = addressRepo.findByIdAndCompanyId(addressId, companyId)
            .orElseThrow(() -> new NotFoundException("ADDRESS_NOT_FOUND", "Shipping address not found"));
        address.setDefault(true);
        return CompanyAddressResponse.from(addressRepo.save(address));
    }

    private void apply(CompanyShippingAddress a, UUID companyId, CompanyAddressRequest req) {
        a.setCompanyId(companyId);
        a.setLabel(req.label());
        a.setFirstName(req.firstName());
        a.setLastName(req.lastName());
        a.setCompany(req.company());
        a.setAddress1(req.address1());
        a.setAddress2(req.address2());
        a.setCity(req.city());
        a.setProvince(req.province());
        a.setZip(req.zip());
        a.setCountry(req.country());
        a.setDefault(req.isDefault());
    }

    private CompanyShippingAddress requireAddress(UUID addressId, UUID companyId) {
        return addressRepo.findByIdAndCompanyId(addressId, companyId)
            .orElseThrow(() -> new NotFoundException("ADDRESS_NOT_FOUND", "Shipping address not found"));
    }

    private void assertCompanyExists(UUID companyId) {
        if (!companyRepo.existsById(companyId)) {
            throw new NotFoundException("COMPANY_NOT_FOUND", "Company not found");
        }
    }

    private void requireOwnerOrManager(UUID companyId, UUID userId) {
        companyService.requireMemberAccess(companyId, userId);
        if (!companyService.isOwnerOrManager(companyId, userId)) {
            throw new ForbiddenException("INSUFFICIENT_COMPANY_ROLE",
                "Only a company owner or manager can manage shipping addresses");
        }
    }
}
