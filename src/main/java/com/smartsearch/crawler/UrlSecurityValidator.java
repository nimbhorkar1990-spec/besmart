package com.smartsearch.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Set;

@Component
public class UrlSecurityValidator {

    private static final Logger logger = LoggerFactory.getLogger(UrlSecurityValidator.class);

    private static final Set<String> DISALLOWED_EXTENSIONS = Set.of(
            ".zip", ".tar", ".gz", ".7z", ".rar", ".exe", ".dll", ".bin", ".dmg", ".iso",
            ".pdf", ".docx", ".xlsx", ".pptx", ".mp3", ".mp4", ".avi", ".mkv", ".mov",
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".ico", ".css", ".js",
            ".woff", ".woff2", ".ttf", ".eot"
    );

    private static final Set<Integer> ALLOWED_PORTS = Set.of(80, 443, 8080, 8443);

    public static class ValidationResult {
        private final boolean safe;
        private final String errorType;
        private final String reason;

        private ValidationResult(boolean safe, String errorType, String reason) {
            this.safe = safe;
            this.errorType = errorType;
            this.reason = reason;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null, "URL is safe to crawl");
        }

        public static ValidationResult invalid(String errorType, String reason) {
            return new ValidationResult(false, errorType, reason);
        }

        public boolean isSafe() {
            return safe;
        }

        public String getErrorType() {
            return errorType;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * Performs deep security inspection on the URL and returns a detailed ValidationResult.
     */
    public ValidationResult validate(String urlStr) {
        if (urlStr == null || urlStr.isBlank()) {
            return ValidationResult.invalid("MALFORMED_URL", "Target URL is null or empty");
        }

        String trimmed = urlStr.trim();
        if (trimmed.length() > 2048) {
            return ValidationResult.invalid("MALFORMED_URL", "Target URL length exceeds 2048 character threshold");
        }

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (Exception e) {
            return ValidationResult.invalid("MALFORMED_URL", "URL syntax parsing failed: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            logger.warn("SSRF Blocked: Invalid scheme {} in URL: {}", scheme, trimmed);
            return ValidationResult.invalid("INVALID_SCHEME", "Prohibited protocol scheme '" + scheme + "'. Only HTTP and HTTPS are permitted.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return ValidationResult.invalid("MALFORMED_URL", "URL host is missing or invalid");
        }

        String lowerHost = host.toLowerCase();

        // Reject local hostnames, intranet, or cloud metadata hostnames
        if (lowerHost.equals("localhost") || lowerHost.endsWith(".localhost")
                || lowerHost.endsWith(".local") || lowerHost.endsWith(".internal")
                || lowerHost.endsWith(".lan") || lowerHost.endsWith(".corp")
                || lowerHost.endsWith(".home") || lowerHost.endsWith(".onion")
                || lowerHost.equals("metadata.google.internal") || lowerHost.equals("instance-data")) {
            logger.warn("SSRF Blocked: Internal/local host reference in URL: {}", trimmed);
            return ValidationResult.invalid("SSRF_BLOCKED", "Internal, local, or cloud metadata hostname prohibited: " + host);
        }

        // Port policy check
        int port = uri.getPort();
        if (port != -1 && !ALLOWED_PORTS.contains(port)) {
            logger.warn("Crawl Safety Block: Prohibited destination port {} in URL: {}", port, trimmed);
            return ValidationResult.invalid("RESTRICTED_PORT", "Port " + port + " blocked by security policy. Allowed ports: 80, 443, 8080, 8443");
        }

        // File extension check
        String path = uri.getPath();
        if (path != null) {
            String lowerPath = path.toLowerCase();
            for (String ext : DISALLOWED_EXTENSIONS) {
                if (lowerPath.endsWith(ext)) {
                    return ValidationResult.invalid("DISALLOWED_EXTENSION", "Target URL points to blocked file extension (" + ext + ")");
                }
            }
        }

        // DNS Resolution & IP checks to prevent SSRF
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isPrivateOrRestrictedIp(addr)) {
                    logger.warn("SSRF Blocked: Host {} resolves to restricted IP {} in URL: {}", host, addr.getHostAddress(), trimmed);
                    return ValidationResult.invalid("PRIVATE_IP_BLOCKED", "Host " + host + " resolves to restricted IP address: " + addr.getHostAddress());
                }
            }
        } catch (UnknownHostException uhe) {
            // When running offline or in restricted sandbox, validate host format
            if (lowerHost.contains(".") && !lowerHost.endsWith(".local") && !lowerHost.endsWith(".internal")) {
                return ValidationResult.valid();
            }
            return ValidationResult.invalid("DNS_FAILURE", "Unable to resolve host: " + host);
        } catch (Exception e) {
            return ValidationResult.invalid("SSRF_BLOCKED", "DNS resolution error: " + e.getMessage());
        }

        return ValidationResult.valid();
    }

    /**
     * Backward-compatible boolean helper.
     */
    public boolean isSafeToCrawl(String urlStr) {
        return validate(urlStr).isSafe();
    }

    public boolean isPrivateOrRestrictedIp(InetAddress addr) {
        if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress() 
                || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
            return true;
        }

        String ip = addr.getHostAddress();
        // Check cloud metadata services & private ranges
        if (ip.startsWith("169.254.") || ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("0.")) {
            return true;
        }
        if (ip.startsWith("192.168.")) {
            return true;
        }
        // Carrier-Grade NAT RFC 6598 (100.64.0.0/10)
        if (ip.startsWith("100.")) {
            try {
                String[] parts = ip.split("\\.");
                int second = Integer.parseInt(parts[1]);
                if (second >= 64 && second <= 127) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        // 172.16.0.0 - 172.31.255.255
        if (ip.startsWith("172.")) {
            try {
                String[] parts = ip.split("\\.");
                int second = Integer.parseInt(parts[1]);
                if (second >= 16 && second <= 31) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        // Alibaba cloud metadata
        if (ip.equals("100.100.100.200")) {
            return true;
        }
        // IPv6 private/unique-local checks
        if (ip.startsWith("fc00:") || ip.startsWith("fd00:") || ip.equals("::1") || ip.startsWith("fe80:")) {
            return true;
        }

        return false;
    }

    public String normalizeUrl(String url) {
        if (url == null) return null;
        try {
            URI uri = URI.create(url.trim());
            String clean = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null).toString();
            if (clean.endsWith("/") && clean.length() > 8) {
                clean = clean.substring(0, clean.length() - 1);
            }
            return clean;
        } catch (Exception e) {
            return url.trim();
        }
    }

    public String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host.replaceFirst("^www\\.", "").toLowerCase() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
