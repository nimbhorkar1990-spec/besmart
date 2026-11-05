package com.smartsearch.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "blog_edit_histories")
public class BlogEditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blog_id", nullable = false)
    private BlogPost blog;

    @Column(nullable = false, length = 100)
    private String originalAuthorName;

    @Column(nullable = false, length = 50)
    private String originalAuthorRole;

    @Column(nullable = false, length = 100)
    private String editorUsername;

    @Column(nullable = false)
    private LocalDateTime editedAt;

    @Column(length = 500)
    private String changeSummary;

    @Column(length = 255)
    private String previousTitle;

    @Column(length = 255)
    private String newTitle;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String previousContent;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String newContent;

    private Integer previousSeoScore;

    private Integer newSeoScore;

    public BlogEditHistory() {
        this.editedAt = LocalDateTime.now();
    }

    public BlogEditHistory(BlogPost blog, String originalAuthorName, String originalAuthorRole,
                           String editorUsername, String changeSummary,
                           String previousTitle, String newTitle,
                           String previousContent, String newContent,
                           Integer previousSeoScore, Integer newSeoScore) {
        this.blog = blog;
        this.originalAuthorName = originalAuthorName != null ? originalAuthorName : "Unknown";
        this.originalAuthorRole = originalAuthorRole != null ? originalAuthorRole : "USER";
        this.editorUsername = editorUsername != null ? editorUsername : "ADMIN";
        this.changeSummary = changeSummary;
        this.previousTitle = previousTitle;
        this.newTitle = newTitle;
        this.previousContent = previousContent;
        this.newContent = newContent;
        this.previousSeoScore = previousSeoScore != null ? previousSeoScore : 0;
        this.newSeoScore = newSeoScore != null ? newSeoScore : 0;
        this.editedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BlogPost getBlog() {
        return blog;
    }

    public void setBlog(BlogPost blog) {
        this.blog = blog;
    }

    public String getOriginalAuthorName() {
        return originalAuthorName;
    }

    public void setOriginalAuthorName(String originalAuthorName) {
        this.originalAuthorName = originalAuthorName;
    }

    public String getOriginalAuthorRole() {
        return originalAuthorRole;
    }

    public void setOriginalAuthorRole(String originalAuthorRole) {
        this.originalAuthorRole = originalAuthorRole;
    }

    public String getEditorUsername() {
        return editorUsername;
    }

    public void setEditorUsername(String editorUsername) {
        this.editorUsername = editorUsername;
    }

    public LocalDateTime getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(LocalDateTime editedAt) {
        this.editedAt = editedAt;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public String getPreviousTitle() {
        return previousTitle;
    }

    public void setPreviousTitle(String previousTitle) {
        this.previousTitle = previousTitle;
    }

    public String getNewTitle() {
        return newTitle;
    }

    public void setNewTitle(String newTitle) {
        this.newTitle = newTitle;
    }

    public String getPreviousContent() {
        return previousContent;
    }

    public void setPreviousContent(String previousContent) {
        this.previousContent = previousContent;
    }

    public String getNewContent() {
        return newContent;
    }

    public void setNewContent(String newContent) {
        this.newContent = newContent;
    }

    public Integer getPreviousSeoScore() {
        return previousSeoScore;
    }

    public void setPreviousSeoScore(Integer previousSeoScore) {
        this.previousSeoScore = previousSeoScore;
    }

    public Integer getNewSeoScore() {
        return newSeoScore;
    }

    public void setNewSeoScore(Integer newSeoScore) {
        this.newSeoScore = newSeoScore;
    }
}
