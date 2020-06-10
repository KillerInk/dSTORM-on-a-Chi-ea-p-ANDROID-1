package de.nanoimaging.stormimager.network;

public interface MqttClientInterface {
    interface MessageEvent
    {
        void onMessage(String msg);
    }
    void publishMessage(String pub_topic, String publishMessage);
    void stopConnection();
    void setIPAddress(String mIPaddress);
    String getIPAdress();
    void connect();
}
