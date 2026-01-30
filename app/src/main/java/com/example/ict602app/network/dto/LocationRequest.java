package com.example.ict602app.network.dto;

public class LocationRequest {
    public String userId;
    public String name;
    public double lat;
    public double lng;
    public String userAgent;
    public String address;


    public LocationRequest(String userId, String name, double lat, double lng, String userAgent, String address) {
        this.userId = userId;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.userAgent = userAgent;
        this.address = address;
    }
}
