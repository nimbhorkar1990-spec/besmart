package com.smartsearch.service;

import com.smartsearch.dto.JobListingDto;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class EmploymentService {

    private final List<JobListingDto> jobListings = new CopyOnWriteArrayList<>();
    private final AtomicLong nextId = new AtomicLong(100);

    public EmploymentService() {
        initDefaultJobs();
    }

    private void initDefaultJobs() {
        JobListingDto job1 = new JobListingDto(
                1L,
                "Senior Java & Spring Boot Backend Engineer",
                "Apex Cloud Systems",
                "Bengaluru, Karnataka (Hybrid)",
                "Full-time",
                "ENGINEERING",
                "₹24,00,000 - ₹34,00,000 / yr",
                "4 - 8 Years",
                "Just now",
                "Architect and scale distributed high-throughput microservices using Java 17+, Spring Boot, Apache Kafka, and MySQL. Drive backend system performance and automated testing suites.",
                List.of("Java 17", "Spring Boot", "MySQL", "Kafka", "Docker", "Microservices"),
                "https://images.unsplash.com/photo-1549923746-c502d488b3ea?auto=format&fit=crop&w=120&h=120&q=80",
                true,
                38
        );
        job1.setGstCert("29AABCU9603R1ZM");
        job1.setMsmeCert("UDYAM-KR-03-0019284");
        jobListings.add(job1);

        JobListingDto job2 = new JobListingDto(
                2L,
                "Search & Information Retrieval Engineer (Apache Lucene)",
                "SmartSearch Labs",
                "Remote (Global)",
                "Remote",
                "ENGINEERING",
                "₹28,00,000 - ₹42,00,000 / yr",
                "3 - 7 Years",
                "2 hours ago",
                "Design inverted indexing pipelines, BM25 scoring algorithms, custom tokenizers, and real-time crawling extractors. Scale full-text search across millions of web documents.",
                List.of("Apache Lucene", "Java 17", "Elasticsearch", "Jsoup", "Information Retrieval"),
                "https://images.unsplash.com/photo-1572021335469-31706a17aaef?auto=format&fit=crop&w=120&h=120&q=80",
                true,
                52
        );
        job2.setShopActCert("DL/CENTRAL/SHOP/88219/2021");
        job2.setGstCert("07AAECR2849F1ZS");
        jobListings.add(job2);

        JobListingDto job3 = new JobListingDto(
                3L,
                "Lead DevOps & Cloud Platform Architect",
                "NexGen FinTech",
                "Hyderabad, Telangana / Remote",
                "Full-time",
                "DEVOPS",
                "₹32,00,000 - ₹48,00,000 / yr",
                "6 - 10 Years",
                "5 hours ago",
                "Oversee zero-downtime Kubernetes deployments, CI/CD pipelines, Terraform infrastructure-as-code, multi-region AWS cloud clusters, and automated security hardening.",
                List.of("Kubernetes", "AWS", "Terraform", "Docker", "CI/CD", "Linux"),
                "https://images.unsplash.com/photo-1551836022-d5d88e9218df?auto=format&fit=crop&w=120&h=120&q=80",
                false,
                24
        );
        job3.setShopActCert("TS/HYD/SHOP/44120/2020");
        job3.setReraCert("PRM/KA/RERA/1251/310/PR/171015");
        jobListings.add(job3);

        JobListingDto job4 = new JobListingDto(
                4L,
                "AI / ML Engineer (Generative AI & LLMs)",
                "Cognitive Matrix AI",
                "Bengaluru, Karnataka (Remote OK)",
                "Remote",
                "AI_ML",
                "₹26,00,000 - ₹40,00,000 / yr",
                "2 - 6 Years",
                "1 day ago",
                "Integrate multimodal foundation models, develop RAG (Retrieval-Augmented Generation) architectures, vector databases, and semantic search agents for enterprise clients.",
                List.of("Python", "LangChain", "Vector DB", "PyTorch", "LLM Integration", "FastAPI"),
                "https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=120&h=120&q=80",
                true,
                89
        );
        job4.setGstCert("29AAACH7409R1ZX");
        job4.setMsmeCert("UDYAM-KR-03-0044812");
        jobListings.add(job4);

        JobListingDto job5 = new JobListingDto(
                5L,
                "Full-Stack Software Engineer (Java + React)",
                "Vanguard Digital",
                "Pune, Maharashtra (Hybrid)",
                "Full-time",
                "ENGINEERING",
                "₹16,00,000 - ₹25,00,000 / yr",
                "2 - 5 Years",
                "1 day ago",
                "Build modern, reactive single-page frontends and robust REST APIs. Work closely with product designers and backend architects to deliver responsive web applications.",
                List.of("Java", "Spring Boot", "React", "TypeScript", "Tailwind CSS", "REST"),
                "https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=120&h=120&q=80",
                false,
                46
        );
        job5.setShopActCert("MH/PUN/SHOP/91024/2019");
        job5.setGstCert("27AAACV9204P1Z3");
        jobListings.add(job5);

        JobListingDto job6 = new JobListingDto(
                6L,
                "Cyber Security & Application Security Analyst",
                "Fortress Infosec",
                "Mumbai, Maharashtra",
                "Contract",
                "SECURITY",
                "₹20,00,000 - ₹30,00,000 / yr",
                "3 - 6 Years",
                "2 days ago",
                "Execute penetration tests, SSRF defense audits, OWASP top 10 vulnerability assessments, and secure code reviews across Spring Boot and microservices ecosystems.",
                List.of("AppSec", "Penetration Testing", "OWASP", "Network Security", "Cryptography"),
                "https://images.unsplash.com/photo-1563986768609-322da13575f3?auto=format&fit=crop&w=120&h=120&q=80",
                false,
                19
        );
        job6.setShopActCert("MH/MUM/SHOP/10294/2018");
        job6.setMsmeCert("UDYAM-MH-19-0012984");
        jobListings.add(job6);

        JobListingDto job7 = new JobListingDto(
                7L,
                "Product Manager - Enterprise Search & Discovery",
                "OmniSearch Global",
                "Remote / Delhi NCR",
                "Full-time",
                "PRODUCT",
                "₹28,00,000 - ₹38,00,000 / yr",
                "4 - 8 Years",
                "3 days ago",
                "Define the technical roadmap for next-gen search intelligence, external aggregator connectors, user query telemetry, and enterprise search UI experience.",
                List.of("Product Roadmap", "Search UX", "Agile", "User Analytics", "Market Research"),
                "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=120&h=120&q=80",
                false,
                31
        );
        job7.setGstCert("07AAFCO9182R1ZA");
        job7.setReraCert("DLRERA2019P0004");
        jobListings.add(job7);
    }

    public List<JobListingDto> getJobListings(String search, String category, String jobType) {
        return jobListings.stream()
                .filter(job -> {
                    if (search != null && !search.isBlank()) {
                        String s = search.toLowerCase().trim();
                        boolean matchTitle = job.getTitle().toLowerCase().contains(s);
                        boolean matchCompany = job.getCompany().toLowerCase().contains(s);
                        boolean matchLoc = job.getLocation().toLowerCase().contains(s);
                        boolean matchTags = job.getTags().stream().anyMatch(t -> t.toLowerCase().contains(s));
                        if (!matchTitle && !matchCompany && !matchLoc && !matchTags) {
                            return false;
                        }
                    }
                    if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) {
                        if (!category.equalsIgnoreCase(job.getCategory())) {
                            return false;
                        }
                    }
                    if (jobType != null && !jobType.isBlank() && !"ALL".equalsIgnoreCase(jobType)) {
                        if (!jobType.equalsIgnoreCase(job.getJobType())) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted(Comparator.comparing(JobListingDto::isFeatured).reversed())
                .toList();
    }

    public Optional<JobListingDto> getJobById(Long id) {
        if (id == null) return Optional.empty();
        return jobListings.stream().filter(j -> j.getId().equals(id)).findFirst();
    }

    public synchronized void postJob(JobListingDto dto) {
        if (!dto.hasMinimumCertificates()) {
            throw new IllegalArgumentException(
                    "Business Verification Error: Minimum any two certificates (Shop Act, GST Certificate, RERA Certificate, or MSME Certificate) are strictly required to post job advertisements.");
        }
        if (dto.getId() == null) {
            dto.setId(nextId.incrementAndGet());
        }
        if (dto.getPostedAgo() == null || dto.getPostedAgo().isBlank()) {
            dto.setPostedAgo("Just now");
        }
        if (dto.getApplicantCount() == 0) {
            dto.setApplicantCount(1);
        }
        if (dto.getCompanyLogo() == null || dto.getCompanyLogo().isBlank()) {
            dto.setCompanyLogo("https://ui-avatars.com/api/?name=" + dto.getCompany().replace(" ", "+") + "&background=1a73e8&color=fff");
        }
        jobListings.add(0, dto);
    }

    public synchronized boolean applyToJob(Long jobId, String candidateName, String candidateEmail, String phone, String experience, String message) {
        Optional<JobListingDto> jobOpt = getJobById(jobId);
        if (jobOpt.isPresent()) {
            JobListingDto job = jobOpt.get();
            job.setApplicantCount(job.getApplicantCount() + 1);
            return true;
        }
        return false;
    }

    public Map<String, Object> getEmploymentStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalActiveJobs", "1,480+");
        stats.put("partnerCompanies", "340+");
        stats.put("avgPlacementSalary", "₹24 LPA");
        stats.put("interviewsScheduled", "8,200+");
        return stats;
    }
}

