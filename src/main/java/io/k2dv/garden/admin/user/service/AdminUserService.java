package io.k2dv.garden.admin.user.service;

import io.k2dv.garden.admin.user.dto.AdminUserResponse;
import io.k2dv.garden.admin.user.dto.UpdateUserRequest;
import io.k2dv.garden.admin.user.dto.UserFilter;
import io.k2dv.garden.iam.service.IamService;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.model.UserStatus;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepo;
    private final IamService iamService;

    @Transactional(readOnly = true)
    public PagedResult<AdminUserResponse> listUsers(UserFilter filter, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (filter.status() != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (filter.email() != null && !filter.email().isBlank()) {
            spec = spec.and((root, query, cb) ->
                cb.like(cb.lower(root.get("email")), "%" + filter.email().toLowerCase() + "%"));
        }
        Page<User> page = userRepo.findAll(spec, pageable);
        List<AdminUserResponse> content = page.getContent().stream()
            .map(u -> AdminUserResponse.from(u, userRepo.findRoleNamesByUserId(u.getId())))
            .toList();
        Page<AdminUserResponse> mapped = new PageImpl<>(content, pageable, page.getTotalElements());
        return PagedResult.of(mapped);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID id) {
        User user = findUser(id);
        return AdminUserResponse.from(user, userRepo.findRoleNamesByUserId(id));
    }

    @Transactional
    public AdminUserResponse updateUser(UUID id, UpdateUserRequest req) {
        User user = findUser(id);
        if (req.firstName() != null) user.setFirstName(req.firstName());
        if (req.lastName() != null) user.setLastName(req.lastName());
        if (req.phone() != null) user.setPhone(req.phone());
        if (req.email() != null) user.setEmail(req.email());
        user = userRepo.save(user);
        return AdminUserResponse.from(user, userRepo.findRoleNamesByUserId(id));
    }

    @Transactional
    public void suspendUser(UUID id) {
        User user = findUser(id);
        user.setStatus(UserStatus.SUSPENDED);
        userRepo.save(user);
    }

    @Transactional
    public void reactivateUser(UUID id) {
        User user = findUser(id);
        user.setStatus(UserStatus.ACTIVE);
        userRepo.save(user);
    }

    @Transactional
    public void assignRole(UUID userId, String roleName) {
        iamService.assignRoleByName(userId, roleName);
    }

    @Transactional
    public void removeRole(UUID userId, String roleName) {
        iamService.removeRoleByName(userId, roleName);
    }

    private User findUser(UUID id) {
        return userRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
    }
}
