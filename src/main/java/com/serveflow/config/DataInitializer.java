package com.serveflow.config;

import com.serveflow.entity.*;
import com.serveflow.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * DataInitializer — seeds the database with essential startup data.
 *
 * Runs automatically once at application startup (implements CommandLineRunner).
 * All operations are idempotent — they check before inserting to avoid duplicates
 * on restart. Safe to restart the application multiple times.
 *
 * WHAT IT SEEDS:
 *   1. TokenSequence row (id=1, lastUsedNumber=0) — required for token generation.
 *   2. AdminUser (biller account) with username "admin" and BCrypt password.
 *   3. Sample FoodItems for the student menu and biller dropdown.
 *
 * HOW TO CHANGE THE DEFAULT PASSWORD:
 *   Update the plaintext password below before first launch.
 *   After the first launch, the BCrypt hash is stored in the DB — changing this
 *   code after that has no effect (idempotency check skips re-seeding).
 *   To reset the password, DELETE the admin_users row from the DB and restart.
 *
 * IMPORTANT: In production, the admin password should be set via an environment
 * variable or a one-time setup endpoint, not hardcoded here. This is documented
 * as a Phase 8 improvement. For now, the default is fine for college canteen use.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final TokenSequenceRepository tokenSequenceRepository;
    private final AdminUserRepository adminUserRepository;
    private final FoodItemRepository foodItemRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(TokenSequenceRepository tokenSequenceRepository,
                           AdminUserRepository adminUserRepository,
                           FoodItemRepository foodItemRepository,
                           PasswordEncoder passwordEncoder) {
        this.tokenSequenceRepository = tokenSequenceRepository;
        this.adminUserRepository = adminUserRepository;
        this.foodItemRepository = foodItemRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        log.info("DataInitializer: Starting startup data seeding...");

        seedTokenSequence();
        seedAdminUser();
        seedFoodItems();

        log.info("DataInitializer: Seeding complete.");
    }

    // ── SEED 1: TOKEN SEQUENCE ────────────────────────────────────────────────────

    /**
     * Ensures the single TokenSequence row (id=1) exists.
     * This row is the counter used by TokenService to generate sequential token numbers.
     * Without this row, the application will throw an error on first token generation.
     */
    private void seedTokenSequence() {
        if (tokenSequenceRepository.findById(1L).isEmpty()) {
            TokenSequence sequence = new TokenSequence(1L, 0L);
            tokenSequenceRepository.save(sequence);
            log.info("DataInitializer: TokenSequence row created (id=1, lastUsedNumber=0).");
        } else {
            log.info("DataInitializer: TokenSequence row already exists. Skipping.");
        }
    }

    // ── SEED 2: ADMIN USER (BILLER ACCOUNT) ──────────────────────────────────────

    /**
     * Seeds the default biller (admin) account.
     * Credentials:
     *   username: admin
     *   password: admin123   ← CHANGE THIS BEFORE DEMO / PRODUCTION USE
     *
     * The password is BCrypt-hashed before storing — never stored in plain text.
     */
    private void seedAdminUser() {
        if (adminUserRepository.findByUsername("admin").isEmpty()) {
            AdminUser admin = new AdminUser();
            admin.setUsername("admin");
            // BCrypt hash of "admin123" — generated at runtime, not hardcoded hash.
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole("ROLE_BILLER");
            admin.setPhone(null); // not required for current phase
            adminUserRepository.save(admin);
            log.info("DataInitializer: Admin user 'admin' created with default password.");
            log.warn("DataInitializer: ⚠ Default password is 'admin123'. Change it before any real deployment!");
        } else {
            log.info("DataInitializer: Admin user already exists. Skipping.");
        }
    }

    // ── SEED 3: FOOD ITEMS (CANTEEN MENU) ────────────────────────────────────────

    /**
     * Seeds the initial canteen menu items.
     * Only runs if the food_items table is completely empty — so a biller who deletes
     * items won't have them re-seeded on restart.
     * Images reference filenames from src/main/resources/static/images/.
     */
    private void seedFoodItems() {
        if (foodItemRepository.count() > 0) {
            log.info("DataInitializer: Food items already exist. Skipping menu seed.");
            return;
        }

        log.info("DataInitializer: Seeding food items...");

        // ── VEG ITEMS ─────────────────────────────────────────────────────────────
        FoodItem idlySambar = new FoodItem();
        idlySambar.setName("Idly Sambar");
        idlySambar.setCategory("Veg");
        idlySambar.setPrice(25.0);
        idlySambar.setImageUrl("/images/idly-sambar.jpg");
        idlySambar.setQuantityAvailable(50);
        idlySambar.setIsVeg(true);
        idlySambar.setDescription("Soft idlies served with hot sambar and coconut chutney");
        foodItemRepository.save(idlySambar);

        FoodItem vegFriedRice = new FoodItem();
        vegFriedRice.setName("Veg Fried Rice");
        vegFriedRice.setCategory("Veg");
        vegFriedRice.setPrice(60.0);
        vegFriedRice.setImageUrl("/images/veg-fried-rice.jpg");
        vegFriedRice.setQuantityAvailable(30);
        vegFriedRice.setIsVeg(true);
        vegFriedRice.setDescription("Wok-tossed rice with mixed vegetables and soy sauce");
        foodItemRepository.save(vegFriedRice);

        FoodItem chapati = new FoodItem();
        chapati.setName("Chapati with Sabji");
        chapati.setCategory("Veg");
        chapati.setPrice(40.0);
        chapati.setImageUrl("/images/chapati-sabji.jpg");
        chapati.setQuantityAvailable(40);
        chapati.setIsVeg(true);
        chapati.setDescription("Soft wheat chapatis with seasonal vegetable curry");
        foodItemRepository.save(chapati);

        FoodItem vegNoodles = new FoodItem();
        vegNoodles.setName("Veg Noodles");
        vegNoodles.setCategory("Veg");
        vegNoodles.setPrice(55.0);
        vegNoodles.setImageUrl("/images/veg-noodles.jpg");
        vegNoodles.setQuantityAvailable(25);
        vegNoodles.setIsVeg(true);
        vegNoodles.setDescription("Stir-fried noodles with colorful vegetables and Indo-Chinese sauces");
        foodItemRepository.save(vegNoodles);

        FoodItem samosaChole = new FoodItem();
        samosaChole.setName("Samosa with Chole");
        samosaChole.setCategory("Veg");
        samosaChole.setPrice(30.0);
        samosaChole.setImageUrl("/images/samosa-chole.jpg");
        samosaChole.setQuantityAvailable(60);
        samosaChole.setIsVeg(true);
        samosaChole.setDescription("Crispy fried pastry stuffed with spiced potatoes, served with chickpea curry");
        foodItemRepository.save(samosaChole);

        FoodItem paneerRice = new FoodItem();
        paneerRice.setName("Paneer Fried Rice");
        paneerRice.setCategory("Veg");
        paneerRice.setPrice(70.0);
        paneerRice.setImageUrl("/images/paneer-fried-rice.jpg");
        paneerRice.setQuantityAvailable(20);
        paneerRice.setIsVeg(true);
        paneerRice.setDescription("Aromatic fried rice with cottage cheese cubes and vegetables");
        foodItemRepository.save(paneerRice);

        // ── NON-VEG ITEMS ─────────────────────────────────────────────────────────
        FoodItem chickenFriedRice = new FoodItem();
        chickenFriedRice.setName("Chicken Fried Rice");
        chickenFriedRice.setCategory("Non-Veg");
        chickenFriedRice.setPrice(80.0);
        chickenFriedRice.setImageUrl("/images/chicken-fried-rice.jpg");
        chickenFriedRice.setQuantityAvailable(25);
        chickenFriedRice.setIsVeg(false);
        chickenFriedRice.setDescription("Wok-fried rice with tender chicken pieces and Indo-Chinese spices");
        foodItemRepository.save(chickenFriedRice);

        FoodItem chickenNoodles = new FoodItem();
        chickenNoodles.setName("Chicken Noodles");
        chickenNoodles.setCategory("Non-Veg");
        chickenNoodles.setPrice(75.0);
        chickenNoodles.setImageUrl("/images/chicken-noodles.jpg");
        chickenNoodles.setQuantityAvailable(20);
        chickenNoodles.setIsVeg(false);
        chickenNoodles.setDescription("Stir-fried noodles with succulent chicken and spicy sauces");
        foodItemRepository.save(chickenNoodles);

        FoodItem eggRice = new FoodItem();
        eggRice.setName("Egg Fried Rice");
        eggRice.setCategory("Non-Veg");
        eggRice.setPrice(65.0);
        eggRice.setImageUrl("/images/egg-fried-rice.jpg");
        eggRice.setQuantityAvailable(30);
        eggRice.setIsVeg(false);
        eggRice.setDescription("Classic fried rice with scrambled eggs, vegetables, and soy sauce");
        foodItemRepository.save(eggRice);

        FoodItem chickenBiryani = new FoodItem();
        chickenBiryani.setName("Chicken Biryani");
        chickenBiryani.setCategory("Non-Veg");
        chickenBiryani.setPrice(100.0);
        chickenBiryani.setImageUrl("/images/chicken-biryani.jpg");
        chickenBiryani.setQuantityAvailable(15);
        chickenBiryani.setIsVeg(false);
        chickenBiryani.setDescription("Fragrant basmati rice cooked with spiced chicken and caramelized onions");
        foodItemRepository.save(chickenBiryani);

        // ── BEVERAGES ─────────────────────────────────────────────────────────────
        FoodItem tea = new FoodItem();
        tea.setName("Tea");
        tea.setCategory("Beverages");
        tea.setPrice(10.0);
        tea.setImageUrl("/images/tea.jpg");
        tea.setQuantityAvailable(100);
        tea.setIsVeg(true);
        tea.setDescription("Hot freshly brewed milk tea with ginger");
        foodItemRepository.save(tea);

        FoodItem coffee = new FoodItem();
        coffee.setName("Coffee");
        coffee.setCategory("Beverages");
        coffee.setPrice(15.0);
        coffee.setImageUrl("/images/coffee.jpg");
        coffee.setQuantityAvailable(100);
        coffee.setIsVeg(true);
        coffee.setDescription("Strong South Indian filter coffee with milk");
        foodItemRepository.save(coffee);

        FoodItem lemonade = new FoodItem();
        lemonade.setName("Lemon Juice");
        lemonade.setCategory("Beverages");
        lemonade.setPrice(20.0);
        lemonade.setImageUrl("/images/lemon-juice.jpg");
        lemonade.setQuantityAvailable(50);
        lemonade.setIsVeg(true);
        lemonade.setDescription("Fresh squeezed lemon juice with sugar and a pinch of salt");
        foodItemRepository.save(lemonade);

        FoodItem lassi = new FoodItem();
        lassi.setName("Sweet Lassi");
        lassi.setCategory("Beverages");
        lassi.setPrice(25.0);
        lassi.setImageUrl("/images/lassi.jpg");
        lassi.setQuantityAvailable(40);
        lassi.setIsVeg(true);
        lassi.setDescription("Creamy chilled yoghurt drink sweetened with sugar");
        foodItemRepository.save(lassi);

        // ── SNACKS ────────────────────────────────────────────────────────────────
        FoodItem veg_puff = new FoodItem();
        veg_puff.setName("Veg Puff");
        veg_puff.setCategory("Snacks");
        veg_puff.setPrice(15.0);
        veg_puff.setImageUrl("/images/veg-puff.jpg");
        veg_puff.setQuantityAvailable(80);
        veg_puff.setIsVeg(true);
        veg_puff.setDescription("Flaky pastry filled with spiced mixed vegetables");
        foodItemRepository.save(veg_puff);

        FoodItem chickenPuff = new FoodItem();
        chickenPuff.setName("Chicken Puff");
        chickenPuff.setCategory("Snacks");
        chickenPuff.setPrice(20.0);
        chickenPuff.setImageUrl("/images/chicken-puff.jpg");
        chickenPuff.setQuantityAvailable(60);
        chickenPuff.setIsVeg(false);
        chickenPuff.setDescription("Flaky pastry filled with spiced chicken mince");
        foodItemRepository.save(chickenPuff);

        log.info("DataInitializer: Seeded {} food items.", foodItemRepository.count());
    }
}
