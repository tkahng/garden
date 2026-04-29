package io.k2dv.garden.webhook.model;

public enum WebhookEventType {
    ORDER_PLACED,
    ORDER_PAID,
    ORDER_CANCELLED,
    ORDER_REFUNDED,
    FULFILLMENT_SHIPPED,
    FULFILLMENT_DELIVERED,
    INVOICE_ISSUED,
    INVOICE_PAID,
    INVOICE_OVERDUE
}
