package com.example.ict602app;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF = "ict602_session";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_NAME = "name";

    private final SharedPreferences sp;

    public SessionManager(Context ctx) {
        sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public void saveUser(String userId, String name) {
        sp.edit()
                .putString(KEY_USER_ID, userId)
                .putString(KEY_NAME, name)
                .apply();
    }

    public String getUserId() {
        return sp.getString(KEY_USER_ID, null);
    }

    public String getName() {
        return sp.getString(KEY_NAME, "User");
    }

    public boolean isLoggedIn() {
        return getUserId() != null;
    }

    public void logout() {
        sp.edit().clear().apply();
    }
}
