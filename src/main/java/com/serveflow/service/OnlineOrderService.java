package com.serveflow.service;

import com.serveflow.dto.request.OnlineOrderCreateRequestDTO;
import com.serveflow.dto.response.OnlineOrderResponseDTO;
import com.serveflow.dto.response.TokenResponseDTO;
import com.serveflow.entity.*;
import com.serveflow.exception.OrderCutoffException;
import com.serveflow.mapper.TokenMapper;
import com.serveflow.repository.FoodItemRepository;
import com.serveflow.repository.OrderRepository;
import com.serveflow.repository.TokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;

/**
 * OnlineOrderService — handles the Campus Bite student pre-order flow (Flow A).
 *
 * FLOW A OVERVIEW:
 *   1. Student browses the menu on home.html, adds items to cart.
 *   2. Student opens checkout.html and clicks "Pay Now".
 *   3. createOnlineOrder() is called: validates cutoff, creates Razorpay Order,
 *      saves our Order with status PENDING, returns Razorpay orderId to the frontend.
 *   4. Frontend opens the Razorpay checkout modal using the orderId.
 *   5. Student completes payment. Razorpay calls our frontend callback with
 *      razorpayOrderId, razorpayPaymentId, razorpaySignature.
 *   6. Frontend sends these to processOnlinePayment().
 *   7. processOnlinePayment() verifies the signature, marks the Order as PAID,
 *      generates a Token, returns the token to the student.
 *
 * 15-MINUTE CUTOFF:
 *   Online ordering closes 15 minutes before lunchStartTime.
 *   This is a service-layer check — not a database column.
 *   Configurable via app.lunch-start-time in application.properties.
 */
@Service
public class OnlineOrderService {

    private static final Logger log = LoggerFactory.getLogger(OnlineOrderService.class);

    private final OrderRepository orderRepository;
    private final FoodItemRepository foodItemRepository;
    private final TokenService tokenService;
    private final TokenRepository tokenRepository;
    private final TokenMapper tokenMapper;

    // Lunch start time — online ordering closes 15 minutes before this.
    // Format: "HH:mm" (24-hour). Example: "12:30"
    @Value("${app.lunch-start-time:12:30}")
    private String lunchStartTimeStr;

    public void setLunchStartTimeStr(String lunchStartTimeStr) {
        this.lunchStartTimeStr = lunchStartTimeStr;
    }

    // Razorpay key secret — used to verify payment signatures.
    // Read from application.properties. NEVER hardcoded.
    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    // Razorpay key ID — sent to the frontend for checkout modal initialization.
    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    public OnlineOrderService(OrderRepository orderRepository,
                              FoodItemRepository foodItemRepository,
                              TokenService tokenService,
                              TokenRepository tokenRepository,
                              TokenMapper tokenMapper) {
        this.orderRepository = orderRepository;
        this.foodItemRepository = foodItemRepository;
        this.tokenService = tokenService;
        this.tokenRepository = tokenRepository;
        this.tokenMapper = tokenMapper;
    }

