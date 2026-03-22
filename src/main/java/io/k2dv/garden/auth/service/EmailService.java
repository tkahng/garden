package io.k2dv.garden.auth.service;

public interface EmailService {
    void sendEmailVerification(String to, String token);
    void sendPasswordReset(String to, String token);
}
