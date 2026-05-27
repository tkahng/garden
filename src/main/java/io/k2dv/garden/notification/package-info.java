/**
 * Per-user opt-in/opt-out settings for transactional email notification types.
 * Every notification-sending service should consult {@code NotificationPreferenceService}
 * before dispatching an email to honour the user's communication preferences.
 */
package io.k2dv.garden.notification;
