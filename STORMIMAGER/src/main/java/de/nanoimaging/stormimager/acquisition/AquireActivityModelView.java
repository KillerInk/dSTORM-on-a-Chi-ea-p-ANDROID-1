package de.nanoimaging.stormimager.acquisition;

import org.opencv.android.OpenCVLoader;

import de.nanoimaging.stormimager.camera.CameraImpl;
import de.nanoimaging.stormimager.databinding.ActivityAcquireBinding;

public class AquireActivityModelView implements AquireActivityModelViewInterface {

    private ActivityAcquireBinding activityViewbinding;
    CameraImpl cameraInterface;
    public AquireActivityModelView(ActivityAcquireBinding binding)
    {
        this.activityViewbinding = binding;
        // Initialize OpenCV using external library for now //TODO use internal!
        OpenCVLoader.initDebug();
        //OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);

        cameraInterface = new CameraImpl();
    }

    @Override
    public void onResume() {

    }

    @Override
    public void onPause() {

    }

    @Override
    public void onDestroy() {

    }
}
