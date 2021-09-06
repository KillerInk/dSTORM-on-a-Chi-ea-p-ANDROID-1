package de.nanoimaging.stormimager.network;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class APINetworking {
    private static String TAG = "restapi";
    public boolean is_available = false;
    public String base_url = "0.0.0.0";
    private boolean is_running = false;


    public APINetworking(String base_url){
        this.base_url = base_url;
        this.is_available = isOnline(base_url);
    }

    public void set_url(String base_url){
        this.base_url = base_url;
    }


    public void rest_post_async(String endpoint, JSONObject jsonobject)  {
        if (! this.is_running){
            APIRequest params = new APIRequest(endpoint, jsonobject);
            rest_post_task post_task = new rest_post_task();
            post_task.execute(params);
        }

    }

    public void rest_post_sync(String endpoint, JSONObject jsonobject) throws IOException {
        //Change the URL with any other publicly accessible POST resource, which accepts JSON request body
        if(this.is_available){
        URL url = new URL("http://"+this.base_url + endpoint);

        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");

        con.setRequestProperty("Content-Type", "application/json; utf-8");
        con.setRequestProperty("Accept", "application/json");

        con.setDoOutput(true);

        //JSON String need to be constructed for the specific resource.
        //We may construct complex JSON using any third-party JSON libraries such as jackson or org.json
        String jsonInputString = jsonobject.toString();
        Log.d(TAG, jsonInputString + " - " + url);

        try (OutputStream os = con.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }

        int code = con.getResponseCode();
        Log.d(TAG, String.valueOf(code));

        try (BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            System.out.println(response.toString());
        }
        catch (java.net.ConnectException e){
            this.is_available = false;
        }
        }
        else{
            Log.d(TAG, "ESP not available");
        }
    }

    public void reconnect(){
        // reset connection
        this.is_available = isOnline(this.base_url);
        this.is_running = false;
    }

    public boolean isOnline(String url) {
        Runtime runtime = Runtime.getRuntime();
        try {
            Process ipProcess = runtime.exec("/system/bin/ping -c 1 "+url);
            int     exitValue = ipProcess.waitFor();
            return (exitValue == 0);
        } catch (IOException e)          { e.printStackTrace(); }
        catch (InterruptedException e) { e.printStackTrace(); }
        return false;
    }

    private static class APIRequest {
        JSONObject jsonObject;
        String endpoint;

        APIRequest(String endpoint, JSONObject jsonObject) {
            this.jsonObject = jsonObject;
            this.endpoint = endpoint;
        }
    }





    private class rest_post_task extends AsyncTask<APIRequest, Void, Void> {
        @Override
        protected Void doInBackground(APIRequest... params) {
            String endpoint = params[0].endpoint;
            JSONObject jsonObject = params[0].jsonObject;
            try {
                is_running = true;
                rest_post_sync(endpoint, jsonObject);
                is_running = false;
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }
    }


}