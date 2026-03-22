package io.k2dv.garden.auth.service;

import io.k2dv.garden.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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
}
