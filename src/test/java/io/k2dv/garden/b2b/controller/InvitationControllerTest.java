package io.k2dv.garden.b2b.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.b2b.dto.InvitationResponse;
import io.k2dv.garden.b2b.model.CompanyRole;
import io.k2dv.garden.b2b.model.InvitationStatus;
import io.k2dv.garden.b2b.service.CompanyInvitationService;
import io.k2dv.garden.config.TestCurrentUserConfig;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InvitationController.class)
@Import({TestSecurityConfig.class, TestCurrentUserConfig.class, GlobalExceptionHandler.class})
class InvitationControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CompanyInvitationService invitationService;

    private InvitationResponse stubInvitation(UUID token) {
        return new InvitationResponse(
            UUID.randomUUID(), UUID.randomUUID(), "Acme Corp",
            "invitee@example.com", CompanyRole.MEMBER, null,
            token, UUID.randomUUID(), InvitationStatus.PENDING,
            Instant.now().plusSeconds(86400 * 7), Instant.now());
    }

    @Test
    void getByToken_returns200() throws Exception {
        UUID token = UUID.randomUUID();
        when(invitationService.getByToken(eq(token))).thenReturn(stubInvitation(token));

        mvc.perform(get("/api/v1/invitations/{token}", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.token").value(token.toString()))
            .andExpect(jsonPath("$.data.companyName").value("Acme Corp"));
    }

    @Test
    void getByToken_notFound_returns404() throws Exception {
        when(invitationService.getByToken(any()))
            .thenThrow(new NotFoundException("INVITATION_NOT_FOUND", "Invitation not found"));

        mvc.perform(get("/api/v1/invitations/{token}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void accept_returns200() throws Exception {
        UUID token = UUID.randomUUID();
        InvitationResponse accepted = new InvitationResponse(
            UUID.randomUUID(), UUID.randomUUID(), "Acme Corp",
            "invitee@example.com", CompanyRole.MEMBER, null,
            token, UUID.randomUUID(), InvitationStatus.ACCEPTED,
            Instant.now().plusSeconds(86400 * 7), Instant.now());

        when(invitationService.accept(eq(token), any())).thenReturn(accepted);

        mvc.perform(post("/api/v1/invitations/{token}/accept", token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }

    @Test
    void accept_emailMismatch_returns403() throws Exception {
        when(invitationService.accept(any(), any()))
            .thenThrow(new ForbiddenException("EMAIL_MISMATCH", "Email mismatch"));

        mvc.perform(post("/api/v1/invitations/{token}/accept", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    void accept_alreadyAccepted_returns409() throws Exception {
        when(invitationService.accept(any(), any()))
            .thenThrow(new ConflictException("INVITATION_NOT_PENDING", "Not pending"));

        mvc.perform(post("/api/v1/invitations/{token}/accept", UUID.randomUUID()))
            .andExpect(status().isConflict());
    }
}
