package de.nanoimaging.stormimager.tflite;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import de.nanoimaging.stormimager.acquisition.AcquireActivity;

public class TFLitePredict {


    // Tensorflow stuff
    Interpreter tflite;
    String MODEL_PATH = "mytflite.tflite";
    Context context;

    // define input/output shapes
    int Nx_in = 200;
    int Ny_in = 200;
    int Ntime = 50;

    int Nx_out = Nx_in;
    int Ny_out = Ny_in;


    static String TAG = "TFLitePredictor";


    public TFLitePredict(Context context, String mymodelfile) {
        this.MODEL_PATH = mymodelfile;
        this.context = context;

        Log.i(TAG, "Loading the TFLite model");
        loadModel(context, this.MODEL_PATH);

    }

    public void predict() {

        Log.i(TAG, "Predicting using the TFLite model");
        float[][][][] inputstack = new float[1][Nx_in][Ny_in][Ntime];
        float[][] output = doInference(inputstack);
/*        int direction = 0;
        Move move;
        boolean[] validMoves = {true, true, true, true};

        do {
            float max = 0.0f;
            for (int i = 0; i < 4; ++i) {
                if (predictions[0][i] > max) {
                    if(validMoves[i]) {
                        max = predictions[0][i];
                        direction = i;
                    }
                }
            }

            validMoves[direction] = false;
            move = Move.toMove(direction);
        }while(move == Move.toOpposite(aiLastMove) || !puzzle.checkMove(move));
*/
    }

    private float[][] doInference(float[][][][] inputBoard) {

        Log.i(TAG, "Do Inference using the TFLite model");
        float[][] outputVal = new float[Nx_out][Ny_out];
        try{
            tflite.run(inputBoard, outputVal);
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
            ByteBuffer buffer = loadModelFile(this.context.getAssets(), MODEL_PATH);
            tflite = new Interpreter(buffer);
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
