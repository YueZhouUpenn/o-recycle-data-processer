package com.company.recycle.model;

/**
 * 导入批次实体
 */
public class ImportBatch {
    private Long id;
    private String batchKey;
    private String fileName;
    private String fileType;
    private String importedAt;
    private int totalRows;
    private int newRows;
    private int skipRows;
    private int anomalyRows;
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getBatchKey() { return batchKey; }
    public void setBatchKey(String batchKey) { this.batchKey = batchKey; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public String getImportedAt() { return importedAt; }
    public void setImportedAt(String importedAt) { this.importedAt = importedAt; }
    
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    
    public int getNewRows() { return newRows; }
    public void setNewRows(int newRows) { this.newRows = newRows; }
    
    public int getSkipRows() { return skipRows; }
    public void setSkipRows(int skipRows) { this.skipRows = skipRows; }
    
    public int getAnomalyRows() { return anomalyRows; }
    public void setAnomalyRows(int anomalyRows) { this.anomalyRows = anomalyRows; }
}
