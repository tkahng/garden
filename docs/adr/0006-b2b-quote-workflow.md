# ADR-0006: B2B Quote → Review → PDF → Accept → Order Flow

**Status:** Accepted

## Context

B2B buyers often need to negotiate pricing before committing to a purchase. Unlike a standard consumer checkout, a B2B transaction may involve: custom line-item pricing, a formal PDF document for procurement review, internal spending-limit approval, and eventual conversion to a net-terms invoice rather than a Stripe payment.

The simplest approach would be to allow admin users to create orders directly with custom pricing. But this bypasses the buyer's own review and approval step, and generates no auditable paper trail.

## Decision

Implement a multi-step quote workflow:

1. **Buyer submits a quote request** from a special quote cart (items + quantities, no pricing). Status: `REQUESTED`.
2. **Admin assigns and prices** each line item individually. Status: `PRICED`.
3. **Admin generates a PDF** (Thymeleaf template → OpenHTMLtoPDF → S3) and sends it to the buyer. Status: `SENT`.
4. **Buyer accepts or rejects** the quote. On acceptance, the system converts the quote to an `Order` with the negotiated line-item prices. Status: `ACCEPTED`.
5. If the resulting order total exceeds the buyer's **spending limit**, it is routed to a company owner/manager for approval before payment proceeds.
6. Payment can be via Stripe Checkout or, for buyers with an approved credit account, **net terms** (invoice issued immediately, payment recorded manually later).

Each status transition emits a timeline event and triggers the appropriate notification email (buyer and internal staff).

## Consequences

**Positive:**
- Buyers receive a formal, signed-off PDF they can submit to their own procurement process.
- Custom pricing is negotiated explicitly rather than implied by price list rules.
- The spending-limit gate enforces company purchasing policies without manual oversight of every order.
- Net terms path lets credit-approved buyers complete checkout without a real-time payment.

**Negative:**
- Adds significant state machine complexity: `QuoteRequest` has 7 statuses, each with its own valid transitions.
- PDF generation is synchronous and CPU-bound; large quotes with many line items can add latency to the `send PDF` step.
- The quote-to-order conversion must carefully handle inventory reservation timing to avoid overselling during negotiation.
