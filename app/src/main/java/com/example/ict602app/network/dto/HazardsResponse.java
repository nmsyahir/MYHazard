package com.example.ict602app.network.dto;

import java.util.List;

public class HazardsResponse {
    public boolean success;
    public String mode;
    public double radiusKm;
    public int count;
    public List<Hazard> data;
}
