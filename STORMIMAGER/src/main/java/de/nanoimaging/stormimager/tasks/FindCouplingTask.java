package de.nanoimaging.stormimager.tasks;

import android.media.Image;
import android.util.Log;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import de.nanoimaging.stormimager.acquisition.GuiMessageEvent;
import de.nanoimaging.stormimager.camera.CameraInterface;
import de.nanoimaging.stormimager.camera.capture.YuvImageCapture;
import de.nanoimaging.stormimager.microscope.MicroScopeInterface;
import de.nanoimaging.stormimager.utils.OpenCVUtil;
import de.nanoimaging.stormimager.utils.SharedValues;

public class FindCouplingTask extends AbstractTask<GuiMessageEvent> {

    private final String TAG = FindCouplingTask.class.getSimpleName();
    private YuvImageCapture yuvimagecapture;

    // settings for coupling
    double val_mean_max = 0;                            // for coupling intensity
    int i_search_maxintensity = 0;                      // global counter for number of search steps
    int val_lens_x_maxintensity = 0;                    // lens-position for maximum intensity
    int val_lens_x_global_old = 0;                      // last lens position before optimization
    boolean is_findcoupling_coarse = true;              // State if coupling is in fine mode
    boolean is_findcoupling_fine = false;               // State if coupling is in coarse mode

    /**
     * HARDWARE Settings for MQTT related values
     */
    int PWM_RES = (int) (Math.pow(2, 15)) - 1;          // bitrate of the PWM signal 15 bit

    public FindCouplingTask(CameraInterface cameraInterface, GuiMessageEvent messageEvent, SharedValues sharedValues, MicroScopeInterface microScopeInterface) {
        super(cameraInterface, messageEvent, sharedValues,microScopeInterface);
    }

    public void setYuvImageCapture(YuvImageCapture yuvImageCapture)
    {
        this.yuvimagecapture = yuvImageCapture;
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

        while (isworking)
        {
            if (is_findcoupling_coarse) {
                calibration_coarse();
            } else if (is_findcoupling_fine) {
                calibration_fine();
            }
        }
    }

    private void calibration_coarse()
    {
        try {
            // convert the Bitmap coming from the camera frame to MAT
            Mat src = new Mat();
            Mat dst = new Mat();
            Image img = yuvimagecapture.pollImage();
            src = OpenCVUtil.getBGRMatFromYuvMat(OpenCVUtil.yuvToMat(img));
            yuvimagecapture.releaseImage(img);
            Imgproc.cvtColor(src, dst, Imgproc.COLOR_BGRA2BGR);


            // reset the lens's position in the first iteration by some value
            if (i_search_maxintensity == 0) {
                val_lens_x_global_old = sharedValues.getVal_lens_x_global(); // Save the value for later
                sharedValues.setVal_lens_x_global(0);
                val_mean_max = 0;
                val_lens_x_maxintensity = 0;
                microScopeInterface.setLensX(sharedValues.getVal_lens_x_global(),isworking);
            }
            else {
                int i_mean = (int) OpenCVUtil.measureCoupling(dst, OpenCVUtil.ROI_SIZE, 9);
                String mycouplingtext = "Coupling (coarse) @ "+String.valueOf(i_search_maxintensity)+" is "+String.valueOf(i_mean)+"with max: "+String.valueOf(val_mean_max);
                messageEvent.onGuiMessage(mycouplingtext);
                Log.i(TAG, mycouplingtext);
                if (i_mean > val_mean_max) {
                    // Save the position with maximum intensity
                    val_mean_max = i_mean;
                    val_lens_x_maxintensity = sharedValues.getVal_lens_x_global();
                }

            }
            // break if algorithm reaches the maximum of lens positions
            if (sharedValues.getVal_lens_x_global() > PWM_RES) {
                // if maximum number of search iteration is reached, break
                if (val_lens_x_maxintensity == 0) {
                    val_lens_x_maxintensity = val_lens_x_global_old;
                }
                sharedValues.setVal_lens_x_global(val_lens_x_maxintensity);
                microScopeInterface.setLensX(sharedValues.getVal_lens_x_global(),isworking);

                i_search_maxintensity = 0;
                //exit = true;
                is_findcoupling_coarse = false;
                is_findcoupling_fine = true;
                Log.i(TAG, "My final Mean/STDV (coarse) is:" + String.valueOf(val_mean_max) + "@" + String.valueOf(val_lens_x_maxintensity));

            } else {
                // increase the lens position
                sharedValues.setVal_lens_x_global(sharedValues.getVal_lens_x_global()+400);
                microScopeInterface.setLensX(sharedValues.getVal_lens_x_global(),isworking);
                // free memory
                System.gc();
                src.release();
                dst.release();
            }
            // release camera

            i_search_maxintensity++;

        }
        catch(Exception v){
            v.printStackTrace();
        }
    }

    private void calibration_fine()
    {
        try {
            // convert the Bitmap coming from the camera frame to MAT
            Mat src = new Mat();
            Mat dst = new Mat();
            Image img = yuvimagecapture.pollImage();
            src = OpenCVUtil.getBGRMatFromYuvMat(OpenCVUtil.yuvToMat(img));
            yuvimagecapture.releaseImage(img);
            Imgproc.cvtColor(src, dst, Imgproc.COLOR_BGRA2BGR);

            // reset the lens's position in the first iteration by some value
            if (i_search_maxintensity == 0) {
                val_lens_x_global_old = sharedValues.getVal_lens_x_global(); // Save the value for later
                sharedValues.setVal_lens_x_global(sharedValues.getVal_lens_x_global()-200);
            }
            i_search_maxintensity++;


            int i_mean = (int) OpenCVUtil.measureCoupling(dst, OpenCVUtil.ROI_SIZE, 9);
            String mycouplingtext = "Coupling (fine) @ "+String.valueOf(i_search_maxintensity)+" is "+String.valueOf(i_mean)+"with max: "+String.valueOf(val_mean_max);
            messageEvent.onGuiMessage(mycouplingtext);

            Log.i(TAG, mycouplingtext);
            if (i_mean > val_mean_max) {
                // Save the position with maximum intensity
                val_mean_max = i_mean;
                val_lens_x_maxintensity = sharedValues.getVal_lens_x_global();
            }


            // break if algorithm reaches the maximum of lens positions
            if (sharedValues.getVal_lens_x_global() > val_lens_x_global_old + 200) {
                // if maximum number of search iteration is reached, break
                if (val_lens_x_maxintensity == 0) {
                    val_lens_x_maxintensity = val_lens_x_global_old;
                }
                sharedValues.setVal_lens_x_global(val_lens_x_maxintensity);
                microScopeInterface.setLensX(sharedValues.getVal_lens_x_global(),isworking);
                isworking = false;
                i_search_maxintensity = 0;
                //exit = true;
                is_findcoupling_fine = false;
                is_findcoupling_coarse = true;
                Log.i(TAG, "My final Mean/STDV (fine) is:" + String.valueOf(val_mean_max) + "@" + String.valueOf(val_lens_x_maxintensity));
                microScopeInterface.setLaser(0,isworking);
            }

            // increase the lens position
            sharedValues.setVal_lens_x_global(sharedValues.getVal_lens_x_global()+10);
            microScopeInterface.setLensX(sharedValues.getVal_lens_x_global(),isworking);

            // free memory
            System.gc();
            src.release();
            dst.release();
        } catch (Exception v) {
            v.printStackTrace();
        }
    }
}
