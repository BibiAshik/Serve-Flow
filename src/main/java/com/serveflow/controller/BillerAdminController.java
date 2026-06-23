package com.serveflow.controller;

import com.serveflow.dto.request.SettingsUpdateRequestDTO;
import com.serveflow.dto.response.OnlineOrderResponseDTO;
import com.serveflow.dto.response.SettingsResponseDTO;
import com.serveflow.entity.Order;
import com.serveflow.entity.OrderPaymentStatus;
import com.serveflow.repository.OrderRepository;
import com.serveflow.repository.BillRepository;
import com.serveflow.repository.FoodItemRepository;
import com.serveflow.entity.FoodItem;
import com.serveflow.service.OrderService;
import com.serveflow.service.PrinterService;
import com.serveflow.service.OnlineOrderService;
import com.serveflow.service.BillingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * BillerAdminController — handles QuickBill sidebar pages (history, online orders, settings).
 *
 * All endpoints require ROLE_BILLER (same JWT chain as BillerController —
 * there is no separate admin role in this system).
 *
 * Endpoints:
 *   GET /api/biller/history          — transaction history (bills + payments + match type)
 *   GET /api/biller/online-orders    — incoming Campus Bite pre-orders feed
 *   GET /api/biller/settings         — current settings
 *   PUT /api/biller/settings         — update lunch cutoff + matching window
 */
@RestController
@RequestMapping("/api/biller")
@PreAuthorize("hasRole('BILLER')")
public class BillerAdminController {

    private final OrderRepository orderRepository;
    private final BillRepository billRepository;
    private final OrderService orderService;
    private final PrinterService printerService;
    private final OnlineOrderService onlineOrderService;
    private final BillingService billingService;
    private final FoodItemRepository foodItemRepository;

    // Settings stored as mutable instance variables for runtime updates.
    // These start from application.properties values. In production, these should
    // be persisted in a Settings table — that's documented as a future improvement.
    @Value("${app.lunch-start-time:12:30}")
    private String lunchStartTime;

    @Value("${app.matching-window-minutes:10}")
    private int matchingWindowMinutes;

    @Value("${app.college-email-domain:@sairamtap.edu.in}")
    private String collegeEmailDomain;

    public BillerAdminController(OrderRepository orderRepository,
                                 BillRepository billRepository,
                                 OrderService orderService,
                                 PrinterService printerService,
                                 OnlineOrderService onlineOrderService,
                                 BillingService billingService,
                                 FoodItemRepository foodItemRepository) {
        this.orderRepository = orderRepository;
        this.billRepository = billRepository;
        this.orderService = orderService;
        this.printerService = printerService;
        this.onlineOrderService = onlineOrderService;
        this.billingService = billingService;
        this.foodItemRepository = foodItemRepository;
    }

    /**
     * Purpose: Returns the transaction history for the biller's History sidebar page.
     *          In this phase, returns the list of online orders (full bill+payment history
     *          join query is a future enhancement listed in the History page comments).
     * Input:   none.
     * Output:  List of online orders as a simple history view.
     */
    @GetMapping("/history")
    public ResponseEntity<List<java.util.Map<String, Object>>> getHistory() {
        List<java.util.Map<String, Object>> history = new ArrayList<>();
        
        // 1. Online Orders
        for (Order o : orderService.getAllOrders()) {
            if (o.getPaymentStatus() == OrderPaymentStatus.PENDING || o.getPaymentStatus() == OrderPaymentStatus.FAILED) {
                continue;
            }
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", "ONL-" + o.getId());
            map.put("studentEmail", o.getStudentEmail());
            
            StringBuilder summary = new StringBuilder();
            for (com.serveflow.entity.OrderItem item : o.getItems()) {
                if (summary.length() > 0) summary.append(", ");
                summary.append(item.getQuantity()).append("x ").append(item.getFoodItem().getName());
            }
            map.put("itemSummary", summary.toString());
            map.put("totalAmount", o.getTotalAmount());
            map.put("status", o.getStatus());
            map.put("paymentStatus", o.getPaymentStatus() != null ? o.getPaymentStatus().name() : "PENDING");
            map.put("placedAt", o.getPlacedAt());
            history.add(map);
        }
        
        // 2. Counter Bills
        for (com.serveflow.entity.Bill b : billRepository.findAllByOrderByCreatedAtDesc()) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", "BIL-" + b.getId());
            map.put("studentEmail", "Counter Bill");
            map.put("itemSummary", b.getQuantity() + "x " + b.getItemName());
            map.put("totalAmount", b.getAmount());
            map.put("status", b.getStatus().name());
            
            // If it's a UPI payment that was matched, show the transaction ID
            String paymentStatus = b.getPaymentMode().name();
            if (b.getMatchedPayment() != null) {
                paymentStatus += " (" + b.getMatchedPayment().getUpiReferenceId() + ")";
            }
            map.put("paymentStatus", paymentStatus);
            map.put("placedAt", b.getCreatedAt());
            history.add(map);
        }
        
