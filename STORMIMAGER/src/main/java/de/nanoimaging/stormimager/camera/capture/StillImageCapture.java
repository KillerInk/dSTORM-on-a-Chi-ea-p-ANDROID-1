package de.nanoimaging.stormimager.camera.capture;

import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;
import android.util.Size;

public abstract class StillImageCapture extends AbstractImageCapture {

    private final String TAG = StillImageCapture.class.getSimpleName();

    public StillImageCapture(Size size, int format, boolean setToPreview) {
        super(size, format, setToPreview);
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        super.onImageAvailable(reader);
        checkIfCaptureCompleted();
    }

    @Override
    public void setCaptureResult(CaptureResult captureResult) {
        super.setCaptureResult(captureResult);
        checkIfCaptureCompleted();
    }

    private synchronized void checkIfCaptureCompleted()
    {
        Log.d(TAG, "checkIfCaptureCompleted :");
        Image img;
        CaptureResult captureResult;
        if ((img = imageBlockingQueue.peek()) != null && (captureResult = captureResultBlockingQueue.peek()) != null) {
            imageBlockingQueue.poll();
            captureResultBlockingQueue.poll();
            onCaptureCompleted(img, captureResult);
        }
    }

    @Override
    public void onCaptureCompleted(Image image, CaptureResult result)
    {

    }
}
