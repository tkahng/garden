package io.k2dv.garden.b2b.dto;

import io.k2dv.garden.b2b.model.CompanyRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(@NotNull CompanyRole role) {}
