package de.nanoimaging.stormimager.network;

import android.util.Log;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import de.nanoimaging.stormimager.StormApplication;
import de.nanoimaging.stormimager.acquisition.AcquireActivity;

public class MqttClient implements MqttClientInterface {


    private final String TAG = MqttClient.class.getSimpleName();
    /**
     * MQTT related stuff
     */
    MqttAndroidClient mqttAndroidClient;

    final String MQTT_USER = "username";
    final String MQTT_PASS = "pi";
    final String MQTT_CLIENTID = "STORMimager";
    String myIPAddress = "192.168.43.88";

    private MqttClientInterface.MessageEvent messageEventListner;

    public MqttClient(MqttClientInterface.MessageEvent messageEventListner)
    {
        this.messageEventListner = messageEventListner;

    }

    public void publishMessage(String pub_topic, String publishMessage) {

        Log.i(TAG, pub_topic + " " + publishMessage);
        try {
            MqttMessage message = new MqttMessage();
            message.setPayload(publishMessage.getBytes());
            mqttAndroidClient.publish(pub_topic, message);
            //addToHistory("Message Published");
            if (!mqttAndroidClient.isConnected()) {
                //addToHistory(mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
            Log.i(TAG, "Message sent: " + pub_topic + message);
        } catch (MqttException e) {
            sendMsg("Error while sending data");
            e.printStackTrace();
        }
    }

    public void stopConnection() {
        try {
            mqttAndroidClient.close();
            sendMsg("Connection closed - on purpose?");
        } catch (Throwable e) {
            sendMsg("Something went wrong - propbably no connection established?");
            Log.e(TAG, String.valueOf(e));
        }
    }

    @Override
    public void setIPAddress(String mIPaddress) {
        this.myIPAddress = mIPaddress;
    }

    @Override
    public String getIPAdress() {
        return myIPAddress;
    }

    @Override
    public void connect() {
        mqttAndroidClient = new MqttAndroidClient(StormApplication.getContext(), "tcp://" + myIPAddress, MQTT_CLIENTID);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String myIPAddress) {

                if (reconnect) {
                    //addToHistory("Reconnected to : " + myIPAddress);
                    // Because Clean Session is true, we need to re-subscribe
                    // subscribeToTopic();
                } else {
                    //addToHistory("Connected to: " + myIPAddress);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                //addToHistory("The Connection was lost.");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                //addToHistory("Incoming message: " + new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);
        mqttConnectOptions.setUserName(MQTT_USER);
        mqttConnectOptions.setPassword(MQTT_PASS.toCharArray());
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);

        try {
            //addToHistory("Connecting to " + myIPAddress);
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);

                    publishMessage("A phone has connected.", "");
                    // subscribeToTopic();

                    sendMsg("Connected");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    //addToHistory("Failed to connect to: " + myIPAddress);
                    sendMsg("Connection attemp failed");
                }
            });


        } catch (MqttException ex) {
            ex.printStackTrace();
        }
    }

    private void sendMsg(String msg)
    {
        if (messageEventListner != null)
            messageEventListner.onMessage(msg);
    }
}
