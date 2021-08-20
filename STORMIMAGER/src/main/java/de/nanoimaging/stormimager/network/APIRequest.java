package de.nanoimaging.stormimager.network;

import org.json.JSONObject;

public class APIRequest {

    String endpoint;  // Create a class attribute
    JSONObject jsonObject;

    public APIRequest(String endpoint, JSONObject jsonObject) {
        this.endpoint = endpoint;
        this.jsonObject = jsonObject;
    }
}