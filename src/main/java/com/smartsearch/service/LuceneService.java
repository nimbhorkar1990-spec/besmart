package com.smartsearch.service;

import com.smartsearch.dto.SearchRequestDto;
import com.smartsearch.dto.SearchResultItemDto;
import com.smartsearch.model.IndexedPage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

@Service
public class LuceneService {

    private static final Logger logger = LoggerFactory.getLogger(LuceneService.class);

    @Value("${smartsearch.lucene.index-dir:data/lucene_index}")
    private String indexDirPath;

    private Directory directory;
    private StandardAnalyzer analyzer;
    private IndexWriter indexWriter;

    @PostConstruct
    public void init() {
        try {
            File dirFile = new File(indexDirPath);
            if (!dirFile.exists()) {
                dirFile.mkdirs();
            }
            // Clear any orphan write.lock from unclean shutdown
            File lockFile = new File(dirFile, "write.lock");
            if (lockFile.exists()) {
                lockFile.delete();
            }

            this.directory = FSDirectory.open(Paths.get(indexDirPath));
            this.analyzer = new StandardAnalyzer();
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            this.indexWriter = new IndexWriter(directory, config);
            this.indexWriter.commit();
            logger.info("Lucene Index initialized successfully at: {}", indexDirPath);
        } catch (IOException e) {
            logger.error("Failed to initialize Lucene index", e);
        }
    }

    @PreDestroy
    public void cleanUp() {
        try {
            if (indexWriter != null && indexWriter.isOpen()) {
                indexWriter.close();
            }
            if (directory != null) {
                directory.close();
            }
            if (analyzer != null) {
                analyzer.close();
            }
            logger.info("Lucene resources closed cleanly");
        } catch (IOException e) {
            logger.error("Error closing Lucene resources", e);
        }
    }

    public synchronized void indexDocument(IndexedPage page) {
        if (page == null || page.getUrl() == null || page.getUrl().isBlank()) {
            return;
        }
        try {
            Document doc = createLuceneDocument(page);
            indexWriter.updateDocument(new Term("url", page.getUrl()), doc);
            indexWriter.commit();
            logger.debug("Indexed document in Lucene: {}", page.getUrl());
        } catch (IOException e) {
            logger.error("Failed to index document in Lucene: " + page.getUrl(), e);
        }
    }

    public synchronized void indexDocuments(List<IndexedPage> pages) {
        if (pages == null || pages.isEmpty()) {
            return;
        }
        try {
            for (IndexedPage page : pages) {
                if (page.getUrl() != null && !page.getUrl().isBlank()) {
                    Document doc = createLuceneDocument(page);
                    indexWriter.updateDocument(new Term("url", page.getUrl()), doc);
                }
            }
            indexWriter.commit();
            logger.info("Batch indexed {} documents in Lucene", pages.size());
        } catch (IOException e) {
            logger.error("Failed to batch index documents in Lucene", e);
        }
    }

    public synchronized void deleteDocument(String url) {
        try {
            indexWriter.deleteDocuments(new Term("url", url));
            indexWriter.commit();
            logger.info("Deleted document from Lucene index: {}", url);
        } catch (IOException e) {
            logger.error("Failed to delete document from Lucene: " + url, e);
        }
    }

    public synchronized void clearIndex() {
        try {
            indexWriter.deleteAll();
            indexWriter.commit();
            logger.info("Cleared Lucene index entirely");
        } catch (IOException e) {
            logger.error("Failed to clear Lucene index", e);
        }
    }

    private Document createLuceneDocument(IndexedPage page) {
        Document doc = new Document();

        // Stored & Indexed fields
        doc.add(new StringField("url", page.getUrl(), Field.Store.YES));
        doc.add(new TextField("title", page.getTitle() != null ? page.getTitle() : "", Field.Store.YES));
        doc.add(new TextField("description", page.getDescription() != null ? page.getDescription() : "", Field.Store.YES));
        doc.add(new TextField("keywords", page.getKeywords() != null ? page.getKeywords() : "", Field.Store.YES));
        doc.add(new TextField("content", page.getContent() != null ? page.getContent() : "", Field.Store.YES));
        doc.add(new StringField("category", page.getCategory() != null ? page.getCategory().toUpperCase() : "ALL", Field.Store.YES));
        doc.add(new StringField("domain", page.getDomain() != null ? page.getDomain() : "", Field.Store.YES));
        
        // Stored only fields
        doc.add(new StoredField("imageUrl", page.getImageUrl() != null ? page.getImageUrl() : ""));
        doc.add(new StoredField("indexedAt", page.getIndexedAt() != null ? page.getIndexedAt().toString() : ""));

        return doc;
    }

    private DirectoryReader getReader() throws IOException {
        if (indexWriter != null && indexWriter.isOpen()) {
            return DirectoryReader.open(indexWriter);
        }
        return DirectoryReader.open(directory);
    }

