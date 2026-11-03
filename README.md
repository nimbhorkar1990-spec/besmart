# BeSmart – Enterprise Search Engine & Career Solutions Platform

[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Apache Lucene](https://img.shields.io/badge/Apache%20Lucene-9.11.1-orange.svg)](https://lucene.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)
[![GitHub Repository](https://img.shields.io/badge/GitHub-besmart-181717.svg?logo=github)](https://github.com/nimbhorkar1990-spec/besmart)

**BeSmart** is a modern, enterprise-grade search engine and career talent platform built with **Java 17**, **Spring Boot 3**, and **Apache Lucene 9**. Combining high-performance inverted index search algorithms, a distributed crawler with SSRF protection, multi-theme UI personalization, and a regulatory-verified Employment & Recruitment Portal.

---

## 🌟 Key Features & Highlights

### 1. Modern Search Engine Homepage & Navigation
- **Google / Bing Inspired Aesthetics:** Minimalist hero layout with electric indigo accents, responsive grid, and smooth CSS animations.
- **Top Navigation Menu:**
  - **Home**: Instant reset and quick trending chips.
  - **Services Dropdown**: Direct navigation to platform services including **1. Employment & Careers Portal**.
- **Unified Theme Selector Dropdown:** Available across every single webpage:
  1. ☀️ **Light Mode** (Crisp clean white & indigo)
  2. 🌙 **Dark Mode** (Deep charcoal & cyan)
  3. ⚡ **Cyberpunk Neon** (Dark slate with neon violet, electric cyan, and hot pink accents)
  4. 🌲 **Forest Emerald** (Deep forest green, mint, and sage highlights)
  5. ☕ **Warm Sepia** (Cozy amber, warm parchment, and terracotta)
  6. ❄️ **Nordic Frost** (Arctic blue, icy steel, and glacier accents)
  7. 👑 **Luxury Gold** (Midnight obsidian with polished champagne gold)
- **Voice Search UI:** Browser-native **Web Speech API** integration with animated pulsing microphone modal and auto-submit.
- **Real-Time Autocomplete:** Instant keyword suggestion dropdown with keyboard navigation (`↑`, `↓`, `Enter`, `Esc`).
- **Global Keyboard Shortcut:** Press `/` anywhere on any page to immediately focus the search bar.

### 2. Comprehensive Search Categories
- **All (Web):** Full-text web results with favicons, breadcrumbs, titles, highlighted keyword snippets (`<mark>`), and instant bookmarking.
- **Images:** Aspect-ratio preserving image gallery with hover metadata overlays and high-resolution photo previews.
- **Videos:** Video cards featuring duration pills, channel titles, published timestamps, and play actions.
- **News:** Headline cards with publisher badges, relative publication timestamps (e.g. *2 hours ago*), and lead snippets.
- **Maps:** Geolocation preview cards with coordinates and one-click OpenStreetMap directions.
- **Shopping:** E-commerce cards displaying product pictures, prices, star ratings, and store merchants.

### 3. Employment & Talent Solutions Portal (`/employment`)
- **Direct Career Matching:** Discover curated engineering, AI/ML, DevOps, and cybersecurity vacancies.
- **Quick Apply Workflow:** Candidates can apply in one click with their profile, phone number, and portfolio links.
- **Employer Access Control:**
  - "Post a Job Opening" option is strictly reserved for authenticated **Employer** accounts.
  - Non-authenticated visitors or regular job seekers are guided with an Employer Sign-In / Registration prompt.
- **Mandatory Business Regulatory Verification:**
  - To prevent fraudulent job ads, employers must supply **at least any two (2)** verified business registration certificates:
    1. **Shop Act Certificate** (Shop & Establishment Act)
    2. **GST Certificate** (15-character GSTIN)
    3. **RERA Certificate** (Real Estate Regulatory Authority)
    4. **MSME / Udyam Certificate** (Ministry of MSME)
  - Interactive client-side live counter (`0 of 2` $\rightarrow$ `✓ 2 of 2 verified`) prevents form submission without credentials.
  - Server-side validation in `EmploymentService` and `UserService` enforces regulatory compliance.
  - Verified badges (`✓ Business Verified: GST, MSME`) display publicly on job vacancy cards.

### 4. User Registration & Personalization (`/register`, `/login`)
- **Account Types:** Dual-mode sign-up for **Job Seekers** and **Employers / Recruiters**.
- **Contact Details:**
  - **Mobile Number:** Mandatory field with international format support.
  - **WhatsApp Number:** Optional field with a handy **"Same as mobile"** real-time sync toggle.
- **Personalized Search Workspace:**
  - **Saved Bookmarks:** Save any search result with one click; filter by category and manage favorites.
  - **Search History:** Query tracking with timestamps, re-search triggers, single deletion, and full history clearing.
  - **Profile Management:** Update full name, avatar, mobile/WhatsApp numbers, and password.

### 5. Apache Lucene 9 Full-Text Search Engine
- Local inverted index using `StandardAnalyzer` and **BM25 scoring**.
- Boosted multi-field search across `title` (^4.0), `keywords` (^3.0), `description` (^2.0), and `content` (^1.0).
- Dynamic keyword highlighter for search snippets (`UnifiedHighlighter`).
- Near-Real-Time (NRT) indexing with automatic orphan lock recovery.

### 6. Safe Multi-Threaded Web Crawler & Error Telemetry (`/admin/crawler`)
- High-throughput asynchronous crawler powered by **JSoup** with thread-pool scheduling.
- **Multi-Layered SSRF Defense:**
  - Private / loopback IP address blocking (`127.0.0.1`, `10.0.0.0/8`, `192.168.0.0/16`, `169.254.169.254`).
  - Prohibited destination port blocks (SSH port 22, FTP 21, Telnet 23, SMTP 25).
  - Protocol filtering (restricting to HTTP/HTTPS).
  - Robots.txt parsing, canonical URL deduplication, and domain depth rate limits.
- **Live Crawl Monitoring:** Real-time crawl safety telemetry, recent job error logs, HTTP status tracking, and crawl duration analytics.

---

## 🛠️ Technology Stack

| Layer | Technologies |
|---|---|
| **Backend Framework** | Java 17 LTS, Spring Boot 3.3.4, Spring MVC, Spring Data JPA, Spring Security 6 |
| **Search Engine** | Apache Lucene 9.11.1 (Core, QueryParser, Highlighter, Analysis) |
| **Web Crawler** | JSoup 1.17.2, ExecutorService Thread Pool |
| **Frontend UI** | Thymeleaf 3, HTML5, CSS3, JavaScript (ES6+), Bootstrap 5.3.3, FontAwesome 6.5.2 |
| **Databases** | H2 Embedded Database (Zero-Config Development) / MySQL 8.0+ (Production) |
| **Build & Test** | Apache Maven 3.9+, JUnit 5, Mockito |

---

## 👥 Default Demo & Test Accounts

| Account Type | Username | Password | Role / Account Type | Privileges |
|---|---|---|---|---|
| **Administrator** | `admin` | `Admin@123` | `ROLE_ADMIN` | Full Admin Dashboard (`/admin`), Crawler, Telemetry, User Management |
| **Employer** | `employer` | `Employer@123` | `ROLE_USER` / `EMPLOYER` | Verified Business; Post Job Openings on `/employment` |
| **Job Seeker** | `demo` | `Demo@123` | `ROLE_USER` / `JOB_SEEKER` | Search, Apply for Jobs, Save Bookmarks, History |

---

## 🚀 Getting Started

### Prerequisites
- **Java Development Kit (JDK) 17** or higher
- **Apache Maven 3.8+**
- Git

### Build & Run Locally

```bash
# Clone the repository
git clone https://github.com/nimbhorkar1990-spec/besmart.git
cd besmart

# Run unit tests
mvn test

# Package the application
mvn clean package -DskipTests

# Run the Spring Boot application
java -jar target/smartsearch-engine-1.0.0.jar
```

The application starts immediately on **`http://localhost:8080`**.

---

## 📁 Repository Structure

```text
besmart/
├── pom.xml                                    # Maven configuration & dependencies
├── README.md                                  # Project documentation
├── .gitignore                                 # Git ignore definitions
├── src/
│   ├── main/
│   │   ├── java/com/smartsearch/
│   │   │   ├── SmartSearchApplication.java    # Application entry point
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java        # Spring Security 6 configuration
│   │   │   │   └── WebMvcConfig.java          # MVC resource routing
│   │   │   ├── controller/
│   │   │   │   ├── HomeController.java        # Homepage & menu navigation
│   │   │   │   ├── EmploymentController.java  # Employment portal, jobs & applications
│   │   │   │   ├── SearchController.java      # Results page & category tabs
│   │   │   │   ├── AuthController.java        # Login & registration
│   │   │   │   ├── UserController.java        # User profile, history, bookmarks
│   │   │   │   ├── AdminController.java       # Admin dashboard, crawler & telemetry
│   │   │   │   └── SearchApiController.java   # REST endpoints (suggest, search)
│   │   │   ├── dto/                           # Data Transfer Objects
│   │   │   ├── model/                         # JPA entities (User, JobListing, IndexedPage, etc.)
│   │   │   ├── repository/                    # Spring Data JPA repositories
│   │   │   ├── security/                      # UserDetails & CustomAuthenticationProvider
│   │   │   └── service/                       # Business logic, Lucene, Crawler, Employment services
│   │   └── resources/
│   │       ├── application.properties         # Embedded H2 configuration
│   │       ├── application-mysql.properties   # Production MySQL configuration
│   │       ├── static/
│   │       │   ├── css/                       # main.css, dark-mode.css, themes.css, admin.css
│   │       │   ├── js/                        # main.js, search.js, voice-search.js, admin.js
│   │       │   └── images/                    # logo.svg, logo-icon.svg
│   │       └── templates/                     # Thymeleaf HTML views
│   │           ├── index.html                 # Homepage with theme selector & Services menu
│   │           ├── employment.html            # Employment & Career Services portal
│   │           ├── search.html                # Search results page
│   │           ├── auth/                      # Login & Register views
│   │           ├── user/                      # Profile, history, bookmarks
│   │           └── admin/                     # Dashboard, crawler, users, analytics, telemetry
│   └── test/                                  # Comprehensive automated test suite
```

---

## 📄 License
This project is open-source and released under the [MIT License](LICENSE).
