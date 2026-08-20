# ATTENDX - Team Presentation & Code Walkthrough Guide

This document serves as a presentation script and technical sitemap for you to explain the **ATTENDX** codebase and system architecture to your team members.

---

## 1. Project Overview (High-Level)
**What it is:**
> "ATTENDX is a smart, geo-fenced QR attendance management system built using Spring Boot, Spring Security, and a vanilla HTML/CSS/JS frontend. It is designed to track student attendance securely and prevent common cheating methods (such as scanning from home or sharing QR codes)."

---

## 2. Key Architecture Pillars

### 🔑 Authentication & Authorization
* **Technology:** Stateless JSON Web Tokens (JWT) + Spring Security.
* **Explanation:**
  > "Instead of storing user sessions in server memory, we use stateless JWTs. When a user logs in, the server generates a token containing their username, ID, and role (STUDENT, TEACHER, ADMIN). The browser sends this token in the `Authorization` header for subsequent requests. This makes the system scalable and secure against CSRF attacks."
* **Core File:** [SecurityConfig.java](file:///c:/Users/kaush/Attendence/src/main/java/com/example/attendance/config/SecurityConfig.java) (defines public vs private routes).

### 📧 Mailing Subsystem
* **Technology:** Brevo HTTP REST API (HTTPS Port 443).
* **Explanation:**
  > "Many cloud hosting platforms (like Railway) block outbound SMTP ports (25, 465, 587) by default. To bypass this, we use the Brevo HTTP API to send verification emails and password reset OTPs over secure port 443, ensuring 100% email delivery."
* **Core File:** [EmailService.java](file:///c:/Users/kaush/Attendence/src/main/java/com/example/attendance/service/EmailService.java) (handles HTTP client payloads).

### ⚙️ Transaction Management
* **Technology:** Spring's `@Transactional` annotation.
* **Explanation:**
  > "We ensure database consistency. For example, during registration, if the email service fails to send the verification link (due to invalid emails or api issues), the entire database transaction is rolled back so that no half-created/corrupt user account persists in the system."
* **Core File:** [AuthService.java](file:///c:/Users/kaush/Attendence/src/main/java/com/example/attendance/service/AuthService.java) (marked Transactional).

---

## 3. Anti-Cheating & Proxy Prevention (The "Cool" Part)

Explain these three features to highlight the engineering strength of the project:

### 📍 1. Geofencing (Haversine Formula)
* **Explanation:**
  > "To stop students from marking attendance from their hostels or homes, the system verifies their physical coordinates. The backend uses the **Haversine Formula** to calculate the exact distance in meters between the student's GPS location and the teacher's classroom coordinates. If the student is outside the allowed radius (e.g. 50 meters), the attendance is rejected."
* **Core Code:** `calculateHaversineMeters` in [AttendanceService.java](file:///c:/Users/kaush/Attendence/src/main/java/com/example/attendance/service/AttendanceService.java#L249-L258).

### 📱 2. Single-Device Restraint (Fingerprinting)
* **Explanation:**
  > "To prevent proxy attendance (where one student scans for multiple absent friends), the frontend generates a unique browser fingerprint. The backend checks if this device signature has already been used to mark attendance for another student in the current active session. If it has, the proxy attempt is blocked."
* **Core Code:** Device and Fingerprint checks in [AttendanceService.java](file:///c:/Users/kaush/Attendence/src/main/java/com/example/attendance/service/AttendanceService.java#L182-L217).

### ⏱️ 3. Live Token Rotation
* **Explanation:**
  > "To prevent students from taking a photo of the QR code and sharing it on WhatsApp, the QR code rotates every **15 seconds** and the 6-digit passcode rotates every **30 seconds**. The backend verifies that the scanned token hash matches the active window time."

---

## 4. Key Code Files & Walkthrough Script

| Class / File | What it does (Your talking points) |
| :--- | :--- |
| **[DataInitializer.java](file:///c:/Users/kaush/Attendence/src/main/java/com/example/attendance/config/DataInitializer.java)** | "Seeds the database with admin accounts on startup. We modified it so that it **only** updates the admin password if the environment variables change, preventing manual admin password updates from getting overwritten on server restarts." |
| **[AuthService.java](file:///c:/Users/kaush/Attendence/src/main/java/com/example/attendance/service/AuthService.java)** | "Handles core login, registration, OTP generation, and password resets. It features a dual password reset flow: via email OTP, or via the user's private 4-digit Security PIN." |
| **[AttendanceService.java](file:///c:/Users/kaush/Attendence/src/main/java/com/example/attendance/service/AttendanceService.java)** | "The core engine of the system. It handles QR validation, Geofencing calculations, device checks, and writes attendance status records (PRESENT/ABSENT)." |
| **[ClassCourseService.java](file:///c:/Users/kaush/Attendence/src/main/java/com/example/attendance/service/ClassCourseService.java)** | "Enforces enrollment rules. We implemented strict department validation so students registered in `BBA` cannot join `BCA` class codes." |
| **[login.html](file:///c:/Users/kaush/Attendence/src/main/resources/static/login.html)** | "The gateway interface. It features instant redirect to OTP verification, and smart autofill override that automatically clears and updates inputs with the newly verified/reset username." |
| **[forgot-password.html](file:///c:/Users/kaush/Attendence/src/main/resources/static/forgot-password.html)** | "Provides a tab-switcher UI offering users two modes of password resets (PIN or email OTP) in a single page." |
