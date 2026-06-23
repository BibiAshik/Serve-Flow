package com.serveflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * ServeFlowApplication — Entry point for the ServeFlow Spring Boot application.
 *
 * ServeFlow is a two-portal college canteen billing and token platform:
 *   - Campus Bite  (student-facing): online pre-order via Google OAuth2 + Razorpay
 *   - QuickBill    (biller-facing):  walk-in counter billing with payment-matching engine
 *
 * GitHub / resume name: ServeFlow
 * Student portal shown to students: Campus Bite
 * Biller portal shown to canteen staff: QuickBill
 */
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ServeFlowApplication {

    private static final Logger log = LoggerFactory.getLogger(ServeFlowApplication.class);

    // Read the dev-mode flag from application.properties.
    // defaultValue = "false" means if the property is missing, dev mode is OFF (safe default).
    @Value("${app.dev-mode:false}")
    private boolean devMode;

    public static void main(String[] args) {
        SpringApplication.run(ServeFlowApplication.class, args);
    }

    /**
     * Runs once the application is fully started and ready to accept requests.
     * Prints a prominent warning if dev mode is active, because
     * /api/dev/simulate-payment must NEVER be reachable in production.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (devMode) {
            // This warning is intentionally loud — the developer must see it.
            log.warn("===========================================================");
            log.warn("⚠  DEV MODE ACTIVE — /api/dev/simulate-payment is ENABLED.");
            log.warn("   This endpoint bypasses Razorpay signature verification.");
            log.warn("   Set app.dev-mode=false before any real deployment!");
            log.warn("===========================================================");
        } else {
            log.info("✅ ServeFlow started in PRODUCTION mode. Dev endpoint disabled.");
        }
        log.info("🚀 ServeFlow is running on http://localhost:8080");
        log.info("   Campus Bite (students) : http://localhost:8080/student/login");
        log.info("   QuickBill  (biller)    : http://localhost:8080/biller/login");
    }
}
