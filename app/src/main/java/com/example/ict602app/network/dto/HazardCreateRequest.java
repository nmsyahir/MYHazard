package com.example.ict602app.network.dto;

public class HazardCreateRequest {
    public String title;
    public String type;
    public int severity;
    public double lat;
    public double lng;
    public String description;
    public String reportedBy;

    public HazardCreateRequest(String title, String type, int severity, double lat, double lng, String description, String reportedBy) {
        this.title = title;
        this.type = type;
        this.severity = severity;
        this.lat = lat;
        this.lng = lng;
        this.description = description;
        this.reportedBy = reportedBy;
    }
}
