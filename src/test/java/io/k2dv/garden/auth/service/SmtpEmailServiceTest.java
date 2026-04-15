package io.k2dv.garden.auth.service;

import io.k2dv.garden.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock AppProperties props;
    @Mock MimeMessage mimeMessage;

    SmtpEmailService service;

    @BeforeEach
    void setUp() {
        lenient().when(props.getFrontendUrl()).thenReturn("https://example.com");
        service = new SmtpEmailService(mailSender, props);
    }

    // ── sendEmailVerification ─────────────────────────────────────────────────

    @Test
    void sendEmailVerification_sendsCorrectMessage() {
        service.sendEmailVerification("user@example.com", "tok123");

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(msg.getSubject()).isEqualTo("Verify your Garden account");
        assertThat(msg.getText()).contains("tok123");
        assertThat(msg.getText()).startsWith("https://example.com");
    }

    @Test
    void sendEmailVerification_mailFailure_doesNotThrow() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service.sendEmailVerification("user@example.com", "tok123"))
            .doesNotThrowAnyException();
    }

    // ── sendPasswordReset ─────────────────────────────────────────────────────

    @Test
    void sendPasswordReset_sendsCorrectMessage() {
        service.sendPasswordReset("user@example.com", "resetTok");

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(msg.getSubject()).isEqualTo("Reset your Garden password");
        assertThat(msg.getText()).contains("resetTok");
    }

    @Test
    void sendPasswordReset_mailFailure_doesNotThrow() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service.sendPasswordReset("user@example.com", "tok"))
            .doesNotThrowAnyException();
    }

    // ── sendQuoteSubmitted ────────────────────────────────────────────────────

    @Test
    void sendQuoteSubmitted_sendsCorrectMessage() {
        UUID quoteId = UUID.randomUUID();
        service.sendQuoteSubmitted("user@example.com", quoteId);

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("user@example.com");
        assertThat(msg.getSubject()).contains("quote request");
        assertThat(msg.getText()).contains(quoteId.toString());
    }

    @Test
    void sendQuoteSubmitted_mailFailure_doesNotThrow() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service.sendQuoteSubmitted("user@example.com", UUID.randomUUID()))
            .doesNotThrowAnyException();
    }

    // ── sendQuoteNewRequest ───────────────────────────────────────────────────

    @Test
    void sendQuoteNewRequest_sendsCorrectMessage() {
        UUID quoteId = UUID.randomUUID();
        service.sendQuoteNewRequest("admin@example.com", quoteId);

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getTo()).containsExactly("admin@example.com");
        assertThat(msg.getSubject()).contains(quoteId.toString());
        assertThat(msg.getText()).contains(quoteId.toString());
    }

    @Test
    void sendQuoteNewRequest_mailFailure_doesNotThrow() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> service.sendQuoteNewRequest("admin@example.com", UUID.randomUUID()))
            .doesNotThrowAnyException();
    }

    // ── sendQuotePdf ──────────────────────────────────────────────────────────

    @Test
    void sendQuotePdf_delegatesToMailSender() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        UUID quoteId = UUID.randomUUID();

        service.sendQuotePdf("user@example.com", quoteId, new byte[]{1, 2, 3});

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendQuotePdf_mailFailure_doesNotThrow() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> service.sendQuotePdf("user@example.com", UUID.randomUUID(), new byte[]{1}))
            .doesNotThrowAnyException();
    }
}
