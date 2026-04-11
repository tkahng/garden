package io.k2dv.garden.auth.service;

import io.k2dv.garden.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final AppProperties props;

    @Override
    public void sendEmailVerification(String to, String token) {
        var msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("Verify your Garden account");
        msg.setText(props.getFrontendUrl() + "/auth/verify-email?token=" + token);
        mailSender.send(msg);
    }

    @Override
    public void sendPasswordReset(String to, String token) {
        var msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("Reset your Garden password");
        msg.setText(props.getFrontendUrl() + "/auth/reset-password/" + token);
        mailSender.send(msg);
    }

    @Override
    public void sendQuoteSubmitted(String to, UUID quoteId) {
        var msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("Your quote request has been received");
        msg.setText("Thank you for submitting your quote request (ID: " + quoteId + "). "
            + "Our team will review it and get back to you shortly.");
        mailSender.send(msg);
    }

    @Override
    public void sendQuotePdf(String to, UUID quoteId, byte[] pdfBytes) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setTo(to);
            helper.setSubject("Your quote is ready — Quote #" + quoteId);
            helper.setText("Please find your quote attached. Review it and accept or reject it in the portal.");
            helper.addAttachment("quote-" + quoteId + ".pdf", () ->
                new java.io.ByteArrayInputStream(pdfBytes), "application/pdf");
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send quote email", e);
        }
    }
}
