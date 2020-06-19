package de.nanoimaging.stormimager.tasks;

import android.os.SystemClock;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import de.nanoimaging.stormimager.acquisition.GuiMessageEvent;
import de.nanoimaging.stormimager.camera.CameraInterface;
import de.nanoimaging.stormimager.camera.VideoRecorder;
import de.nanoimaging.stormimager.camera.capture.YuvImageCapture;
import de.nanoimaging.stormimager.events.StartRecordingEvent;
import de.nanoimaging.stormimager.events.StopRecordingEvent;
import de.nanoimaging.stormimager.microscope.MicroScopeController;
import de.nanoimaging.stormimager.microscope.MicroScopeInterface;
import de.nanoimaging.stormimager.process.VideoProcessor;
import de.nanoimaging.stormimager.utils.OpenCVUtil;
import de.nanoimaging.stormimager.utils.SharedValues;

public class SofiMeasurementTask extends AbstractTask<GuiMessageEvent> {

    private final String TAG = SofiMeasurementTask.class.getSimpleName();
    long t = 0;
    int i_meas = 0;
    int n_meas = 20;
    String mypath;
    private VideoRecorder recorder;
    private YuvImageCapture yuvImageCapture;

    public SofiMeasurementTask(CameraInterface cameraInterface, GuiMessageEvent messageEvent, SharedValues sharedValues, MicroScopeInterface microScopeInterface,VideoRecorder recorder, String path) {
        super(cameraInterface, messageEvent, sharedValues, microScopeInterface);
        this.recorder = recorder;
        this.mypath = path;
    }

    public void setYuvImageCapture(YuvImageCapture yuvImageCapture)
    {
        this.yuvImageCapture = yuvImageCapture;
    }

    @Override
    public boolean preProcess() {
        if (isworking)
            isworking = false;
        else
            isworking = true;
        return isworking;
    }

    @Override
    public void run() {
// Wait for the data to propigate down the chain
        t = SystemClock.elapsedRealtime();

        // Start with a video measurement for XXX-seconds
        i_meas = 1;
        while (isworking) {
            // Do recalibration every  10 measurements
            // do lens calibration every n-th step
            if ((i_meas % sharedValues.val_nperiods_calibration) == 0) {
                startFindCoupling();
            } else if (isworking) {// if no coupling has to be done -> measure!
                startMeasurement();
            }
        }
    }

    private void startMeasurement() {
        microScopeInterface.setState(MicroScopeController.STATE_RECORD);
        // Once in a while update the GUI
        //publishProgress();
        messageEvent.onGuiMessage("Measurement: " + String.valueOf(i_meas) + '/' + String.valueOf(n_meas));
        // set lens to correct position
        microScopeInterface.setLensX(sharedValues.getVal_lens_x_global(), false);

        // turn on the laser
        microScopeInterface.setLaser(sharedValues.val_laser_red_global, false);

        // start video-capture
        EventBus.getDefault().post(new StartRecordingEvent(i_meas));

        // turn on fluctuation
        microScopeInterface.setSOFIZ(true, sharedValues.val_sofi_amplitude_z);
        mSleep(sharedValues.val_duration_measurement * 1000); //Let AEC stabalize if it's on

        // turn off fluctuation
        microScopeInterface.setSOFIZ(false, sharedValues.val_sofi_amplitude_z);

        mSleep(500); //Let AEC stabalize if it's on

        // stop video-capture
        EventBus.getDefault().post(new StopRecordingEvent());

        // turn off the laser
        microScopeInterface.setLaser(1, false);

        //TODO : Dirty hack for now since we don't have a proper laser
        mSleep(200); //Let AEC stabalize if it's on
        //setLensX(1); // Heavily detune the lens to reduce phototoxicity

        // Once in a while update the GUI
        messageEvent.onGuiMessage("Waiting for next measurements. " + (sharedValues.val_nperiods_calibration - i_meas) + "/" + sharedValues.val_nperiods_calibration + "left until recalibration");
        microScopeInterface.setState(MicroScopeController.STATE_WAIT);

        // only perform the measurements if the camera is not looking for best coupling
        messageEvent.onGuiMessage("Processing Video...");

        VideoProcessor vidproc = new VideoProcessor(recorder.getmCurrentFile().getAbsolutePath(), OpenCVUtil.ROI_SIZE, recorder.getFrameRate());
        vidproc.setupvideo();
        vidproc.process(10);
        vidproc.saveresult(mypath + File.separator + "VID_" + String.valueOf(i_meas) + ".png");

        for (int iwait = 0; iwait < sharedValues.val_period_measurement * 10; iwait++) {
            if (!isworking) break;
            messageEvent.onGuiMessage("Waiting: " + String.valueOf(iwait / 10) + "/" + String.valueOf(sharedValues.val_period_measurement) + "s");
            mSleep(100);
        }
        i_meas++;
    }

    private void startFindCoupling() {
        // turn on the laser
        microScopeInterface.setLaser(sharedValues.val_laser_red_global, false);
        Log.i(TAG, "Lens Calibration in progress");
        messageEvent.onGuiMessage("Lens Calibration in progress");
        isworking = false;
                /*is_findcoupling = true;
                is_measurement = false;*/
        i_meas++;
        //publishProgress();
        microScopeInterface.setState(MicroScopeController.STATE_CALIBRATION);
    }

    void mSleep(int sleepVal) {
        try {
            Thread.sleep(sleepVal);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
