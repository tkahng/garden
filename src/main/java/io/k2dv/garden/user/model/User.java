package io.k2dv.garden.user.model;

import io.k2dv.garden.iam.model.Role;
import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(schema = "auth", name = "users")
@Getter
@Setter
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.UNVERIFIED;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "admin_notes", columnDefinition = "text")
    private String adminNotes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags = new java.util.ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private java.util.Map<String, Object> metadata = new java.util.HashMap<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        schema = "auth",
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
}
