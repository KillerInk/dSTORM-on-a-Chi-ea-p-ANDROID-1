package de.nanoimaging.stormimager.camera.capture;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.util.Log;
import android.util.Size;

import org.opencv.core.Mat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

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
    public void onCaptureCompleted(Image image, CaptureResult result) {
        Log.d(TAG, "onCaptureCompleted");
        try {
            if (yuvToBitmapEventListner != null)
                yuvToBitmapEventListner.onYuvMatCompleted(OpenCVUtil.imageToMat(image));
            else
                Log.d(TAG, "YuvToBitmapListner is null");
        }
        catch (IllegalStateException ex)
        {
            Log.d(TAG, "Image Already Closed");
        }


        super.onCaptureCompleted(image, result);
    }
}
