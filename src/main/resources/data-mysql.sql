-- ========================================================
-- SmartSearch Initial Seed Data
-- ========================================================

-- Insert Default Admin (Password: Admin@123) and Demo User (Password: Demo@123)
-- BCrypt encoded passwords
INSERT INTO users (username, email, password, full_name, role, avatar_url, enabled, created_at, updated_at)
VALUES 
('admin', 'admin@smartsearch.local', '$2a$10$f3b20u6Q6HqZ4lK.B0h0tup.hK.Jt7K409yQeZc5D28kL4E3E5e0i', 'SmartSearch Administrator', 'ROLE_ADMIN', 'https://ui-avatars.com/api/?name=Admin+User&background=0b57d0&color=fff', TRUE, NOW(), NOW()),
('demo', 'demo@smartsearch.local', '$2a$10$f3b20u6Q6HqZ4lK.B0h0tup.hK.Jt7K409yQeZc5D28kL4E3E5e0i', 'Alex Morgan', 'ROLE_USER', 'https://ui-avatars.com/api/?name=Alex+Morgan&background=1a73e8&color=fff', TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- Insert Default System Settings
INSERT INTO system_settings (setting_key, setting_value, description, updated_at)
VALUES
('SEARCH_PROVIDER', 'HYBRID', 'Search provider mode: LUCENE_ONLY, EXTERNAL_ONLY, HYBRID', NOW()),
('EXTERNAL_SEARCH_ENABLED', 'true', 'Enable live external web search fallback', NOW()),
('BING_API_KEY', '', 'Bing Web Search API Key (optional)', NOW()),
('BING_ENDPOINT', 'https://api.bing.microsoft.com/v7.0/search', 'Bing Web Search API Endpoint', NOW()),
('DEFAULT_PAGE_SIZE', '10', 'Default search result items per page', NOW()),
('DEFAULT_SAFE_SEARCH', 'MODERATE', 'Default safe search filtering: OFF, MODERATE, STRICT', NOW()),
('VOICE_SEARCH_ENABLED', 'true', 'Enable client-side voice search UI', NOW()),
('SUGGEST_ENABLED', 'true', 'Enable autocomplete suggestions', NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- Insert Sample Popular Queries
INSERT INTO search_analytics (query, search_count, top_category, first_searched_at, last_searched_at)
VALUES
('spring boot', 142, 'ALL', NOW(), NOW()),
('java 17', 115, 'ALL', NOW(), NOW()),
('apache lucene', 98, 'ALL', NOW(), NOW()),
('artificial intelligence', 210, 'ALL', NOW(), NOW()),
('web development', 76, 'ALL', NOW(), NOW()),
('macbook pro', 54, 'SHOPPING', NOW(), NOW())
ON DUPLICATE KEY UPDATE search_count = search_count + 1;

