<div align="center">
  <img src="src/main/resources/static/images/Serve_Flow_Logo.png" alt="ServeFlow Logo" width="200" />
  <h1>Serve Flow</h1>
  <p><strong>A Next-Generation Automated Canteen Billing & Token Management Platform</strong></p>
</div>

<br />

ServeFlow is a comprehensive, dual-portal web application designed to eliminate canteen queues, automate UPI payment matching, and seamlessly bridge the gap between walk-in customers and online pre-orders.

---

## 📸 Screenshots



<img width="800" height="500" alt="image" src="https://github.com/user-attachments/assets/d5a5c98d-446f-454d-996d-263e24a4cbd8" />

<img width="800" height="500" alt="Screenshot 2026-06-25 000610" src="https://github.com/user-attachments/assets/f7d8129b-38b9-4643-b8ae-ccebdb5dde11" />

<br />

> **QuickBill Dashboard** 
> 

> <img width="800" height="500" alt="Screenshot 2026-06-25 000421" src="https://github.com/user-attachments/assets/01ba43ec-f893-4cb8-b0c0-1506cc948b02" />
<img width="800" height="500" alt="Screenshot 2026-06-25 000555" src="https://github.com/user-attachments/assets/5cd08492-fd3a-4f6b-a761-812cb2b0654a" />

<br />

> **Campus Bite Mobile App**
> 
> <img width="800" height="500" alt="Screenshot 2026-06-25 000339" src="https://github.com/user-attachments/assets/770cb1fc-5c17-4fb0-ad99-84f74adaaa71" />
<img width="350" height="700" alt="image" src="https://github.com/user-attachments/assets/b98b1c37-e310-45b4-b7c4-aa863549e4d4" />
<img width="350" height="700" alt="image" src="https://github.com/user-attachments/assets/1b4d4431-1f43-4721-b011-4527699962d4" />


---

## 🚀 Key Features

### 🏢 QuickBill (Biller Portal)
- **Live UPI Matching Engine**: Instantly and automatically matches walk-in Razorpay static QR payments to generated bills using dynamic time windows.
- **Ambiguous Match Resolution**: Intelligently detects identical payments arriving simultaneously and prompts the biller to resolve them via the last 4 digits of the UPI reference.
- **ESC/POS Thermal Printing**: Connects directly to local network thermal receipt printers via TCP sockets for instant, hardware-level token printing.
- **Virtual Print Fallback**: Automatically provides an on-screen, perfectly isolated printable browser token if the physical printer goes offline.
- **Live Dashboard**: Auto-polling UI that updates the status bar, recent tokens, and pending payments instantly without refreshing.

### 🎓 Campus Bite (Student Portal)
- **Mobile-First Experience**: A beautifully crafted, responsive UI specifically designed for students on the go.
- **Google OAuth2 Security**: Strict authentication allowing only students with `@sairamtap.edu.in` accounts to log in.
- **Online Pre-Ordering**: Students can browse the menu, add to cart, and checkout online.
- **Razorpay Integration**: Flawless online payment capture and signature verification to guarantee secure transactions.
- **Token Tracking**: Live "My Orders" dashboard tracking order status from PAID to SERVED.

---

## 💻 Tech Stack

**Backend**
- **Java 17 & Spring Boot 3**: High-performance backend REST APIs.
- **Spring Security**: JWT-based stateless authentication and Google OAuth2 integration.
- **Spring Data JPA & Hibernate**: Robust ORM layer connecting to MySQL.
- **Razorpay SDK**: Webhook signature verification and order creation.
- **Escpos-Coffee**: Hardware-level thermal printer integration.

**Frontend**
- **HTML5 & CSS3**: Pure, lightweight, dependency-free vanilla frontend.
- **Vanilla JavaScript**: ES6+ modules fetching live data and handling UI states.

---

## 🛠️ Setup & Installation

### 1. Database Setup
Ensure you have MySQL running locally. Create a new database:
```sql
CREATE DATABASE serveflow_db;
```

### 2. Environment Variables
Configure your `src/main/resources/application-local.properties` file with the following keys:
```properties
# Database
spring.datasource.username=root
spring.datasource.password=your_mysql_password

# JWT Security
jwt.secret=generate_a_very_long_secure_random_string_here

# Google OAuth2
spring.security.oauth2.client.registration.google.client-id=your_client_id
spring.security.oauth2.client.registration.google.client-secret=your_client_secret

# Razorpay
razorpay.key.id=your_razorpay_key
razorpay.key.secret=your_razorpay_secret
razorpay.webhook.secret=your_webhook_secret

# Thermal Printer
app.printer.host=localhost
app.printer.port=9100
```

### 3. Run the Application
You can run the application directly using Maven:
```bash
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`.

---

## 🌐 Accessing the Portals

- **Biller Login**: `http://localhost:8080/biller/login`
- **Student Portal**: `http://localhost:8080/student/home`

---
*Developed with ❤️ to modernize campus dining.*
