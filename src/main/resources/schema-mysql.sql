-- ========================================================
-- SmartSearch Database Schema (MySQL 8.0+)
-- Database: smartsearch_db
-- ========================================================

CREATE DATABASE IF NOT EXISTS smartsearch_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smartsearch_db;

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    avatar_url VARCHAR(255),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_user_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Search History Table
CREATE TABLE IF NOT EXISTS search_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    query VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT 'ALL',
    ip_address VARCHAR(64),
    searched_at DATETIME NOT NULL,
    INDEX idx_history_user (user_id),
    INDEX idx_history_searched_at (searched_at),
    CONSTRAINT fk_history_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Search Queries & Analytics Table
CREATE TABLE IF NOT EXISTS search_analytics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    query VARCHAR(255) NOT NULL UNIQUE,
    search_count BIGINT NOT NULL DEFAULT 1,
    top_category VARCHAR(50) DEFAULT 'ALL',
    first_searched_at DATETIME NOT NULL,
    last_searched_at DATETIME NOT NULL,
    INDEX idx_analytics_count (search_count),
    INDEX idx_analytics_last_searched (last_searched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Saved Results (Bookmarks) Table
CREATE TABLE IF NOT EXISTS saved_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    url VARCHAR(1500) NOT NULL,
    snippet VARCHAR(2000),
    category VARCHAR(50) DEFAULT 'ALL',
    image_url VARCHAR(1500),
    saved_at DATETIME NOT NULL,
    INDEX idx_saved_user (user_id),
    INDEX idx_saved_category (category),
    CONSTRAINT fk_saved_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Indexed Pages (Crawler & Lucene Store) Table
CREATE TABLE IF NOT EXISTS indexed_pages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    url VARCHAR(1500) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL,
    description VARCHAR(2000),
    keywords VARCHAR(500),
    category VARCHAR(50) NOT NULL DEFAULT 'ALL',
    content TEXT,
    image_url VARCHAR(1500),
    domain VARCHAR(255),
    canonical_url VARCHAR(1500),
    content_hash VARCHAR(64),
    headings VARCHAR(2000),
    published_at DATETIME,
    quality_score DOUBLE DEFAULT 50.0,
    status VARCHAR(50) NOT NULL DEFAULT 'INDEXED',
    indexed_at DATETIME NOT NULL,
    last_crawled_at DATETIME,
    INDEX idx_page_category (category),
    INDEX idx_page_domain (domain),
    INDEX idx_page_hash (content_hash),
    INDEX idx_page_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. System & API Configuration Settings Table
CREATE TABLE IF NOT EXISTS system_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value VARCHAR(2000),
    description VARCHAR(255),
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Search Queries Log Table
CREATE TABLE IF NOT EXISTS search_queries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    query VARCHAR(500) NOT NULL,
    category VARCHAR(50) DEFAULT 'ALL',
    results_count BIGINT NOT NULL DEFAULT 0,
    execution_time_ms BIGINT NOT NULL DEFAULT 0,
    primary_source VARCHAR(50) DEFAULT 'HYBRID',
    external_triggered BOOLEAN NOT NULL DEFAULT FALSE,
    client_ip VARCHAR(64),
    searched_at DATETIME NOT NULL,
    INDEX idx_sq_query (query),
    INDEX idx_sq_searched_at (searched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Multi-Search Providers Configuration Table
CREATE TABLE IF NOT EXISTS search_providers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    api_key VARCHAR(255),
    search_engine_id VARCHAR(255),
    endpoint_url VARCHAR(500),
    priority INT NOT NULL DEFAULT 1,
    total_calls BIGINT NOT NULL DEFAULT 0,
    success_calls BIGINT NOT NULL DEFAULT 0,
    failure_calls BIGINT NOT NULL DEFAULT 0,
    last_called_at DATETIME,
    last_error VARCHAR(500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Discovered URLs Table
CREATE TABLE IF NOT EXISTS discovered_urls (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    url VARCHAR(1500) NOT NULL,
    domain VARCHAR(255),
    source_query VARCHAR(255),
    source_provider VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    status_reason VARCHAR(500),
    depth INT DEFAULT 0,
    discovered_at DATETIME NOT NULL,
    processed_at DATETIME,
    INDEX idx_disc_url (url),
    INDEX idx_disc_status (status),
    INDEX idx_disc_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Crawled Pages Metadata Table
CREATE TABLE IF NOT EXISTS crawled_pages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    url VARCHAR(1500) NOT NULL,
    domain VARCHAR(255),
    canonical_url VARCHAR(1500),
    title VARCHAR(500),
    status_code INT DEFAULT 200,
    response_time_ms BIGINT DEFAULT 0,
    content_size_bytes BIGINT DEFAULT 0,
    content_hash VARCHAR(64),
    content_type VARCHAR(100),
    crawled_at DATETIME NOT NULL,
    INDEX idx_cp_url (url),
    INDEX idx_cp_domain (domain),
    INDEX idx_cp_crawled_at (crawled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. Crawl Jobs Table
CREATE TABLE IF NOT EXISTS crawl_jobs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trigger_source VARCHAR(100) NOT NULL DEFAULT 'AUTO_SEARCH',
    seed_query_or_url VARCHAR(500),
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    pages_discovered INT NOT NULL DEFAULT 0,
    pages_crawled INT NOT NULL DEFAULT 0,
    pages_indexed INT NOT NULL DEFAULT 0,
    duplicates_skipped INT NOT NULL DEFAULT 0,
    errors_count INT NOT NULL DEFAULT 0,
    started_at DATETIME NOT NULL,
    completed_at DATETIME,
    summary_message VARCHAR(1000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. Crawl Queue Table
CREATE TABLE IF NOT EXISTS crawl_queue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    url VARCHAR(1500) NOT NULL,
    domain VARCHAR(255),
    depth INT DEFAULT 0,
    priority INT NOT NULL DEFAULT 5,
    status VARCHAR(50) NOT NULL DEFAULT 'WAITING',
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    crawl_job_id BIGINT,
    scheduled_at DATETIME NOT NULL,
    processed_at DATETIME,
    INDEX idx_cq_status (status),
    INDEX idx_cq_priority (priority),
    INDEX idx_cq_domain (domain)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. Crawl Error Logs Table
CREATE TABLE IF NOT EXISTS crawl_errors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    url VARCHAR(1500) NOT NULL,
    domain VARCHAR(255),
    error_type VARCHAR(50) NOT NULL,
    http_status_code INT,
    error_message VARCHAR(1000),
    crawl_job_id BIGINT,
    occurred_at DATETIME NOT NULL,
    INDEX idx_ce_domain (domain),
    INDEX idx_ce_type (error_type),
    INDEX idx_ce_occurred (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. Search Results Cache Table
CREATE TABLE IF NOT EXISTS search_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    query_hash VARCHAR(64) NOT NULL,
    raw_query VARCHAR(500) NOT NULL,
    category VARCHAR(50) DEFAULT 'ALL',
    results_json TEXT,
    total_hits BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    INDEX idx_sr_query (query_hash),
    INDEX idx_sr_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 15. Duplicate Records Table
CREATE TABLE IF NOT EXISTS duplicate_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    duplicate_url VARCHAR(1500) NOT NULL,
    original_url VARCHAR(1500) NOT NULL,
    content_hash VARCHAR(64),
    duplicate_type VARCHAR(50) NOT NULL DEFAULT 'CANONICAL',
    detected_at DATETIME NOT NULL,
    INDEX idx_dup_hash (content_hash),
    INDEX idx_dup_orig_url (original_url)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 16. API Usage Logs Table
CREATE TABLE IF NOT EXISTS api_usage_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id VARCHAR(50) NOT NULL,
    query VARCHAR(500) NOT NULL,
    response_status INT DEFAULT 200,
    results_found INT DEFAULT 0,
    latency_ms BIGINT DEFAULT 0,
    error_message VARCHAR(500),
    timestamp DATETIME NOT NULL,
    INDEX idx_aul_provider (provider_id),
    INDEX idx_aul_timestamp (timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

