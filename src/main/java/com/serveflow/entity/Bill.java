package com.serveflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bill — represents a single counter bill created by the biller (Flow B — QuickBill).
 *
 * The biller creates a bill for each customer at the counter. The bill records what
 * the customer ordered, the amount, and how they paid. If paid by UPI, the
 * PaymentMatchingService links an incoming webhook payment to this bill.
 *
 * Lifecycle:
 *   CASH: Bill created → status = CASH_CONFIRMED → token generated immediately.
 *   UPI:  Bill created → status = WAITING_PAYMENT → matching engine searches for payment
 *         → if exactly one match: status = MATCHED, token generated
 *         → if multiple matches: status = AMBIGUOUS, biller resolves manually
 *         → if no match yet: stays WAITING_PAYMENT, retried on next webhook arrival
 *
 * Relationships:
 *   - Optionally references a FoodItem (nullable if biller typed a custom item name).
 *   - One-to-one with Payment (set only when a UPI payment is successfully matched).
 *   - One-to-one with Token (set once a token is generated for this bill).
 */
@Entity
@Table(name = "bills")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The FoodItem selected from the dropdown — nullable if biller typed a custom name.
    // When not null, the itemName is copied from foodItem.name at bill creation time.
    @ManyToOne
    @JoinColumn(name = "food_item_id", nullable = true)
    private FoodItem foodItem;

    // Display name of the item — copied from FoodItem.name at creation time, OR typed
    // manually by the biller for custom items not in the FoodItem table.
    // This snapshot ensures the bill is self-contained even if FoodItem records change.
    private String itemName;

    // Number of units billed.
    private Integer quantity;

    // Price per unit in rupees. Auto-filled from FoodItem.price but editable by the biller.
    @Column(precision = 10, scale = 2)
    private BigDecimal unitRate;

    // Total amount = unitRate × quantity. Calculated and stored at creation time.
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    // How the customer paid: CASH (immediate token) or UPI (matching engine).
    @Enumerated(EnumType.STRING)
    private PaymentMode paymentMode;

    // Current status of this bill in its lifecycle. See BillStatus enum for details.
    @Enumerated(EnumType.STRING)
    private BillStatus status;

    // Timestamp when the biller created this bill.
    private LocalDateTime createdAt;

    // The UPI payment that was matched to this bill. Null until a payment is claimed.
    // This is a bidirectional link — Payment also has a matchedBill reference.
    @OneToOne
    @JoinColumn(name = "matched_payment_id", nullable = true)
    private Payment matchedPayment;

    // The token generated for this bill. Null until the bill is confirmed (MATCHED or CASH_CONFIRMED).
    // Both Flow A and Flow B tokens are stored in the same Token table.
    @OneToOne
    @JoinColumn(name = "token_id", nullable = true)
    private Token token;
}
