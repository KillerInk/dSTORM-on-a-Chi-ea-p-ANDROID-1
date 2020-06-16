package de.nanoimaging.stormimager.camera.capture;

import android.graphics.ImageFormat;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.util.Log;
import android.util.Size;

import org.opencv.core.Mat;

import de.nanoimaging.stormimager.utils.OpenCVUtil;

public class YuvImageCapture extends AbstractImageCapture {


    private final String TAG = YuvImageCapture.class.getSimpleName();

    public YuvImageCapture(Size size) {
        super(size, ImageFormat.YUV_420_888,true);
    }

    @Override
    public void onCaptureCompleted(Image image, CaptureResult result) {

    }
}
