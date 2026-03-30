package com.company.recycle.model;

/**
 * 销售出库单实体
 */
public class Outbound {
    private String serialNo;
    private String materialCode;
    private String materialName;
    private String spec;
    private String outboundDate;
    private String docNo;
    private String orderNo;
    private String salesperson;
    private String salesDept;
    private String customer;
    private String endCustomer;
    private String docType;
    private String description;
    private Long batchId;
    
    // Getters and Setters
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
    
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    
    public String getOutboundDate() { return outboundDate; }
    public void setOutboundDate(String outboundDate) { this.outboundDate = outboundDate; }
    
    public String getDocNo() { return docNo; }
    public void setDocNo(String docNo) { this.docNo = docNo; }
    
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    
    public String getSalesperson() { return salesperson; }
    public void setSalesperson(String salesperson) { this.salesperson = salesperson; }
    
    public String getSalesDept() { return salesDept; }
    public void setSalesDept(String salesDept) { this.salesDept = salesDept; }
    
    public String getCustomer() { return customer; }
    public void setCustomer(String customer) { this.customer = customer; }
    
    public String getEndCustomer() { return endCustomer; }
    public void setEndCustomer(String endCustomer) { this.endCustomer = endCustomer; }
    
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
}
