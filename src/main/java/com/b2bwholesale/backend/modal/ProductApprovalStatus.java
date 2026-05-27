package com.b2bwholesale.backend.modal;

/**
 * Lifecycle of a business-submitted product before it is visible on the marketplace.
 * Newly created products start as PENDING; the admin transitions them to
 * APPROVED (→ visible in marketplace) or REJECTED (→ hidden, reason captured).
 */
public enum ProductApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
