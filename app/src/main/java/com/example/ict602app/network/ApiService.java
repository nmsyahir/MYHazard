package com.example.ict602app.network;

import com.example.ict602app.network.dto.AuthResponse;
import com.example.ict602app.network.dto.LoginRequest;
import com.example.ict602app.network.dto.RegisterRequest;
import com.example.ict602app.network.dto.LocationRequest;
import com.example.ict602app.network.dto.Hazard;
import com.example.ict602app.network.dto.HazardCreateRequest;
import com.example.ict602app.network.dto.HazardCreateResponse;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {

    @POST("api/location")
    Call<ResponseBody> postLocation(@Body LocationRequest body);

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest body);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest body);

    @GET("api/hazards")
    Call<List<Hazard>> getHazards();

    // ✅ server-side nearby filter
    @GET("api/hazards")
    Call<List<Hazard>> getHazardsNearby(
            @Query("lat") double lat,
            @Query("lng") double lng,
            @Query("radiusKm") double radiusKm
    );

    // ✅ crowdsource hazard
    @POST("api/hazards")
    Call<HazardCreateResponse> postHazard(@Body HazardCreateRequest body);
}
