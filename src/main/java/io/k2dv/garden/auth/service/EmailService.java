package io.k2dv.garden.auth.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Contract for all transactional and lifecycle email notifications sent by the platform.
 * Covers identity flows (verification, password reset), B2B quote lifecycle events,
 * order fulfilment notifications, and operational alerts (low stock, abandoned cart).
 * Implementations are expected to handle delivery failures gracefully without
 * propagating exceptions to callers.
 */
public interface EmailService {
    /** Sends the email-address verification link to a newly registered user. */
    void sendEmailVerification(String to, String token);

    /** Sends a one-time password-reset link; called only after rate-limit checks pass. */
    void sendPasswordReset(String to, String token);

    /** Notifies the customer that their quote request has been received and is under review. */
    void sendQuoteSubmitted(String to, UUID quoteId);

    /** Alerts internal staff that a new quote request requires attention. */
    void sendQuoteNewRequest(String to, UUID quoteId);

    /** Delivers the quote PDF as an email attachment for offline review. */
    void sendQuotePdf(String to, UUID quoteId, byte[] pdfBytes);

    /** Notifies the customer that their quote was accepted and an order was created. */
    void sendQuoteAccepted(String to, UUID quoteId, UUID orderId);

    /** Prompts a B2B approver that a quote is awaiting their internal sign-off. */
    void sendQuotePendingApproval(String to, UUID quoteId);

    /** Informs the customer that the internal approval step has been cleared and the quote is approved. */
    void sendQuoteApproved(String to, UUID quoteId);

    /** Notifies internal staff that the customer has rejected the submitted quote. */
    void sendQuoteRejectedByUser(String to, UUID quoteId);

    /** Notifies the customer that the internal approval for their quote was denied. */
    void sendQuoteApprovalRejected(String to, UUID quoteId);

    /** Notifies the customer that their quote has passed its validity window without acceptance. */
    void sendQuoteExpired(String to, UUID quoteId);

    /** Sends a B2B company membership invitation with a single-use acceptance token. */
    void sendCompanyInvitation(String to, String companyName, String inviterName, String token);

    /** Sends the post-purchase order confirmation with line-item summary and order total. */
    void sendOrderConfirmation(String to, String orderRef, BigDecimal total, String currency, List<String> itemLines, String storeFrontUrl);

    /** Notifies the customer that their order has shipped, including carrier tracking details. */
    void sendShippingNotification(String to, String orderRef, String trackingNumber, String trackingCompany, String trackingUrl, String storeFrontUrl);

    /** Informs the customer that their order has been cancelled. */
    void sendOrderCancelled(String to, String orderRef, String storeFrontUrl);

    /** Confirms delivery and prompts a post-purchase review or follow-up action. */
    void sendOrderDelivered(String to, String orderRef, String productHandle, String storeFrontUrl);

    /** Sends a re-engagement reminder to a shopper who left items in their cart. */
    void sendAbandonedCartReminder(String to, String firstName, List<String> itemLines, String cartUrl);

    /** Sends an operational alert to staff listing inventory variants that have fallen below threshold. */
    void sendLowStockAlert(String to, List<String> itemLines);
}
