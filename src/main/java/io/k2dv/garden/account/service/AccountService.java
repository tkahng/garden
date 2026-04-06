package io.k2dv.garden.account.service;

import io.k2dv.garden.account.dto.AccountResponse;
import io.k2dv.garden.account.dto.AddressRequest;
import io.k2dv.garden.account.dto.AddressResponse;
import io.k2dv.garden.account.dto.UpdateAccountRequest;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.model.Address;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.AddressRepository;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepo;
    private final AddressRepository addressRepo;

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID userId) {
        return AccountResponse.from(findUser(userId));
    }

    @Transactional
    public AccountResponse updateAccount(UUID userId, UpdateAccountRequest req) {
        User user = findUser(userId);
        if (req.firstName() != null) user.setFirstName(req.firstName());
        if (req.lastName() != null) user.setLastName(req.lastName());
        if (req.phone() != null) user.setPhone(req.phone());
        return AccountResponse.from(userRepo.save(user));
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> listAddresses(UUID userId) {
        return addressRepo.findByUserId(userId).stream()
            .map(AddressResponse::from)
            .toList();
    }

    @Transactional
    public AddressResponse createAddress(UUID userId, AddressRequest req) {
        findUser(userId); // verify user exists
        if (req.isDefault()) {
            clearDefault(userId);
        }
        Address address = new Address();
        address.setUser(userRepo.getReferenceById(userId));
        populateAddress(address, req);
        return AddressResponse.from(addressRepo.save(address));
    }

    @Transactional
    public AddressResponse updateAddress(UUID userId, UUID addressId, AddressRequest req) {
        Address address = findAddress(addressId);
        verifyOwnership(userId, address);
        if (req.isDefault()) {
            clearDefault(userId);
        }
        populateAddress(address, req);
        return AddressResponse.from(addressRepo.save(address));
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        Address address = findAddress(addressId);
        verifyOwnership(userId, address);
        addressRepo.delete(address);
    }

    private User findUser(UUID userId) {
        return userRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    }

    private Address findAddress(UUID addressId) {
        return addressRepo.findById(addressId)
            .orElseThrow(() -> new NotFoundException("ADDRESS_NOT_FOUND", "Address not found"));
    }

    private void verifyOwnership(UUID userId, Address address) {
        if (!address.getUser().getId().equals(userId)) {
            throw new ForbiddenException("ACCESS_DENIED", "Address does not belong to this user");
        }
    }

    private void clearDefault(UUID userId) {
        addressRepo.findByUserIdAndIsDefaultTrue(userId).ifPresent(a -> {
            a.setDefault(false);
            addressRepo.save(a);
        });
    }

    private void populateAddress(Address address, AddressRequest req) {
        address.setFirstName(req.firstName());
        address.setLastName(req.lastName());
        address.setCompany(req.company());
        address.setAddress1(req.address1());
        address.setAddress2(req.address2());
        address.setCity(req.city());
        address.setProvince(req.province());
        address.setZip(req.zip());
        address.setCountry(req.country());
        address.setDefault(req.isDefault());
    }
}