    /**
     * Purpose: Creates a new online pre-order for a logged-in student.
     *          Enforces the 15-minute cutoff before lunch.
     *          Creates a Razorpay Order (deterministic payment linkage — no ambiguity).
     * Input:   dto          — the order details (items, quantities, pickup time).
     *          studentEmail — extracted from JWT by OnlineOrderController using @AuthenticationPrincipal.
     *                         NEVER accepted as a request parameter.
     * Output:  OnlineOrderResponseDTO including the Razorpay orderId for the checkout modal.
     * Throws:  OrderCutoffException if past the 15-minute cutoff before lunch.
     */
    @Transactional
    public OnlineOrderResponseDTO createOnlineOrder(OnlineOrderCreateRequestDTO dto, String studentEmail) {

        // ── STEP 1: CHECK THE 15-MINUTE CUTOFF ───────────────────────────────────────
        // Parse the configured lunch start time.
        LocalTime lunchStartTime = LocalTime.parse(lunchStartTimeStr, DateTimeFormatter.ofPattern("HH:mm"));

        // The ordering cutoff is 15 minutes before lunch starts.
        LocalTime cutoffTime = lunchStartTime.minusMinutes(15);

        // Compare current time to the cutoff.
        LocalTime now = LocalTime.now();
        if (now.isAfter(cutoffTime)) {
            // Current time is past the cutoff — reject the order with a clear message.
            throw new OrderCutoffException(
                    "Online ordering has closed. Please visit the counter directly. " +
                    "Ordering resumes the next day before " + cutoffTime.format(DateTimeFormatter.ofPattern("HH:mm")) + ".");
        }

        // ── STEP 2: CALCULATE TOTAL AMOUNT ────────────────────────────────────────────
        double totalAmount = 0.0;
        List<OrderItem> orderItems = new ArrayList<>();

        for (OnlineOrderCreateRequestDTO.OrderItemDTO itemDTO : dto.getItems()) {
            FoodItem foodItem = foodItemRepository.findById(itemDTO.getFoodItemId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Food item not found: " + itemDTO.getFoodItemId()));

            // Deduct from available quantity.
            if (foodItem.getQuantityAvailable() < itemDTO.getQuantity()) {
                throw new IllegalArgumentException(
                        "Not enough quantity available for: " + foodItem.getName());
            }
            foodItem.setQuantityAvailable(foodItem.getQuantityAvailable() - itemDTO.getQuantity());
            foodItemRepository.save(foodItem);

            OrderItem orderItem = new OrderItem();
            orderItem.setFoodItem(foodItem);
            orderItem.setQuantity(itemDTO.getQuantity());
            orderItem.setPrice(foodItem.getPrice() * itemDTO.getQuantity());
            totalAmount += orderItem.getPrice();
            orderItems.add(orderItem);
        }

        // ── STEP 3: CREATE THE ORDER ENTITY ──────────────────────────────────────────
        Order order = new Order();
        order.setStudentEmail(studentEmail);  // from JWT — never from request body
        order.setStudentName(studentEmail.split("@")[0]); // use email prefix as name
        order.setRollNumber("N/A"); // not required for OAuth flow
        order.setStatus("PENDING");
        order.setTotalAmount(totalAmount);
        order.setPickupTime(dto.getPickupTime());
        order.setOrderDate(LocalDateTime.now());
        order.setPlacedAt(LocalDateTime.now());
        order.setPaymentStatus(OrderPaymentStatus.PENDING);

        // Generate a placeholder token number (old format) — the real token is generated after payment.
        order.setTokenNumber("ONLINE-" + System.currentTimeMillis());

        // Set the OrderItems' back-reference to the order.
        for (OrderItem item : orderItems) {
            item.setOrder(order);
        }
        order.setItems(orderItems);

        // ── STEP 4: CREATE RAZORPAY ORDER ─────────────────────────────────────────────
        String razorpayOrderId;
        try {
            RazorpayClient razorpay = new RazorpayClient(razorpayKeyId.trim(), razorpayKeySecret.trim());
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", Math.round(totalAmount * 100)); // amount in paise
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "txn_" + System.currentTimeMillis());

            com.razorpay.Order razorpayOrder = razorpay.orders.create(orderRequest);
            razorpayOrderId = razorpayOrder.get("id");
            order.setRazorpayOrderId(razorpayOrderId);
        } catch (Exception e) {
            log.error("Failed to create Razorpay order", e);
            throw new IllegalStateException("Failed to initialize payment gateway. Please try again.");
        }

        Order savedOrder = orderRepository.save(order);

        log.info("createOnlineOrder: Order {} created for student {} (Razorpay: {})",
                savedOrder.getId(), studentEmail, razorpayOrderId);

        // Build and return the response DTO.
        OnlineOrderResponseDTO responseDTO = new OnlineOrderResponseDTO();
        responseDTO.setId(savedOrder.getId());
        responseDTO.setStudentEmail(savedOrder.getStudentEmail());
        responseDTO.setTotalAmount(savedOrder.getTotalAmount());
        responseDTO.setPaymentStatus(savedOrder.getPaymentStatus());
        responseDTO.setRazorpayOrderId(savedOrder.getRazorpayOrderId());
        responseDTO.setPlacedAt(savedOrder.getPlacedAt());
        responseDTO.setStatus(savedOrder.getStatus());

        return responseDTO;
    }

