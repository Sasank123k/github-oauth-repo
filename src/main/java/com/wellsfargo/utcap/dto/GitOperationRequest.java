package com.wellsfargo.utcap.dto;

public class GitOperationRequest {
    // Specifies the Git operation: "createBranch", "updateFile", "addFile", "mergeBranch", "pushFile", etc.
    private String operation;
    private String owner;
    private String repo;

    // For createBranch operation
    private String newBranch;
    private String baseSha;
    private String baseBranch;

    // For updateFile and addFile operations
    private String filePath;
    private String commitMessage;
    private String content;
    private String fileSha;

    // For mergeBranch operation
    private String baseBranchForMerge;
    private String headBranch;

    // New fields for dynamic path construction
    private String sor;       // The SOR name (e.g., "sor1")
    private String feedName;  // The feed (table) name
    private String fileType;  // The file type (e.g., "json", "sql", "scripts", "metadata", "hql", "ddl")

    // Getters and setters for all fields

    public String getOperation() {
        return operation;
    }
    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOwner() {
        return owner;
    }
    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getRepo() {
        return repo;
    }
    public void setRepo(String repo) {
        this.repo = repo;
    }

    public String getNewBranch() {
        return newBranch;
    }
    public void setNewBranch(String newBranch) {
        this.newBranch = newBranch;
    }

    public String getBaseSha() {
        return baseSha;
    }
    public void setBaseSha(String baseSha) {
        this.baseSha = baseSha;
    }

    public String getBaseBranch() {
        return baseBranch;
    }
    public void setBaseBranch(String baseBranch) {
        this.baseBranch = baseBranch;
    }

    public String getFilePath() {
        return filePath;
    }
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getCommitMessage() {
        return commitMessage;
    }
    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }

    public String getContent() {
        return content;
    }
    public void setContent(String content) {
        this.content = content;
    }

    public String getFileSha() {
        return fileSha;
    }
    public void setFileSha(String fileSha) {
        this.fileSha = fileSha;
    }

    public String getBaseBranchForMerge() {
        return baseBranchForMerge;
    }
    public void setBaseBranchForMerge(String baseBranchForMerge) {
        this.baseBranchForMerge = baseBranchForMerge;
    }

    public String getHeadBranch() {
        return headBranch;
    }
    public void setHeadBranch(String headBranch) {
        this.headBranch = headBranch;
    }

    public String getSor() {
        return sor;
    }
    public void setSor(String sor) {
        this.sor = sor;
    }

    public String getFeedName() {
        return feedName;
    }
    public void setFeedName(String feedName) {
        this.feedName = feedName;
    }

    public String getFileType() {
        return fileType;
    }
    public void setFileType(String fileType) {
        this.fileType = fileType;
    }
}