        // Sort descending by date
        history.sort((a, b) -> {
            java.time.LocalDateTime dateA = (java.time.LocalDateTime) a.get("placedAt");
            java.time.LocalDateTime dateB = (java.time.LocalDateTime) b.get("placedAt");
            if (dateA == null && dateB == null) return 0;
            if (dateA == null) return 1;
            if (dateB == null) return -1;
            return dateB.compareTo(dateA);
        });
        
        return ResponseEntity.ok(history);
    }

    /**
     * Purpose: Returns the incoming Campus Bite online pre-orders for the biller.
     *          Biller uses this to see which orders have been placed and need to be prepared.
     * Input:   status — optional filter: "ALL", "PAID", "PENDING", "FAILED" (query param).
     * Output:  List of OnlineOrderResponseDTO.
     */
    @GetMapping("/online-orders")
    public ResponseEntity<List<OnlineOrderResponseDTO>> getOnlineOrders(
            @RequestParam(required = false, defaultValue = "ALL") String status) {

        List<Order> orders;

        if ("ALL".equalsIgnoreCase(status)) {
            orders = orderService.getAllOrders();
        } else {
            // Convert the status string to the enum and filter.
            try {
                OrderPaymentStatus paymentStatus = OrderPaymentStatus.valueOf(status.toUpperCase());
                orders = orderRepository.findByPaymentStatusOrderByPlacedAtDesc(paymentStatus);
            } catch (IllegalArgumentException e) {
                // Invalid status value — return all orders.
                orders = orderService.getAllOrders();
            }
        }

        // Convert to DTOs with masked email (first 3 chars + ***@domain).
        List<OnlineOrderResponseDTO> dtos = new ArrayList<>();
        for (Order order : orders) {
            OnlineOrderResponseDTO dto = new OnlineOrderResponseDTO();
            dto.setId(order.getId());

            // Mask the student email for privacy: "joh***@sairamtap.edu.in"
            String email = order.getStudentEmail();
            if (email != null && email.contains("@")) {
                String local = email.substring(0, email.indexOf('@'));
                String domain = email.substring(email.indexOf('@'));
                String masked = (local.length() > 3 ? local.substring(0, 3) : local) + "***" + domain;
                dto.setStudentEmail(masked);
            } else {
                dto.setStudentEmail(email);
            }

            dto.setTotalAmount(order.getTotalAmount());
            dto.setPaymentStatus(order.getPaymentStatus());
            dto.setPlacedAt(order.getPlacedAt());
            dto.setStatus(order.getStatus());
            dto.setPickupTime(order.getPickupTime());

            // Add the items summary to the card
            StringBuilder summary = new StringBuilder();
            for (com.serveflow.entity.OrderItem item : order.getItems()) {
                if (summary.length() > 0) summary.append(", ");
                summary.append(item.getQuantity()).append("x ").append(item.getFoodItem().getName());
            }
            dto.setItemSummary(summary.toString());
            
            dtos.add(dto);
        }

        return ResponseEntity.ok(dtos);
    }

    /**
     * Purpose: Returns the current system settings for the QuickBill settings page.
     * Input:   none.
     * Output:  SettingsResponseDTO with lunch cutoff time, matching window, domain, printer status.
     */
    @GetMapping("/settings")
    public ResponseEntity<SettingsResponseDTO> getSettings() {
        SettingsResponseDTO settings = new SettingsResponseDTO();
        settings.setLunchStartTime(lunchStartTime);
        settings.setMatchingWindowMinutes(matchingWindowMinutes);
        settings.setCollegeEmailDomain(collegeEmailDomain);
        settings.setPrinterStatus(printerService.getPrinterStatus());
        settings.setPrinterHost(printerService.getPrinterHost());
        return ResponseEntity.ok(settings);
    }

    /**
     * Purpose: Updates the lunch cutoff time and matching window from the settings page.
     *          Does NOT require a restart — changes take effect immediately for new operations.
     *          The college email domain is display-only and cannot be changed from the UI.
     * Input:   SettingsUpdateRequestDTO — lunchStartTime and matchingWindowMinutes.
     * Output:  Updated SettingsResponseDTO.
     */
    @PutMapping("/settings")
    public ResponseEntity<SettingsResponseDTO> updateSettings(@RequestBody SettingsUpdateRequestDTO request) {
        if (request.getLunchStartTime() != null) {
            this.lunchStartTime = request.getLunchStartTime();
            onlineOrderService.setLunchStartTimeStr(request.getLunchStartTime());
        }
        if (request.getMatchingWindowMinutes() != null) {
            this.matchingWindowMinutes = request.getMatchingWindowMinutes();
            billingService.setMatchingWindowMinutes(request.getMatchingWindowMinutes());
        }
        if (request.getPrinterHost() != null && !request.getPrinterHost().trim().isEmpty()) {
            printerService.setPrinterHost(request.getPrinterHost().trim());
            updateApplicationProperties("app.printer.host", request.getPrinterHost().trim());
        }

        // Return the updated settings.
        return getSettings();
    }

    /**
     * Purpose: Retrieves all food items for the biller settings dropdown.
     */
    @GetMapping("/food-items")
    public ResponseEntity<List<FoodItem>> getAllFoodItems() {
        return ResponseEntity.ok(foodItemRepository.findAll());
    }

    /**
     * Purpose: Deletes a food item.
     */
    @DeleteMapping("/food-items/{id}")
    public ResponseEntity<String> deleteFoodItem(@PathVariable Long id) {
        if (!foodItemRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        foodItemRepository.deleteById(id);
        return ResponseEntity.ok("Food item deleted successfully");
    }

    /**
     * Purpose: Updates an existing food item.
     * Note: Now handles image updates via multipart form data.
     */
    @PutMapping("/food-items/{id}")
    public ResponseEntity<String> updateFoodItem(
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam("category") String category,
            @RequestParam("price") Double price,
            @RequestParam("quantityAvailable") Integer quantityAvailable,
            @RequestParam("isVeg") Boolean isVeg,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "image", required = false) org.springframework.web.multipart.MultipartFile image) {
        
        return foodItemRepository.findById(id).map(existingItem -> {
            existingItem.setName(name);
            existingItem.setCategory(category);
            existingItem.setPrice(price);
            existingItem.setQuantityAvailable(quantityAvailable);
            existingItem.setIsVeg(isVeg);
            existingItem.setDescription(description);

            if (image != null && !image.isEmpty()) {
                try {
                    java.io.File uploadDir = new java.io.File(System.getProperty("user.dir"), "uploads");
                    if (!uploadDir.exists()) {
                        uploadDir.mkdirs();
                    }
                    String originalFilename = image.getOriginalFilename();
                    String extension = "";
                    if (originalFilename != null && originalFilename.contains(".")) {
                        extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                    }
                    String newFilename = java.util.UUID.randomUUID().toString() + extension;
                    java.io.File destination = new java.io.File(uploadDir, newFilename);
                    image.transferTo(destination);
                    existingItem.setImageUrl("/uploads/" + newFilename);
                } catch (Exception e) {
                    return ResponseEntity.internalServerError().body("Failed to upload image: " + e.getMessage());
                }
            }
            foodItemRepository.save(existingItem);
            return ResponseEntity.ok("Food item updated successfully");
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Purpose: Adds a new food item to the menu, handling image upload.
     * Input:   Multipart form data containing item details and image file.
     * Output:  Success message or error.
     */
    @PostMapping("/food-items")
    public ResponseEntity<String> addFoodItem(
            @RequestParam("name") String name,
            @RequestParam("category") String category,
            @RequestParam("price") Double price,
            @RequestParam("quantityAvailable") Integer quantityAvailable,
            @RequestParam("isVeg") Boolean isVeg,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "image", required = false) org.springframework.web.multipart.MultipartFile image) {

        FoodItem item = new FoodItem();
        item.setName(name);
        item.setCategory(category);
        item.setPrice(price);
        item.setQuantityAvailable(quantityAvailable);
        item.setIsVeg(isVeg);
        item.setDescription(description);

        if (image != null && !image.isEmpty()) {
            try {
                // Ensure uploads directory exists
                java.io.File uploadDir = new java.io.File(System.getProperty("user.dir"), "uploads");
                if (!uploadDir.exists()) {
                    uploadDir.mkdirs();
                }

                // Generate a unique filename
                String originalFilename = image.getOriginalFilename();
                String extension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                }
                String newFilename = java.util.UUID.randomUUID().toString() + extension;

                // Save to uploads folder
                java.io.File destination = new java.io.File(uploadDir, newFilename);
                image.transferTo(destination);

                // Set the URL path mapped in WebConfig
                item.setImageUrl("/uploads/" + newFilename);

            } catch (Exception e) {
                return ResponseEntity.internalServerError().body("Failed to upload image: " + e.getMessage());
            }
        } else {
            // Default placeholder if no image provided
            item.setImageUrl("/images/default-food.jpg");
        }

        foodItemRepository.save(item);
        return ResponseEntity.ok("Food item added successfully");
    }

    private void updateApplicationProperties(String key, String value) {
        try {
            java.io.File file = new java.io.File("src/main/resources/application.properties");
            if (file.exists()) {
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                if (content.contains(key + "=")) {
                    content = content.replaceAll("(?m)^" + key + "=.*", key + "=" + value);
                } else {
                    content += "\n" + key + "=" + value + "\n";
                }
                java.nio.file.Files.write(file.toPath(), content.getBytes());
            }
        } catch (Exception e) {
            // Log but don't fail the request if file writing fails
            System.err.println("Failed to update application.properties: " + e.getMessage());
        }
    }
}
