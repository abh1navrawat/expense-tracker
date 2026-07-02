# Fintrack — Premium Multi-Tenant Expense Tracker

Fintrack is a full-stack, enterprise-grade multi-tenant expense ledger designed with a premium, modern dark-mode aesthetic inspired by **shadcn/ui & 21st.dev**. It features secure JWT cookie authentication, robust data isolation, automated forex currency conversion (defaulting to INR), interactive visual analytics with Chart.js, and background-compiled Excel reports using Apache POI.

---

## Key Features
- 🔒 **Stateless JWT Cookie Authentication:** Securely packages JWT tokens in HttpOnly, secure cookies for seamless MVC page transitions.
- 🎨 **Premium Modern Design System:** Soft Zinc / Cozy Slate dark theme, organic borders, bouncy interactive transitions, and responsive collapsable sidebar navigation.
- 🇮🇳 **Automated Forex Currency Converter:** Pre-selects INR (₹) as the default currency and converts foreign currency statements dynamically to the user's base currency using live external rate APIs.
- 📊 **Financial Cockpit & Visual Analytics:** Real-time summary dashboard cards (Month/Cumulative Spend) with interactive Chart.js analytics.
- 📂 **Background POI Excel Compiler:** Generates formatted multi-sheet Excel workbooks asynchronously in the background.
- 🛡️ **Multi-Tenant Separation & Safety Constraints:** Safe user self-deletion and safety-constrained administrator panel allowing admins to delete portfolios (preventing self-deletion of the active admin).

---

## Technology Stack
- **Backend:** Java 17, Spring Boot 3.x, Spring Security 6, Spring Data JPA
- **Database:** PostgreSQL
- **Frontend Template Engine:** Thymeleaf, Vanilla CSS, HTML5 semantic layout
- **Visuals & Reports:** Chart.js, Apache POI (Excel)
- **Deployment:** Docker (multi-stage build), Render/Railway compatible

---

## Local Setup & Installation

### Prerequisites
- Java Development Kit (JDK) 17 or higher
- Apache Maven
- PostgreSQL running locally (default port `5432`)

### 1. Database Setup
Create a PostgreSQL database named `expense_tracker`:
```sql
CREATE DATABASE expense_tracker;
```

### 2. Configure Environment Properties
Create or edit `src/main/resources/application.properties`:
```properties
# Server Port
server.port=8080

# PostgreSQL Settings
spring.datasource.url=jdbc:postgresql://localhost:5432/expense_tracker
spring.datasource.username=postgres
spring.datasource.password=your_db_password

# JWT Settings
jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
jwt.expiration=86400000
```

### 3. Run Locally (Maven)
Run the application using the Maven Wrapper:
```bash
./mvnw spring-boot:run
```
Visit `http://localhost:8080` in your web browser.

---

## Production Deployment (Docker / Render)

This repository includes a production-ready multi-stage `Dockerfile`.

### 1. Build & Run Container Locally
```bash
# Build the Docker image
docker build -t expense-tracker .

# Run the container
docker run -p 8080:8080 -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:port/dbname -e SPRING_DATASOURCE_USERNAME=user -e SPRING_DATASOURCE_PASSWORD=pass expense-tracker
```

### 2. Direct Deploy to Render / Railway
1. Create a **PostgreSQL Database** on Render.
2. Create a new **Web Service** on Render, connecting your GitHub repository.
3. Select **Docker** as the Runtime.
4. Add the following **Environment Variables**:
   - `SPRING_DATASOURCE_URL`: `jdbc:postgresql://<external-db-host>/<db-name>?sslmode=require`
   - `SPRING_DATASOURCE_USERNAME`: `<db-username>`
   - `SPRING_DATASOURCE_PASSWORD`: `<db-password>`
