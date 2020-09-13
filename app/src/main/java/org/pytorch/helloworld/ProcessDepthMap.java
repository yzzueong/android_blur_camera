package org.pytorch.helloworld;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ProcessDepthMap {

    public static Bitmap run(Context context, Bitmap bitmap, Bitmap mask_257, ImageSegmentationModelExecutor imageSegmentationModelExecutor) throws IOException {
        Module module = null;
        module = Module.load(assetFilePath(context, "depth_models.pt"));

        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);

        // running the model
        final Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();
        int height = (int)outputTensor.shape()[2];
        int width = (int)outputTensor.shape()[3];
        System.out.println(height);
        System.out.println(bitmap.getHeight());
        System.out.println(width);
        System.out.println(bitmap.getWidth());


        // getting tensor content as java array of floats
        float[] scores = outputTensor.getDataAsFloatArray();

        Bitmap resized = imageSegmentationModelExecutor.scaleBitmap(mask_257, width, height);
        float sum = 0;
        int count = 0;
        //System.out.println(resized.getWidth());
        //System.out.println(resized.getHeight());
        for (int i=0; i< resized.getHeight(); i++){
            for (int j=0; j<resized.getWidth(); j++){
                if (resized.getPixel(j, i) == Color.BLACK) {
                    int index = i * resized.getWidth() + j;
                    sum += scores[index];
                    count++;
                    scores[index] = 0;
                }
                //System.out.print(scores[i*resized.getWidth()+j]);
                //System.out.print(' ');
            }
        }
        float avg = sum / count;

        //process depth to relative distance
        Bitmap temp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int k=0; k<scores.length; k++){
            int row = k / width;
            int col = k % width;
            float value = scores[k];
            if (value != 0){
                //value = Math.abs(value - avg) + 0.5f;
                value = Math.abs((value - avg) * 5);
            }
            switch ((int)Math.round(value)){
                case 0: temp.setPixel(col, row, Color.BLACK);
                        break;
                case 1: temp.setPixel(col, row, Color.BLUE);
                        break;
                case 2: temp.setPixel(col, row, Color.GREEN);
                        break;
                case 3: temp.setPixel(col, row, Color.GRAY);
                        break;
                default: temp.setPixel(col, row, Color.WHITE);
            }
        }

        Bitmap result = imageSegmentationModelExecutor.scaleBitmap(temp, bitmap.getWidth(), bitmap.getHeight());

        return result;
    }
    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }
}
