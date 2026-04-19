package io.k2dv.garden.auth.service;

import java.util.UUID;

public interface EmailService {
    void sendEmailVerification(String to, String token);
    void sendPasswordReset(String to, String token);
    void sendQuoteSubmitted(String to, UUID quoteId);
    void sendQuoteNewRequest(String to, UUID quoteId);
    void sendQuotePdf(String to, UUID quoteId, byte[] pdfBytes);
    void sendCompanyInvitation(String to, String companyName, String inviterName, String token);
}
