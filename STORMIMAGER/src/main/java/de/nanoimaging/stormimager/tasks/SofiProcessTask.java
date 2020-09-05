package de.nanoimaging.stormimager.tasks;

import android.graphics.Bitmap;
import android.media.Image;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.nanoimaging.stormimager.StormApplication;
import de.nanoimaging.stormimager.acquisition.GuiMessageEvent;
import de.nanoimaging.stormimager.camera.CameraInterface;
import de.nanoimaging.stormimager.camera.capture.YuvImageCapture;
import de.nanoimaging.stormimager.microscope.MicroScopeInterface;
import de.nanoimaging.stormimager.tflite.TFLitePredict;
import de.nanoimaging.stormimager.utils.ImageUtils;
import de.nanoimaging.stormimager.utils.OpenCVUtil;
import de.nanoimaging.stormimager.utils.SharedValues;

public class SofiProcessTask extends AbstractTask<GuiMessageEvent> {

    private final String TAG = SofiProcessTask.class.getSimpleName();

    private YuvImageCapture yuvImageCapture;

    // Tensorflow stuff
    int Nx_in = 128;
    int Ny_in = Nx_in;
    int N_time = 20;
    int N_upscale = 2; // Upscalingfactor

    int i_time = 0;     // global counter for timesteps to feed the neural network
    private TFLitePredict mypredictor;
    private TFLitePredict mypredictor_mean;
    private TFLitePredict mypredictor_stdv;
    String mymodelfile = "converted_model128_20_keras.tflite";
    String mymodelfile_mean = "converted_model_mean.tflite";
    String mymodelfile_stdv = "converted_model_stdv.tflite";




    public SofiProcessTask(CameraInterface cameraInterface, GuiMessageEvent messageEvent, SharedValues sharedValues, MicroScopeInterface microScopeInterface) {
        super(cameraInterface, messageEvent, sharedValues, microScopeInterface);
        // load tensorflow stuff
        mypredictor = new TFLitePredict(StormApplication.getContext(), mymodelfile, Nx_in, Ny_in, N_time, N_upscale);
        mypredictor_mean = new TFLitePredict(StormApplication.getContext(), mymodelfile_mean, Nx_in, Ny_in, N_time);
        mypredictor_stdv = new TFLitePredict(StormApplication.getContext(), mymodelfile_stdv, Nx_in, Ny_in, N_time);
    }

    public void setYuvImageCapture(YuvImageCapture yuvImageCapture)
    {
        this.yuvImageCapture = yuvImageCapture;
    }

    @Override
    public boolean preProcess() {
        isworking = !isworking;
        return isworking;
    }

    @Override
    public void run() {
        Mat src = new Mat();
        Mat grayMat = new Mat();
        List<Mat> listMat = new ArrayList<>();
        // define ouput Data to store result
        float[] TF_input = new float[(int)(Nx_in*Ny_in*N_time)];
        // Need to convert TestMat to float array to feed into TF
        MatOfFloat TF_input_f = new MatOfFloat(CvType.CV_32F);
        Rect roi = null;

        while (isworking) {
            for (int i = 0; i < N_time;i++) {

                // convert the Bitmap coming from the camera frame to MAT and crop it
                Image img = yuvImageCapture.pollImage();
                if (img == null)
                    return;
                src = OpenCVUtil.getBGRMatFromYuvMat(OpenCVUtil.yuvToMat(img));
                yuvImageCapture.releaseImage(img);
                if (roi == null)
                    roi = new Rect((int) src.width() / 2 - Nx_in / 2, (int) src.height() / 2 - Ny_in / 2, Nx_in, Ny_in);
                Imgproc.cvtColor(src, grayMat, Imgproc.COLOR_BGRA2BGR);
                // Extract one frame channel
                List<Mat> rgb_list = new ArrayList(3);
                Core.split(grayMat, rgb_list);
                rgb_list.get(0).copyTo(grayMat);

                Mat dst = new Mat(grayMat, roi);
                try {
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // accumulate the result of all frames
                //dst.convertTo(dst, CvType.CV_32FC1);

                // preprocess the frame
                // dst = preprocess(dst);
                //Log.e(TAG,dst.dump());

                // convert MAT to MatOfFloat
                dst.convertTo(TF_input_f, CvType.CV_32F);
                //Log.e(TAG,dst.dump());

                // Add the frame to the list
                listMat.add(TF_input_f);
                Log.d(TAG, "captured img:" + i + "/"+N_time+ " list size:" + listMat.size());
                dst.release();
            }
            Log.i(TAG,"capture frames dones");
            //is_process_sofi = false;

            // If a stack of batch_size images is loaded, feed it into the TF object and run iference
            Mat tmp_dst = new Mat();
            Core.merge(listMat, tmp_dst); // Log.i(TAG, String.valueOf(tmp_dst));
            for (Mat m : listMat)
                m.release();
            listMat.clear();

            // define ouput Data to store result
            // TF_output = new float[(int) (OUTPUT_SIZE[0]*OUTPUT_SIZE[1]*OUTPUT_SIZE[2]*OUTPUT_SIZE[3])];

            // get the frame/image and allocate it in the MOF object
            tmp_dst.get(0, 0, TF_input);

            tmp_dst.release();

            Log.i(TAG, "All frames have been accumulated");

            String is_output_nn = "nn_sofi"; // nn_stdv, nn_mean, nn_sofi
            Mat myresult = null;
            if (is_output_nn.equals("nn_sofi")) {
                myresult = mypredictor.predict(TF_input);
            } else if (is_output_nn.equals("nn_stdv")) {
                myresult = mypredictor_mean.predict(TF_input);
            } else {
                myresult = mypredictor_stdv.predict(TF_input);
            }

            String mytimestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.US).format(new Date());
            String myresultpath = Environment.getExternalStorageDirectory() + "/STORMimager/" + mytimestamp + "_test.png";
            myresult = ImageUtils.imwriteNorm(myresult, myresultpath);

            Bitmap myresult_bmp = Bitmap.createBitmap(Nx_in * N_upscale, Ny_in * N_upscale, Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(myresult, myresult_bmp);
            Log.d(TAG,"send onUpdatePreviewImg:" + (myresult_bmp != null));
            messageEvent.onUpdatePreviewImg(myresult_bmp);

        }
        src.release();
        grayMat.release();
        System.gc();
    }
}