    public Map<String, Object> search(SearchRequestDto request) {
        Map<String, Object> response = new HashMap<>();
        List<SearchResultItemDto> items = new ArrayList<>();
        long totalHits = 0;

        try (DirectoryReader reader = getReader()) {
            IndexSearcher searcher = new IndexSearcher(reader);

            String queryStr = request.getQuery() != null ? request.getQuery().trim() : "";
            if (queryStr.isEmpty() && (request.getExactMatch() == null || request.getExactMatch().isBlank())) {
                response.put("items", items);
                response.put("totalHits", 0L);
                return response;
            }

            BooleanQuery.Builder mainQueryBuilder = new BooleanQuery.Builder();

            // Multi-field text query with boosting
            Map<String, Float> boosts = new HashMap<>();
            boosts.put("title", 4.0f);
            boosts.put("keywords", 3.0f);
            boosts.put("description", 2.0f);
            boosts.put("content", 1.0f);

            if (!queryStr.isEmpty()) {
                MultiFieldQueryParser parser = new MultiFieldQueryParser(
                        new String[]{"title", "keywords", "description", "content"},
                        analyzer,
                        boosts
                );
                parser.setDefaultOperator(QueryParser.Operator.OR);
                String escapedQuery = QueryParser.escape(queryStr);
                Query parsedQuery = parser.parse(escapedQuery);
                mainQueryBuilder.add(parsedQuery, BooleanClause.Occur.MUST);
            }

            // Exact match query
            if (request.getExactMatch() != null && !request.getExactMatch().isBlank()) {
                String exact = request.getExactMatch().trim();
                PhraseQuery.Builder phraseBuilder = new PhraseQuery.Builder();
                String[] words = exact.split("\\s+");
                for (String word : words) {
                    phraseBuilder.add(new Term("content", word.toLowerCase()));
                }
                mainQueryBuilder.add(phraseBuilder.build(), BooleanClause.Occur.MUST);
            }

            // Category filter
            if (request.getCategory() != null && !request.getCategory().equalsIgnoreCase("ALL")) {
                TermQuery categoryQuery = new TermQuery(new Term("category", request.getCategory().toUpperCase()));
                mainQueryBuilder.add(categoryQuery, BooleanClause.Occur.FILTER);
            }

            // Domain filter (site:domain)
            if (request.getSite() != null && !request.getSite().isBlank()) {
                TermQuery domainQuery = new TermQuery(new Term("domain", request.getSite().trim().toLowerCase()));
                mainQueryBuilder.add(domainQuery, BooleanClause.Occur.FILTER);
            }

            BooleanQuery finalQuery = mainQueryBuilder.build();

            // Pagination setup
            int page = Math.max(request.getPage(), 1);
            int pageSize = Math.max(request.getSize(), 10);
            int startOffset = (page - 1) * pageSize;
            int maxResults = page * pageSize;

            TopDocs topDocs = searcher.search(finalQuery, maxResults);
            totalHits = topDocs.totalHits.value;

            ScoreDoc[] hits = topDocs.scoreDocs;
            int endIndex = Math.min(startOffset + pageSize, hits.length);

            for (int i = startOffset; i < endIndex; i++) {
                Document doc = searcher.doc(hits[i].doc);
                SearchResultItemDto item = new SearchResultItemDto();
                item.setId(String.valueOf(hits[i].doc));
                item.setTitle(doc.get("title"));
                item.setUrl(doc.get("url"));
                item.setDomain(doc.get("domain"));
                item.setCategory(doc.get("category"));
                item.setImageUrl(doc.get("imageUrl"));
                item.setScore(hits[i].score);
                item.setSource("LUCENE");

                // Highlight description/snippet: pick whichever contains query keywords
                String desc = doc.get("description");
                String content = doc.get("content");
                String rawSnippet = desc;
                if (queryStr != null && !queryStr.isBlank()) {
                    String lowerQ = queryStr.toLowerCase();
                    if ((desc == null || !desc.toLowerCase().contains(lowerQ)) && content != null && content.toLowerCase().contains(lowerQ)) {
                        rawSnippet = content;
                    }
                }
                if (rawSnippet == null || rawSnippet.isBlank()) {
                    rawSnippet = (content != null && !content.isBlank()) ? content : "No description available.";
                }
                if (rawSnippet.length() > 220) {
                    rawSnippet = rawSnippet.substring(0, 220) + "...";
                }

                item.setSnippet(highlightQueryTerms(rawSnippet, queryStr));
                items.add(item);
            }

        } catch (IndexNotFoundException e) {
            logger.warn("Lucene index not found or empty yet");
        } catch (Exception e) {
            logger.error("Error executing Lucene search", e);
        }

        response.put("items", items);
        response.put("totalHits", totalHits);
        return response;
    }

    private String highlightQueryTerms(String text, String query) {
        if (text == null || text.isBlank() || query == null || query.isBlank()) {
            return text;
        }
        String[] terms = query.trim().split("\\s+");
        String result = text;
        for (String term : terms) {
            String clean = term.replaceAll("[^a-zA-Z0-9]", "");
            if (clean.length() >= 2) {
                result = result.replaceAll("(?i)\\b(" + java.util.regex.Pattern.quote(clean) + ")\\b", "<mark class=\"search-highlight\">$1</mark>");
            }
        }
        return result;
    }

    public Map<String, Object> getIndexStats() {
        Map<String, Object> stats = new HashMap<>();
        try (DirectoryReader reader = getReader()) {
            stats.put("totalDocuments", reader.numDocs());
            stats.put("maxDoc", reader.maxDoc());
            stats.put("numDeletedDocs", reader.numDeletedDocs());
            stats.put("isOpen", true);
            stats.put("indexLocation", indexDirPath);
        } catch (Exception e) {
            stats.put("totalDocuments", 0);
            stats.put("isOpen", false);
            stats.put("indexLocation", indexDirPath);
            stats.put("error", e.getMessage());
        }
        return stats;
    }
}
