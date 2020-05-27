package de.nanoimaging.stormimager.tflite;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Optional;

public class TFLitePredict {


    // Tensorflow stuff
    Interpreter tflite;
    String MODEL_PATH = "mytflite.tflite";
    Context context;

    // define input/output shapes
    int Nx = 50;
    int Ny = 50;
    int Ntime = 50;
    int Nscale = 1;


    static String TAG = "TFLitePredictor";


    public TFLitePredict(Context context, String mymodelfile, int Nx, int Ny, int Ntime, int Nscale) {
        this.MODEL_PATH = mymodelfile;
        this.context = context;

        Log.i(TAG, "Loading the TFLite model: "+mymodelfile);
        loadModel(context, this.MODEL_PATH);

        this.Nx = Nx;
        this.Ny = Ny;
        this.Ntime = Ntime;
        this.Nscale = Nscale;

    }

    public TFLitePredict(Context context, String mymodelfile, int Nx, int Ny, int Ntime) {
        this.MODEL_PATH = mymodelfile;
        this.context = context;

        Log.i(TAG, "Loading the TFLite model: "+mymodelfile);
        loadModel(context, this.MODEL_PATH);

        this.Nx = Nx;
        this.Ny = Ny;
        this.Ntime = Ntime;

    }

    public Mat predict(float[] TF_input) {

        Log.i(TAG, "Predicting using the TFLite model");
        float[] output = doInference(TF_input);
        Mat outmat  = new Mat(this.Nx*this.Nscale, this.Nx*this.Nscale, CvType.CV_32F);
        outmat.put( 0,0, output);

        return outmat;
    }

    private float[]doInference(float[] inputval) {

        Log.i(TAG, "Do Inference using the TFLite model");
        float[]outputVal = new float[this.Nx*this.Ny*(this.Nscale*2)];
        try{
            tflite.run(inputval, outputVal);
        }
        catch(Error e){
            Log.e(TAG, String.valueOf(e));
        }
        Log.i(TAG, "Done with Inference using the TFLite model");


        return outputVal;
    }


    /** Load TF Lite model. */
    private synchronized void loadModel(Context context, String MODEL_PATH) {
        try {
            /*
            https://community.arm.com/developer/ip-products/processors/b/ml-ip-blog/posts/an-introduction-to-machine-learning-on-mobile
            Interpreter.Options tfliteOptions = new Interpreter.Options();

            tfliteOptions.setNumThreads(4);

            Interpreter tfliteInterpreter = new Interpreter(model, tfliteOptions);
             */
            ByteBuffer buffer = loadModelFile(this.context.getAssets(), MODEL_PATH);
            tflite = new Interpreter(buffer);
            tflite.setNumThreads(4);
            Log.v(TAG, "TFLite model loaded.");
        } catch (IOException ex) {
            Log.e(TAG, ex.getMessage());
        }
    }


    /** Load TF Lite model from assets. */
    private static MappedByteBuffer loadModelFile(AssetManager assetManager, String MODEL_PATH) throws IOException {
        try (AssetFileDescriptor fileDescriptor = assetManager.openFd(MODEL_PATH);
             FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor())) {
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
        catch(Error e){
            Log.e(TAG, String.valueOf(e));
            return null;
        }
    }
}
