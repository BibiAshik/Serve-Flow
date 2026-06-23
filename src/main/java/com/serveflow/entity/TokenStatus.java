package com.serveflow.entity;

/**
 * TokenStatus — tracks the lifecycle of a generated token (both flows).
 *
 * ACTIVE:       Token has been generated and is ready for the customer.
 *               Waiting to be printed or displayed.
 *
 * PRINTED:      Token was successfully sent to the physical ESC/POS printer
 *               and confirmed printed. printedAt timestamp is set.
 *
 * PRINT_FAILED: Printer was unreachable or threw an error.
 *               PrinterService automatically falls back to virtual printing
 *               (rendering the token on-screen in QuickBill's Recent Tokens zone).
 *
 * Future (NOT implemented in this phase):
 *   SERVED — for the Token Desk phase where a staff member marks a token
 *            as served when handing over the food, preventing reuse.
 */
public enum TokenStatus {
    ACTIVE,
    PRINTED,
    PRINT_FAILED

    // Future: SERVED (Token Desk phase — not implemented yet)
}
