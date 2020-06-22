package de.nanoimaging.stormimager.tasks;

import android.media.Image;
import android.util.Log;

import org.opencv.core.Mat;

import de.nanoimaging.stormimager.acquisition.GuiMessageEvent;
import de.nanoimaging.stormimager.camera.CameraInterface;
import de.nanoimaging.stormimager.camera.capture.YuvImageCapture;
import de.nanoimaging.stormimager.microscope.MicroScopeInterface;
import de.nanoimaging.stormimager.utils.OpenCVUtil;
import de.nanoimaging.stormimager.utils.SharedValues;

public class FindFocusTask extends AbstractTask<GuiMessageEvent> {

    private final String TAG = FindFocusTask.class.getSimpleName();
    int val_focus_pos_global_old = 0;
    int val_focus_pos_global = 0;
    int i_search_bestfocus = 0;
    int val_focus_pos_best_global = 0;
    int val_focus_searchradius = 40;
    int val_focus_search_stepsize = 1;
    double val_stdv_max = 0;                            // for focus stdv

    private YuvImageCapture yuvImageCapture;

    public FindFocusTask(CameraInterface cameraInterface, GuiMessageEvent zFocusInterface, SharedValues sharedValues, MicroScopeInterface microScopeInterface)
    {
        super(cameraInterface,zFocusInterface,sharedValues,microScopeInterface);
    }

    public void setYuvImageCapture(YuvImageCapture yuvImageCapture)
    {
        this.yuvImageCapture = yuvImageCapture;
    }

    @Override
    public boolean preProcess() {
        if (isworking) {
            isworking = false;
            return false;
        }
        isworking = true;
        return true;
    }

    @Override
    public void run() {
        Mat src = new Mat();
        Mat dst = new Mat();
        Mat input = null;
        while (isworking) {

            Image img = yuvImageCapture.pollImage();
            if(img == null)
                return;
            input = OpenCVUtil.yuvToMat(img);
            yuvImageCapture.releaseImage(img);
            dst = OpenCVUtil.getBGRMatFromYuvMat(input);

            // reset the lens's position in the first iteration by some value
            if (i_search_bestfocus == 0) {
                resetLensPosition();
            }
            val_focus_pos_global = i_search_bestfocus;

            // first increase the lens position
            increaseLensPosition();

            // then measure the focus quality
            measureFocusQuality(dst);

            // break if algorithm reaches the maximum of lens positions
            if (i_search_bestfocus >= (2 * val_focus_searchradius)) {
                // if maximum number of search iteration is reached, break
                if (val_focus_pos_best_global == 0) {
                    val_focus_pos_best_global = val_focus_pos_global_old;
                }

                // Go to position with highest stdv
                microScopeInterface.setZFocus(-(2 * val_focus_searchradius) + val_focus_pos_best_global);

                i_search_bestfocus = 0;
                Log.i(TAG, "My final focus is at z=" + String.valueOf(val_focus_pos_best_global) + '@' + String.valueOf(val_stdv_max));
            }
        }
        // free memory

        src.release();
        dst.release();
        input.release();
        System.gc();
    }

    private void measureFocusQuality(Mat dst) {
        i_search_bestfocus = i_search_bestfocus + val_focus_search_stepsize;

        double i_stdv = OpenCVUtil.measureCoupling(dst, OpenCVUtil.ROI_SIZE, 9);
        String myfocusingtext = "Focus @ " + String.valueOf(i_search_bestfocus) + " is " + String.valueOf(i_stdv);
        messageEvent.onGuiMessage(myfocusingtext);

        Log.i(TAG, myfocusingtext);
        if (i_stdv > val_stdv_max) {
            // Save the position with maximum intensity
            val_stdv_max = i_stdv;
            val_focus_pos_best_global = val_focus_pos_global;
        }
    }

    private void increaseLensPosition() {
        int val_lens_x_global = sharedValues.getVal_lens_x_global();
        sharedValues.setVal_lens_x_global((val_lens_x_global + val_focus_search_stepsize));
        microScopeInterface.setZFocus(val_focus_search_stepsize);
    }

    private void resetLensPosition() {
        val_stdv_max = 0;
        val_focus_pos_global_old = 0; // Save the value for later
        val_focus_pos_global = -val_focus_searchradius;
        // reset lens position
        microScopeInterface.setZFocus(-val_focus_searchradius);
        try {
            Thread.sleep(3000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
