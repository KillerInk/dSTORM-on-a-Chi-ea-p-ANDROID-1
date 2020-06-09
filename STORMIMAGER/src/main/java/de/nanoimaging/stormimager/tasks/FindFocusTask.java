package de.nanoimaging.stormimager.tasks;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import de.nanoimaging.stormimager.acquisition.ZFocusInterface;
import de.nanoimaging.stormimager.camera.CameraInterface;
import de.nanoimaging.stormimager.camera.capture.YuvImageCapture;
import de.nanoimaging.stormimager.utils.OpenCVUtil;

public class FindFocusTask implements YuvImageCapture.YuvToBitmapEvent {

    private final String TAG = FindFocusTask.class.getSimpleName();
    private CameraInterface cameraInterface;
    private ZFocusInterface zFocusInterface;
    private Mat bitmap;
    private Object bitmapLock = new Object();
    private boolean searchForFocus = false;
    int val_focus_pos_global_old = 0;
    int val_focus_pos_global = 0;
    int i_search_bestfocus = 0;
    int val_focus_pos_best_global = 0;
    int val_focus_searchradius = 40;
    int val_focus_search_stepsize = 1;
    int val_lens_x_global = 0;                          // global position for the x-lens
    double val_stdv_max = 0;                            // for focus stdv


    public FindFocusTask(CameraInterface cameraInterface, ZFocusInterface zFocusInterface)
    {
        this.cameraInterface = cameraInterface;
        this.zFocusInterface = zFocusInterface;
    }

    public boolean isSearchForFocus()
    {
        return searchForFocus;
    }

    public void process()
    {
        if (searchForFocus) {
            searchForFocus = false;
            return;
        }
        searchForFocus = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Mat src = new Mat();
                Mat dst = new Mat();
                while (searchForFocus) {
                    try {
                        getNewBitmap();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (bitmap == null)
                    {
                        Log.d(TAG, "Bitmap is null");
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        return;
                    }
                    dst = OpenCVUtil.getBGRMatFromYuvMat(bitmap);

                    // reset the lens's position in the first iteration by some value
                    if (i_search_bestfocus == 0) {
                        val_stdv_max = 0;
                        val_focus_pos_global_old = 0; // Save the value for later
                        val_focus_pos_global = -val_focus_searchradius;
                        // reset lens position
                        zFocusInterface.setZFocus(-val_focus_searchradius);
                        try {
                            Thread.sleep(3000);
                        } catch (Exception e) {
                            Log.e(TAG, String.valueOf(e));
                        }
                    }
                    val_focus_pos_global = i_search_bestfocus;

                    // first increase the lens position
                    val_lens_x_global = val_lens_x_global + val_focus_search_stepsize;
                    zFocusInterface.setZFocus(val_focus_search_stepsize);

                    // then measure the focus quality
                    i_search_bestfocus = i_search_bestfocus + val_focus_search_stepsize;

                    double i_stdv = OpenCVUtil.measureCoupling(dst, OpenCVUtil.ROI_SIZE, 9);
                    String myfocusingtext = "Focus @ " + String.valueOf(i_search_bestfocus) + " is " + String.valueOf(i_stdv);
                    zFocusInterface.onGuiMessage(myfocusingtext);

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
                        zFocusInterface.setZFocus(-(2 * val_focus_searchradius) + val_focus_pos_best_global);

                        i_search_bestfocus = 0;
                        Log.i(TAG, "My final focus is at z=" + String.valueOf(val_focus_pos_best_global) + '@' + String.valueOf(val_stdv_max));
                    }
                }
                // free memory

                src.release();
                dst.release();
                bitmap.release();
                System.gc();
            }

        }).start();
    }

    public void stop()
    {
        searchForFocus = false;
    }

    private void getNewBitmap() throws Exception {
        synchronized (bitmapLock)
        {
            cameraInterface.captureImage();
            bitmapLock.wait();
        }
    }

    @Override
    public void onYuvMatCompleted(Mat bitmap) {
        synchronized (bitmapLock)
        {
            this.bitmap = bitmap;
            bitmapLock.notify();
        }
    }

}
