package de.nanoimaging.stormimager.camera.capture;

import android.graphics.ImageFormat;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.util.Log;
import android.util.Size;

import org.opencv.core.Mat;

import de.nanoimaging.stormimager.utils.OpenCVUtil;

public class YuvImageCapture extends AbstractImageCapture {

    public interface YuvToBitmapEvent
    {
        void onYuvMatCompleted(Mat bitmap);
    }

    private final String TAG = YuvImageCapture.class.getSimpleName();
    private YuvToBitmapEvent yuvToBitmapEventListner;

    public YuvImageCapture(Size size) {
        super(size, ImageFormat.YUV_420_888);
    }

    public void setYuvToBitmapEventListner(YuvToBitmapEvent eventListner)
    {
        this.yuvToBitmapEventListner = eventListner;
    }

    @Override
    public synchronized void onCaptureCompleted(Image image, CaptureResult result) {
        Log.d(TAG, "onCaptureCompleted");
        try {
            if (yuvToBitmapEventListner != null)
                yuvToBitmapEventListner.onYuvMatCompleted(OpenCVUtil.yuvToMat(image));
            else
                Log.d(TAG, "YuvToBitmapListner is null");
        }
        catch (IllegalStateException ex)
        {
            if (yuvToBitmapEventListner != null)
                yuvToBitmapEventListner.onYuvMatCompleted(null);
            Log.d(TAG, "Image Already Closed");
        }


        super.onCaptureCompleted(image, result);
    }
}
