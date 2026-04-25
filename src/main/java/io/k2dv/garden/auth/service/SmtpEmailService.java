package io.k2dv.garden.auth.service;

import io.k2dv.garden.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final AppProperties props;
    private final TemplateEngine templateEngine;

    @Override
    public void sendEmailVerification(String to, String token) {
        try {
            var msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject("Verify your Garden account");
            msg.setText(props.getFrontendUrl() + "/auth/verify-email?token=" + token);
            mailSender.send(msg);
        } catch (MailException e) {
            log.error("Failed to send email verification to {}: {}", to, e.getMessage(), e);
        }
    }

    @Override
    public void sendPasswordReset(String to, String token) {
        try {
            var msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject("Reset your Garden password");
            msg.setText(props.getFrontendUrl() + "/auth/reset-password/" + token);
            mailSender.send(msg);
        } catch (MailException e) {
            log.error("Failed to send password reset to {}: {}", to, e.getMessage(), e);
        }
    }

    @Override
    public void sendQuoteSubmitted(String to, UUID quoteId) {
        try {
            var msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject("Your quote request has been received");
            msg.setText("Thank you for submitting your quote request (ID: " + quoteId + "). "
                + "Our team will review it and get back to you shortly.");
            mailSender.send(msg);
        } catch (MailException e) {
            log.error("Failed to send quote-submitted email to {} for quote {}: {}", to, quoteId, e.getMessage(), e);
        }
    }

    @Override
    public void sendQuoteNewRequest(String to, UUID quoteId) {
        try {
            var msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject("New quote request received — #" + quoteId);
            msg.setText("A new quote request has been submitted (ID: " + quoteId + "). "
                + "Log in to the admin portal to review and assign it.");
            mailSender.send(msg);
        } catch (MailException e) {
            log.error("Failed to send quote-new-request email to {} for quote {}: {}", to, quoteId, e.getMessage(), e);
        }
    }

    @Override
    public void sendCompanyInvitation(String to, String companyName, String inviterName, String token) {
        try {
            var msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(inviterName + " has invited you to join " + companyName + " on Garden");
            msg.setText("You've been invited to join " + companyName + " by " + inviterName + ".\n\n"
                + "Accept your invitation here:\n"
                + props.getFrontendUrl() + "/invitations/" + token + "\n\n"
                + "This invitation expires in 7 days.");
            mailSender.send(msg);
        } catch (MailException e) {
            log.error("Failed to send company invitation to {}: {}", to, e.getMessage(), e);
        }
    }

    @Override
    public void sendOrderConfirmation(String to, String orderRef, BigDecimal total, String currency,
                                      List<String> itemLines, String storeFrontUrl) {
        try {
            Context ctx = new Context();
            ctx.setVariable("orderRef", orderRef);
            ctx.setVariable("total", formatAmount(total, currency));
            ctx.setVariable("itemLines", itemLines);
            ctx.setVariable("storeFrontUrl", storeFrontUrl);
            String html = templateEngine.process("email/order-confirmation", ctx);

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject("Order confirmed — " + orderRef);
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send order confirmation to {} for {}: {}", to, orderRef, e.getMessage(), e);
        }
    }

    @Override
    public void sendShippingNotification(String to, String orderRef, String trackingNumber,
                                         String trackingCompany, String trackingUrl, String storeFrontUrl) {
        try {
            Context ctx = new Context();
            ctx.setVariable("orderRef", orderRef);
            ctx.setVariable("trackingNumber", trackingNumber);
            ctx.setVariable("trackingCompany", trackingCompany);
            ctx.setVariable("trackingUrl", trackingUrl);
            ctx.setVariable("storeFrontUrl", storeFrontUrl);
            String html = templateEngine.process("email/shipping-notification", ctx);

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject("Your order has shipped — " + orderRef);
            helper.setText(html, true);
            mailSender.send(msg);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send shipping notification to {} for {}: {}", to, orderRef, e.getMessage(), e);
        }
    }

    private static String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) return "";
        String symbol = "usd".equalsIgnoreCase(currency) ? "$" : currency.toUpperCase() + " ";
        return symbol + String.format("%.2f", amount);
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
        } catch (MessagingException | MailException e) {
            log.error("Failed to send quote PDF email to {} for quote {}: {}", to, quoteId, e.getMessage(), e);
        }
    }
}
