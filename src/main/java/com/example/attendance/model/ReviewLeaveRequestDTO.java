package com.example.attendance.model;

public class ReviewLeaveRequestDTO {

    private boolean approve;
    private String remarks;

    public ReviewLeaveRequestDTO() {
    }

    public ReviewLeaveRequestDTO(boolean approve, String remarks) {
        this.approve = approve;
        this.remarks = remarks;
    }

    public boolean isApprove() {
        return approve;
    }

    public void setApprove(boolean approve) {
        this.approve = approve;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
