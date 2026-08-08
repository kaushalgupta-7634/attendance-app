# Smart Attendance System (Spring Boot + Security + JWT + Geofencing)

A modern, location-verified Smart Attendance Web Application built with **Spring Boot 3**, **Spring Security 6**, **JJWT**, **ZXing**, **Spring Data JPA**, and vanilla HTML5/CSS3/JS.

It features **role-based access control** (TEACHERS and STUDENTS), **20-second rotating HMAC QR tokens**, **Haversine formula geofencing verification**, **live camera scanning**, **live 5-second polling feeds**, and **clean JSON error handling**.

---

## 🚀 Features

- 🔐 **JWT Authentication**: Stateless authentication with custom `userId` and `role` claims.
- 👨‍🏫 **Teacher Dashboard**: Create class sessions, view live rotating QR codes every 20 seconds, and monitor student check-ins in real-time.
- 📱 **Student Camera Scanner**: Built-in HTML5 QR scanner with device camera integration and browser GPS location verification.
- 📍 **Geofencing Verification**: Haversine formula distance check ensures students are within the classroom's allowed radius (`radiusMeters`).
- ⚡ **Anti-Cheating Safeguards**: Prevents duplicate attendance marking per session via database unique constraints (`uk_session_student`) and invalidates expired 20s QR tokens.
- ☁️ **Railway Ready**: Environment variable support for Railway MySQL credentials (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`), `Procfile`, and zero-setup H2 in-memory fallback.

---

## 🛠️ Technology Stack

- **Backend**: Java 22, Spring Boot 3.3.2, Spring Security 6, Spring Data JPA, Hibernate, JJWT `0.12.6`, ZXing `3.5.3`
- **Database**: H2 (In-Memory for Dev) / MySQL 8 (Production)
- **Frontend**: HTML5, CSS3 (Glassmorphism & Dark Mode), Vanilla JavaScript, `html5-qrcode`

---

## 📦 Pre-Seeded Test Credentials

The application automatically seeds 1 Teacher and 5 Test Students via `data.sql`:

| Role | Username | Password | Email |
| :--- | :--- | :--- | :--- |
| **TEACHER** | `teacher1` | `password123` | `teacher1@example.com` |
| **STUDENT** | `student1` | `password123` | `alice@example.com` |
| **STUDENT** | `student2` | `password123` | `bob@example.com` |
| **STUDENT** | `student3` | `password123` | `charlie@example.com` |
| **STUDENT** | `student4` | `password123` | `diana@example.com` |
| **STUDENT** | `student5` | `password123` | `evan@example.com` |

---

## ⚙️ Local Setup & Running

### Prerequisites
- JDK 17 or Java 22
- Maven 3.x (or built-in Maven Wrapper)

### Quick Run Steps

1. **Clone or Open the Repository**
   ```bash
   cd Attendance
   ```

2. **Run the Application**
   ```bash
   ./mvnw spring-boot:run
   ```
   *(Or using installed Maven: `mvn spring-boot:run`)*

3. **Open Application in Browser**
   - Main Portal: [http://localhost:8080/](http://localhost:8080/)
   - H2 Console: [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL: `jdbc:h2:mem:attendance_db`, User: `sa`, Password: *(leave blank)*)

---

## 🧪 End-to-End Testing Workflow

1. **Teacher Flow**:
   - Go to [http://localhost:8080/login.html](http://localhost:8080/login.html)
   - Log in with `teacher1` / `password123` $\rightarrow$ Redirects to `teacher-dashboard.html`.
   - Click **Detect Current Location (GPS)** or leave coordinates default.
   - Click **Launch Active Session**.
   - Watch the live QR code rotate automatically every 20 seconds with a countdown timer.

2. **Student Flow**:
   - Open a separate browser tab or mobile window at [http://localhost:8080/login.html](http://localhost:8080/login.html).
   - Log in with `student1` / `password123` $\rightarrow$ Redirects to `student-scan.html`.
   - Allow location permissions when prompted.
   - Click **Start Camera Scanner** to scan the QR code from the teacher dashboard (or paste token into the manual input box).
   - Observe the instant **PRESENT** confirmation card!

3. **Live Attendance Monitoring**:
   - Switch back to the **Teacher Dashboard** tab.
   - Observe the live attendance table update automatically within 5 seconds displaying `student1` as `PRESENT`.

---

## 📡 REST API Specifications

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/auth/register` | Public | Register a new user (`TEACHER` or `STUDENT`) |
| `POST` | `/auth/login` | Public | Authenticate user and return JWT bearer token |
| `POST` | `/sessions/start` | `TEACHER` | Launch a new class session (teacherId extracted from JWT) |
| `GET` | `/sessions/{id}/qr` | `TEACHER` (Owner) | Get current rotating 20s QR code PNG image |
| `GET` | `/sessions/{id}/attendance` | `TEACHER` (Owner) | Get live list of marked student attendance records |
| `POST` | `/attendance/mark` | `STUDENT` | Submit QR token & GPS coordinates to mark attendance |

---

## ☁️ Production Deployment (Railway)

Set the following environment variables in your Railway project settings:

- `SPRING_DATASOURCE_URL` (injected by Railway MySQL plugin)
- `SPRING_DATASOURCE_USERNAME` (injected by Railway MySQL plugin)
- `SPRING_DATASOURCE_PASSWORD` (injected by Railway MySQL plugin)
- `APP_JWT_SECRET` (e.g. 64-character hex key)
- `APP_QR_SECRET` (e.g. 64-character hex key)

Railway uses the root [`Procfile`](file:///c:/Users/kaush/Attendence/Procfile) to start the production jar:
```procfile
web: java -jar target/attendance-0.0.1-SNAPSHOT.jar
```
