package com.codexswitcher.model;

public class CloudAuthSession {

    private Long userId;
    private String username;
    private String token;

    public boolean isLoggedIn() {
        return token != null && !token.isBlank();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void clear() {
        userId = null;
        username = null;
        token = null;
    }
}
