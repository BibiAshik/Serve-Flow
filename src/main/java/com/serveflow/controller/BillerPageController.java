package com.serveflow.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * BillerPageController — serves the QuickBill HTML pages to the browser.
 *
 * These are Thymeleaf template routes — they return page names (not JSON).
 * Spring Boot resolves them from src/main/resources/templates/biller/*.html
 *
 * NOTE: The actual security for biller operations is on the API endpoints,
 * not the page routes. The page HTML itself only shows UI — the API calls
 * from billing.js / history.js / settings.js carry the JWT and are secured.
 *
 * PAGES:
 *   /biller/login    → login.html    (username + password form → JWT)
 *   /biller/billing  → billing.html  (main QuickBill screen — 3-column layout)
 *   /biller/history  → history.html  (transaction history sidebar)
 *   /biller/orders   → orders.html   (Campus Bite online orders feed)
 *   /biller/settings → settings.html (lunch cutoff + matching window config)
 */
@Controller
@RequestMapping("/biller")
public class BillerPageController {

    @GetMapping("/login")
    public String billerLoginPage() {
        return "biller/login"; // resolves to templates/biller/login.html
    }

    @GetMapping("/billing")
    public String billingPage() {
        return "biller/billing"; // resolves to templates/biller/billing.html
    }

    @GetMapping("/history")
    public String historyPage() {
        return "biller/history"; // resolves to templates/biller/history.html
    }

    @GetMapping("/orders")
    public String ordersPage() {
        return "biller/orders"; // resolves to templates/biller/orders.html
    }

    @GetMapping("/settings")
    public String settingsPage() {
        return "biller/settings"; // resolves to templates/biller/settings.html
    }
}
