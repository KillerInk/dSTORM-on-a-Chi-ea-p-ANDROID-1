package de.nanoimaging.stormimager.microscope;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import de.nanoimaging.stormimager.StormApplication;
import de.nanoimaging.stormimager.acquisition.GuiMessageEvent;
import de.nanoimaging.stormimager.network.MqttClient;
import de.nanoimaging.stormimager.network.MqttClientInterface;
import de.nanoimaging.stormimager.utils.SharedValues;

public class MicroScopeController implements MicroScopeInterface {

    public static final String STATE_CALIBRATION = "state_calib";       // STate signal sent to ESP for light signal
    public static final String STATE_WAIT = "state_wait";               // STate signal sent to ESP for light signal
    public static final String STATE_RECORD = "state_record";           // STate signal sent to ESP for light signal

    private final String TAG = MicroScopeController.class.getSimpleName();

    private MqttClientInterface mqttClientInterface;
    private final SharedValues sharedValues;
    // Global MQTT Values
    private final int MQTT_SLEEP = 250;                       // wait until next thing should be excuted
    public static final int PWM_RES = (int) (Math.pow(2, 15)) - 1;          // bitrate of the PWM signal 15 bit
    private final GuiMessageEvent guiMessageEventListner;

    public MicroScopeController(SharedValues sharedValues, GuiMessageEvent messageEventListner)
    {
        this.guiMessageEventListner = messageEventListner;
        this.mqttClientInterface = mqttClientInterface;
        this.sharedValues = sharedValues;
        // start MQTT
        mqttClientInterface = new MqttClient(new MqttClientInterface.MessageEvent() {
            @Override
            public void onMessage(String msg) {
                guiMessageEventListner.onShowToast(msg);
            }
        });
        if (true){//TODO: Not working, why?! isNetworkAvailable()) {
            guiMessageEventListner.onShowToast("Connecting MQTT");
            mqttClientInterface.connect();
        } else
            guiMessageEventListner.onShowToast("We don't have network");
    }



    @Override
    public void setZFocus(int stepsize) {
        if(stepsize>0) mqttClientInterface.set_focus_z_fwd(String.valueOf(Math.abs(stepsize)));
        if(stepsize<0) mqttClientInterface.set_focus_z_bwd(String.valueOf(Math.abs(stepsize)));

        try {Thread.sleep(stepsize*80); }
        catch (Exception e) { Log.e(TAG, String.valueOf(e));}
    }

    @Override
    public void setLaser(int laserintensity, boolean findcoupling) {
        if (laserintensity < PWM_RES && laserintensity>=0 ) {
            if (laserintensity ==  0)laserintensity=1;
            mqttClientInterface.set_laser(String.valueOf(lin2qudratic(laserintensity, PWM_RES)));
            // Wait until the command was actually sent
            if(findcoupling){
                try {
                    Thread.sleep(MQTT_SLEEP);
                } catch (Exception e) {
                    Log.e(TAG, String.valueOf(e));
                }
            }
        }
    }

    // SOME I/O thingys
    public int lin2qudratic(int input, int mymax) {
        double normalizedval = (double) input / (double) mymax;
        double quadraticval = Math.pow(normalizedval, 2);
        return (int) (quadraticval * (double) mymax);
    }

    @Override
    public void setLensX(int lensposition, boolean findcoupling) {
        if ((lensposition < PWM_RES) && (lensposition >=0)) {
            if (lensposition ==  0)lensposition=1;
            mqttClientInterface.set_lens_x(String.valueOf(lin2qudratic(lensposition, PWM_RES)));
            // Wait until the command was actually sent
            if(findcoupling){
                try {
                    Thread.sleep(MQTT_SLEEP);
                } catch (Exception e) {
                    Log.e(TAG, String.valueOf(e));
                }
            }
        }
    }

    @Override
    public void setLensZ(int lensposition, boolean findcoupling) {
        if (lensposition < PWM_RES && lensposition >= 0) {
            if (lensposition ==  0)lensposition=1;
            mqttClientInterface.set_lens_z(String.valueOf(lin2qudratic(lensposition, PWM_RES)));
            // Wait until the command was actually sent
            if(findcoupling){
                try {
                    Thread.sleep(MQTT_SLEEP);
                } catch (Exception e) {
                    Log.e(TAG, String.valueOf(e));
                }
            }
        }
    }

    @Override
    public void setSOFIX(boolean misSOFI_X, int mvalSOFIX) {
        sharedValues.val_sofi_amplitude_x = mvalSOFIX;
        sharedValues.is_SOFI_x = misSOFI_X;
        mqttClientInterface.set_lens_sofi_x(String.valueOf(sharedValues.val_sofi_amplitude_x));
    }

    @Override
    public void setSOFIZ(boolean misSOFI_Z, int mvalSOFIZ) {
        sharedValues.val_sofi_amplitude_z = mvalSOFIZ;
        sharedValues.is_SOFI_z = misSOFI_Z;
        mqttClientInterface.set_lens_sofi_z(String.valueOf(sharedValues.val_sofi_amplitude_z));
    }

    @Override
    public void setValSOFIX(int mval_sofi_amplitude_x) {
        sharedValues.val_sofi_amplitude_x = mval_sofi_amplitude_x;
    }

    @Override
    public void setValSOFIZ(int mval_sofi_amplitude_z) {
        sharedValues.val_sofi_amplitude_z = mval_sofi_amplitude_z;
    }

    @Override
    public void setValDurationMeas(int mval_duration_measurement) {
        sharedValues.val_duration_measurement = mval_duration_measurement;
    }

    @Override
    public void setValPeriodMeas(int mval_period_measurement) {
        sharedValues.val_period_measurement = mval_period_measurement;
    }

    @Override
    public void setNValPeriodCalibration(int mval_period_calibration) {
        sharedValues.val_nperiods_calibration = mval_period_calibration;
    }

    @Override
    public void setState(String state) {
        mqttClientInterface.setState(state);
    }

    @Override
    public void setIpAdress(String ipAdress) {
        mqttClientInterface.setIPAddress(ipAdress);
    }

    @Override
    public String getIpAdress() {
        return mqttClientInterface.getIPAdress();
    }

    @Override
    public void Reconnect() {
        mqttClientInterface.stopConnection();
        //mqttClientInterface.setIPAddress(mIP);
        mqttClientInterface.connect();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) StormApplication.getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}
