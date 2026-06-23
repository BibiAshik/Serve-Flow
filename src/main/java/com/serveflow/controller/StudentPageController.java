package com.serveflow.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * StudentPageController — serves the Campus Bite HTML pages to the browser.
 *
 * These are Thymeleaf template routes — they return page names (not JSON).
 * Spring Boot resolves them from src/main/resources/templates/student/*.html
 *
 * The student login page is public — anyone can view it.
 * Home, checkout, and my-orders pages load via the browser, but their actual
 * API calls (/api/online/**) require a valid ROLE_STUDENT JWT.
 *
 * PAGES:
 *   /student/login     → login.html     (Google OAuth2 login button)
 *   /student/home      → home.html      (food menu + cart + checkout flow)
 *   /student/checkout  → checkout.html  (cart review + Razorpay modal)
 *   /student/my-orders → my-orders.html (order history + token display)
 */
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;

@Controller
@RequestMapping("/student")
public class StudentPageController {

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @GetMapping("/login")
    public String studentLoginPage() {
        return "student/login"; // resolves to templates/student/login.html
    }

    @GetMapping("/home")
    public String studentHomePage(Model model) {
        model.addAttribute("razorpayKeyId", razorpayKeyId);
        return "student/home"; // resolves to templates/student/home.html
    }

    @GetMapping("/checkout")
    public String checkoutPage(Model model) {
        model.addAttribute("razorpayKeyId", razorpayKeyId);
        return "student/checkout"; // resolves to templates/student/checkout.html
    }

    @GetMapping("/my-orders")
    public String myOrdersPage() {
        return "student/my-orders"; // resolves to templates/student/my-orders.html
    }

    @GetMapping("/profile")
    public String profilePage() {
        return "student/profile"; // resolves to templates/student/profile.html
    }

    @GetMapping("/favorites")
    public String favoritesPage() {
        return "student/favorites"; // resolves to templates/student/favorites.html
    }
}
