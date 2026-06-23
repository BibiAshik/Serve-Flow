# ServeFlow

Automated Canteen Billing & Token Platform. 
ServeFlow consists of two main interfaces:
1. **QuickBill** (Biller Portal): A real-time, responsive billing dashboard for canteen staff to create bills, generate tokens, and match UPI payments automatically.
2. **Campus Bite** (Student Portal): A mobile-first web app for students to browse the menu, add items to their cart, pay online using Razorpay, and track their tokens live.

## Features
- **Real-Time Payment Matching**: Matches Razorpay webhook events to walk-in bills automatically based on amounts and time windows.
- **Ambiguous Match Resolution**: Handles cases where multiple identical payments arrive simultaneously by prompting the biller to resolve using the last 4 digits of the UPI reference.
- **Thermal Printer Integration**: Connects directly to ESC/POS network thermal printers to print tokens on the fly.
- **Google OAuth2**: Students login exclusively using their `@sairamtap.edu.in` Google accounts.
- **JWT Authentication**: Biller portal uses secure JWT-based authentication.
- **Live Polling & UI Updates**: The frontend fetches live status updates automatically for a seamless experience.

## Tech Stack
- **Backend**: Spring Boot 3, Spring Data JPA, Spring Security (OAuth2 & JWT), Hibernate, MySQL
- **Frontend**: HTML5, CSS3, Vanilla JavaScript (No heavy frameworks)
- **Payment Gateway**: Razorpay
- **Build Tool**: Maven

## Setup & Running Locally

1. **Database Configuration**
   - Create a MySQL database named `serveflow_db`.
   - Update `src/main/resources/application.properties` with your MySQL `root` password:
     ```properties
     spring.datasource.username=root
     spring.datasource.password=YOUR_PASSWORD
     ```

2. **Configure API Keys**
   - Update `application.properties` with your Google OAuth2 Client ID and Secret.
   - Update your Razorpay Test Key ID, Secret, and Webhook Secret.
   - Generate a strong random string for `jwt.secret` (at least 32 characters).

3. **Running the Application**
   ```sh
   ./mvnw spring-boot:run
   ```
   Or using Docker Compose:
   ```sh
   docker-compose up --build
   ```

## Development
- Dev mode is enabled by default in `application.properties` (`app.dev-mode=true`). This exposes a testing endpoint (`/api/dev/simulate-payment`) to simulate Razorpay webhooks during local testing without needing a public ngrok URL.
- Ensure to set `app.dev-mode=false` in production.

## Accessing Portals
- **Student Portal**: `http://localhost:8080/student/home`
- **Biller Portal**: `http://localhost:8080/biller/login`
