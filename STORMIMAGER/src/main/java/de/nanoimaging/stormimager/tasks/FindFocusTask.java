package de.nanoimaging.stormimager.tasks;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import de.nanoimaging.stormimager.acquisition.ZFocusInterface;
import de.nanoimaging.stormimager.camera.CameraInterface;
import de.nanoimaging.stormimager.camera.capture.YuvImageCapture;
import de.nanoimaging.stormimager.utils.OpenCVUtil;
import de.nanoimaging.stormimager.utils.SharedValues;

public class FindFocusTask extends AbstractTask<ZFocusInterface> implements YuvImageCapture.YuvToBitmapEvent {

    private final String TAG = FindFocusTask.class.getSimpleName();
    int val_focus_pos_global_old = 0;
    int val_focus_pos_global = 0;
    int i_search_bestfocus = 0;
    int val_focus_pos_best_global = 0;
    int val_focus_searchradius = 40;
    int val_focus_search_stepsize = 1;
    double val_stdv_max = 0;                            // for focus stdv

    private final BlockingQueue<Mat> mats_to_process;

    public FindFocusTask(CameraInterface cameraInterface, ZFocusInterface zFocusInterface, SharedValues sharedValues)
    {
        super(cameraInterface,zFocusInterface,sharedValues);
        mats_to_process = new ArrayBlockingQueue<>(3);
    }


    @Override
    public boolean preProcess() {
        if (isworking) {
            isworking = false;
            return false;
        }
        try {
            cameraInterface.captureImage();
        } catch (Exception e) {
            e.printStackTrace();
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
            try {
                input = mats_to_process.take();
                Log.d(TAG,"run: take mate");
            } catch (InterruptedException e) {
                e.printStackTrace();
                input = null;
            }
            if(input == null)
                return;
            dst = OpenCVUtil.getBGRMatFromYuvMat(input);

            // reset the lens's position in the first iteration by some value
            if (i_search_bestfocus == 0) {
                val_stdv_max = 0;
                val_focus_pos_global_old = 0; // Save the value for later
                val_focus_pos_global = -val_focus_searchradius;
                // reset lens position
                messageEvent.setZFocus(-val_focus_searchradius);
                try {
                    Thread.sleep(3000);
                } catch (Exception e) {
                    Log.e(TAG, String.valueOf(e));
                }
            }
            val_focus_pos_global = i_search_bestfocus;

            // first increase the lens position
            int val_lens_x_global = sharedValues.getVal_lens_x_global();
            sharedValues.setVal_lens_x_global((val_lens_x_global + val_focus_search_stepsize));
            messageEvent.setZFocus(val_focus_search_stepsize);

            // then measure the focus quality
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

            // break if algorithm reaches the maximum of lens positions
            if (i_search_bestfocus >= (2 * val_focus_searchradius)) {
                // if maximum number of search iteration is reached, break
                if (val_focus_pos_best_global == 0) {
                    val_focus_pos_best_global = val_focus_pos_global_old;
                }

                // Go to position with highest stdv
                messageEvent.setZFocus(-(2 * val_focus_searchradius) + val_focus_pos_best_global);

                i_search_bestfocus = 0;
                Log.i(TAG, "My final focus is at z=" + String.valueOf(val_focus_pos_best_global) + '@' + String.valueOf(val_stdv_max));
            }
        }
        // free memory

        src.release();
        dst.release();
        input.release();
        for (Mat mat :mats_to_process) {
            mat.release();
        }
        mats_to_process.clear();
        System.gc();
    }

    @Override
    public void onYuvMatCompleted(Mat bitmap) {
        Log.d(TAG, "onYuvMapCompleted: " + (bitmap != null));
        if (bitmap != null) {
            /*if (mats_to_process.remainingCapacity() == 0) {
                try {
                    Mat mat = mats_to_process.take();
                    mat.release();

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }*/
            try {
                Log.d(TAG, "onYuvMapCompleted remaining size" + mats_to_process.remainingCapacity());
                mats_to_process.put(bitmap);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (isWorking()) {
            try {
                cameraInterface.captureImage();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