    /**
     * Purpose: Verifies the Razorpay payment signature for a completed student checkout,
     *          marks the Order as PAID, and generates a Token.
     * Input:   razorpayOrderId   — the Razorpay order ID (from the frontend callback).
     *          razorpayPaymentId — the Razorpay payment ID (from the frontend callback).
     *          razorpaySignature — the HMAC-SHA256 signature (from the frontend callback).
     *          studentEmail      — from JWT, ensures student can only confirm their own payment.
     * Output:  TokenResponseDTO — the student's food pickup token.
     * Throws:  IllegalArgumentException if the order is not found or signature is invalid.
     */
    @Transactional
    public TokenResponseDTO processOnlinePayment(String razorpayOrderId, String razorpayPaymentId,
                                                  String razorpaySignature, String studentEmail) {

        log.info("processOnlinePayment: Verifying payment for order {}", razorpayOrderId);

        // ── STEP 1: VERIFY THE RAZORPAY SIGNATURE ────────────────────────────────────
        // Razorpay signs: HMAC-SHA256(razorpayOrderId + "|" + razorpayPaymentId, keySecret)
        // We compute the same and compare. This proves the payment is genuine.
        String expectedSignature = computeRazorpaySignature(razorpayOrderId, razorpayPaymentId);

        if (!expectedSignature.equals(razorpaySignature)) {
            log.error("processOnlinePayment: Signature mismatch for order {}!", razorpayOrderId);
            throw new IllegalArgumentException("Payment verification failed. Invalid signature.");
        }

        // ── STEP 2: LOAD THE ORDER ────────────────────────────────────────────────────
        Order order = orderRepository.findAll().stream()
                .filter(o -> razorpayOrderId.equals(o.getRazorpayOrderId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Order not found for Razorpay ID: " + razorpayOrderId));

        // Security check: ensure this student can only confirm their own order.
        if (!studentEmail.equals(order.getStudentEmail())) {
            throw new IllegalArgumentException("Access denied: this order belongs to a different student.");
        }

        // ── STEP 3: MARK THE ORDER AS PAID ───────────────────────────────────────────
        order.setPaymentStatus(OrderPaymentStatus.PAID);
        order.setRazorpayPaymentId(razorpayPaymentId);
        order.setStatus("PAID");
        orderRepository.save(order);

        // ── STEP 4: GENERATE A TOKEN ──────────────────────────────────────────────────
        Token token = tokenService.generateToken(order);

        // Store the token ID reference in the order.
        order.setTokenId(token.getId());
        orderRepository.save(order);

        log.info("processOnlinePayment: Token #{} generated for order {}", token.getTokenNumber(), order.getId());

        return tokenMapper.toDTO(token);
    }

    /**
     * Purpose: Fetches all orders and tokens for a specific student (My Orders page).
     * Input:   studentEmail — from JWT, identifies the logged-in student.
     * Output:  List of OnlineOrderResponseDTO with token details included where available.
     */
    public List<OnlineOrderResponseDTO> getStudentOrders(String studentEmail) {
        List<Order> orders = orderRepository.findByStudentEmailOrderByPlacedAtDesc(studentEmail);
        List<OnlineOrderResponseDTO> dtos = new ArrayList<>();

        for (Order order : orders) {
            OnlineOrderResponseDTO dto = new OnlineOrderResponseDTO();
            dto.setId(order.getId());
            dto.setStudentEmail(order.getStudentEmail());
            dto.setTotalAmount(order.getTotalAmount());
            dto.setPaymentStatus(order.getPaymentStatus());
            dto.setRazorpayOrderId(order.getRazorpayOrderId());
            dto.setPlacedAt(order.getPlacedAt());
            dto.setStatus(order.getStatus());

            // Include token details if a token was generated for this order.
            if (order.getTokenId() != null) {
                tokenRepository.findById(order.getTokenId()).ifPresent(token ->
                        dto.setToken(tokenMapper.toDTO(token)));
            }

            dtos.add(dto);
        }

        return dtos;
    }

    /**
     * Purpose: Marks a student's paid order as SERVED once the food is handed over.
     */
    @Transactional
    public void markOrderAsServed(Long orderId, String studentEmail) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!studentEmail.equals(order.getStudentEmail())) {
            throw new IllegalArgumentException("Access denied.");
        }

        if (order.getPaymentStatus() != OrderPaymentStatus.PAID) {
            throw new IllegalStateException("Order is not paid.");
        }

        order.setStatus("SERVED");
        orderRepository.save(order);
    }

    /**
     * Purpose: Computes the HMAC-SHA256 signature for Razorpay payment verification.
     *          The input is: razorpayOrderId + "|" + razorpayPaymentId
     *          The key is: Razorpay Key Secret
     * Input:   razorpayOrderId   — the Razorpay order ID.
     *          razorpayPaymentId — the Razorpay payment ID.
     * Output:  Hex string of the HMAC-SHA256 digest.
     */
    private String computeRazorpaySignature(String razorpayOrderId, String razorpayPaymentId) {
        try {
            String data = razorpayOrderId + "|" + razorpayPaymentId;
            Mac hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                    razorpayKeySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSha256.init(secretKey);
            byte[] bytes = hmacSha256.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute Razorpay signature", e);
        }
    }
}
