package io.k2dv.garden.auth.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface EmailService {
    void sendEmailVerification(String to, String token);
    void sendPasswordReset(String to, String token);
    void sendQuoteSubmitted(String to, UUID quoteId);
    void sendQuoteNewRequest(String to, UUID quoteId);
    void sendQuotePdf(String to, UUID quoteId, byte[] pdfBytes);
    void sendCompanyInvitation(String to, String companyName, String inviterName, String token);
    void sendOrderConfirmation(String to, String orderRef, BigDecimal total, String currency, List<String> itemLines, String storeFrontUrl);
    void sendShippingNotification(String to, String orderRef, String trackingNumber, String trackingCompany, String trackingUrl, String storeFrontUrl);
    void sendOrderCancelled(String to, String orderRef, String storeFrontUrl);
    void sendOrderDelivered(String to, String orderRef, String productHandle, String storeFrontUrl);
    void sendAbandonedCartReminder(String to, String firstName, List<String> itemLines, String cartUrl);
    void sendLowStockAlert(String to, List<String> itemLines);
}
