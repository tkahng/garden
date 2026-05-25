package io.k2dv.garden.b2b.service;

import io.k2dv.garden.b2b.dto.AssignDepartmentRequest;
import io.k2dv.garden.b2b.dto.DepartmentRequest;
import io.k2dv.garden.b2b.dto.DepartmentResponse;
import io.k2dv.garden.b2b.model.Department;
import io.k2dv.garden.b2b.repository.CompanyMembershipRepository;
import io.k2dv.garden.b2b.repository.DepartmentRepository;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository deptRepo;
    private final CompanyMembershipRepository membershipRepo;

    @Transactional(readOnly = true)
    public List<DepartmentResponse> tree(UUID companyId) {
        List<Department> all = deptRepo.findByCompanyId(companyId);
        List<Department> roots = all.stream().filter(d -> d.getParentId() == null).toList();
        return roots.stream().map(r -> buildTree(r, all)).toList();
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listFlat(UUID companyId) {
        return deptRepo.findByCompanyId(companyId).stream().map(d -> toResponse(d, List.of())).toList();
    }

    @Transactional
    public DepartmentResponse create(UUID companyId, DepartmentRequest req) {
        if (deptRepo.existsByCompanyIdAndName(companyId, req.name())) {
            throw new ConflictException("DEPARTMENT_NAME_TAKEN", "A department with this name already exists");
        }
        if (req.parentId() != null) {
            deptRepo.findById(req.parentId())
                .filter(p -> p.getCompanyId().equals(companyId))
                .orElseThrow(() -> new NotFoundException("PARENT_NOT_FOUND", "Parent department not found"));
        }
        Department dept = new Department();
        dept.setCompanyId(companyId);
        dept.setName(req.name());
        dept.setParentId(req.parentId());
        return toResponse(deptRepo.save(dept), List.of());
    }

    @Transactional
    public DepartmentResponse rename(UUID deptId, UUID companyId, DepartmentRequest req) {
        Department dept = requireOwned(deptId, companyId);

        // Check name uniqueness (same rule as create)
        if (!dept.getName().equals(req.name()) && deptRepo.existsByCompanyIdAndName(companyId, req.name())) {
            throw new ConflictException("DEPARTMENT_NAME_TAKEN", "A department with this name already exists");
        }

        dept.setName(req.name());
        if (req.parentId() != null) {
            if (req.parentId().equals(deptId)) {
                throw new ConflictException("DEPARTMENT_CYCLE", "A department cannot be its own parent");
            }
            deptRepo.findById(req.parentId())
                .filter(p -> p.getCompanyId().equals(companyId))
                .orElseThrow(() -> new NotFoundException("PARENT_NOT_FOUND", "Parent department not found"));
            // Guard against cycles by walking ancestors of the proposed parent
            if (isDescendant(deptId, req.parentId())) {
                throw new ConflictException("DEPARTMENT_CYCLE",
                    "Setting this parent would create a cycle in the department tree");
            }
            dept.setParentId(req.parentId());
        }
        return toResponse(deptRepo.save(dept), List.of());
    }

    /** Returns true if {@code candidateAncestorId} is a descendant of {@code rootId}. */
    private boolean isDescendant(UUID rootId, UUID candidateAncestorId) {
        Set<UUID> visited = new HashSet<>();
        UUID current = candidateAncestorId;
        while (current != null && visited.add(current)) {
            if (current.equals(rootId)) return true;
            current = deptRepo.findById(current).map(Department::getParentId).orElse(null);
        }
        return false;
    }

    @Transactional
    public void delete(UUID deptId, UUID companyId) {
        Department dept = requireOwned(deptId, companyId);
        // Re-parent children to this dept's parent before deleting
        deptRepo.findByParentId(deptId).forEach(child -> {
            child.setParentId(dept.getParentId());
            deptRepo.save(child);
        });
        deptRepo.delete(dept);
    }

    @Transactional
    public void assignMemberDepartment(UUID companyId, UUID userId, AssignDepartmentRequest req) {
        if (req.departmentId() != null) {
            deptRepo.findById(req.departmentId())
                .filter(d -> d.getCompanyId().equals(companyId))
                .orElseThrow(() -> new NotFoundException("DEPT_NOT_FOUND", "Department not found"));
        }
        membershipRepo.findByCompanyIdAndUserId(companyId, userId)
            .ifPresentOrElse(
                m -> { m.setDepartmentId(req.departmentId()); membershipRepo.save(m); },
                () -> { throw new NotFoundException("MEMBER_NOT_FOUND", "Member not found in company"); }
            );
    }

    private Department requireOwned(UUID deptId, UUID companyId) {
        Department dept = deptRepo.findById(deptId)
            .orElseThrow(() -> new NotFoundException("DEPT_NOT_FOUND", "Department not found"));
        if (!dept.getCompanyId().equals(companyId)) {
            throw new ForbiddenException("WRONG_COMPANY", "Department does not belong to this company");
        }
        return dept;
    }

    private DepartmentResponse buildTree(Department dept, List<Department> all) {
        List<DepartmentResponse> children = all.stream()
            .filter(d -> dept.getId().equals(d.getParentId()))
            .map(child -> buildTree(child, all))
            .toList();
        return toResponse(dept, children);
    }

    private DepartmentResponse toResponse(Department d, List<DepartmentResponse> children) {
        return new DepartmentResponse(
            d.getId(), d.getCompanyId(), d.getParentId(),
            d.getName(), children, d.getCreatedAt()
        );
    }
}
