package de.nanoimaging.stormimager.network;

public interface MqttClientInterface {

    String topic_lens_z = "lens/right/z";
    String topic_lens_x = "lens/right/x";
    String topic_laser = "laser/red";
    String topic_lens_sofi_z = "lens/right/sofi/z";
    String topic_lens_sofi_x = "lens/right/sofi/x";
    String topic_state = "state";
    String topic_focus_z_fwd = "stepper/z/fwd";
    String topic_focus_z_bwd = "stepper/z/bwd";

    interface MessageEvent
    {
        void onMessage(String msg);
    }
    void publishMessage(String pub_topic, String publishMessage);
    void stopConnection();
    void setIPAddress(String mIPaddress);
    String getIPAdress();
    void connect();
    void setState(String state);
    void set_lens_sofi_z(String z);
    void set_lens_sofi_x(String x);
    void set_lens_z(String z);
    void set_lens_x(String x);
    void set_laser(String laser);
    void set_focus_z_fwd(String focus);
    void set_focus_z_bwd(String focus);
}
