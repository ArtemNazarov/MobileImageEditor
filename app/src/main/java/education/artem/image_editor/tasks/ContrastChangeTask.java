package education.artem.image_editor.tasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.Map;

import education.artem.image_editor.BitmapHandle;
import education.artem.image_editor.CurrentOperation;
import education.artem.image_editor.Histogram;
import education.artem.image_editor.OperationName;
import education.artem.image_editor.ProcessTask;

public class ContrastChangeTask extends ProcessTask {


    public ContrastChangeTask(Context currContext, ImageView imageView, TextView status, ProgressBar progress, TextView exec, TextView cancelView) {
        super(currContext, imageView, status, progress, exec, cancelView);
    }

    @Override
    protected Bitmap doInBackground(OperationName... params) {
        try {
            OperationName currentOperation = params[0];
            Map<String, String> operationParams = CurrentOperation.getOperationParams();
            double threshold = 1;
            if (operationParams.size() > 0) {
                String thresholdStr = CurrentOperation.getOperationParams().get("threshold");
                if (thresholdStr != null) {
                    threshold = Double.parseDouble(thresholdStr);
                }
            }
            switch (currentOperation) {
                case EQUALIZE_CONTRAST:
                    return equalizeHistogram(BitmapHandle.getBitmapSource());
                case LINEAR_CONTRAST:
                    return adjustContrast(BitmapHandle.getBitmapSource(), threshold);
            }
        } catch (Exception e) {
            this.e = e;
            if (e.getMessage() != null) {
                Log.e(getClass().getName(), e.getMessage());
            }
            cancel(true);
        }
        return null;
    }

    private Histogram buildImageHistogram(Bitmap image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int size = 256;
        Histogram hist = new Histogram(size);
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (!(i == 0 || j == 0 || i == width - 1 || j == height - 1)) {
                    int color = image.getPixel(i - 1, j - 1);
                    int R = Color.red(color);
                    hist.getRed()[R] += 1;
                    int B = Color.blue(color);
                    hist.getBlue()[B] += 1;
                    int G = Color.green(color);
                    hist.getGreen()[G] += 1;
                }
            }

        }

        return hist;
    }

    private Bitmap equalizeHistogram(Bitmap image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int size = 256;
        Bitmap newImage = image.copy(image.getConfig(), true);
        Histogram hist = buildImageHistogram(image);
        if (isCancelled()) {
            return null;
        }
        for (int i = 0; i < size; i++) {
            hist.getRed()[i] /= (width * height);
            hist.getGreen()[i] /= (width * height);
            hist.getBlue()[i] /= (width * height);
        }
        for (int i = 1; i < size; i++) {
            hist.getRed()[i] = hist.getRed()[i - 1] + hist.getRed()[i];
            hist.getGreen()[i] = hist.getGreen()[i - 1] + hist.getGreen()[i];
            hist.getBlue()[i] = hist.getBlue()[i - 1] + hist.getBlue()[i];
        }

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (isCancelled()) {
                    return null;
                }
                if (!(i == 0 || j == 0 || i == width - 1 || j == height - 1)) {
                    int color = image.getPixel(i - 1, j - 1);
                    int indexR = Color.red(color);
                    int indexG = Color.green(color);
                    int indexB = Color.blue(color);
                    int red = (int) (hist.getRed()[indexR] * size);
                    int green = (int) (hist.getGreen()[indexG] * size);
                    int blue = (int) (hist.getBlue()[indexB] * size);
                    newImage.setPixel(i, j, Color.rgb(red, green, blue));
                }
                else {
                    int color = image.getPixel(i, j);
                    int indexR = Color.red(color);
                    int indexG = Color.green(color);
                    int indexB = Color.blue(color);
                    newImage.setPixel(i, j, Color.rgb(indexR, indexG, indexB));

                }
            }
            double progress = (double)i/width * 100;

            publishProgress((int)progress);
        }
        return newImage;
    }

    private Bitmap adjustContrast(Bitmap image, double threshold) {
        Bitmap newImage = image.copy(image.getConfig(), true);
        int height = image.getHeight();
        int width = image.getWidth();
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (isCancelled()) {
                    return null;
                }
                if (!(i == 0 || j == 0 || i == width - 1 || j == height - 1)) {
                    int color = image.getPixel(i - 1, j - 1);
                    int indexR = Color.red(color);
                    int indexG = Color.green(color);
                    int indexB = Color.blue(color);
                    double red = (((double) indexR / 255 - 0.5) * threshold + 0.5) * 255;
                    double green = (((double) indexG / 255 - 0.5) * threshold + 0.5) * 255;
                    double blue = (((double) indexB / 255 - 0.5) * threshold + 0.5) * 255;
                    indexR = (int) red;
                    indexR = Math.min(indexR, 255);
                    indexR = Math.max(indexR, 0);
                    indexG = (int) green;
                    indexG = Math.min(indexG, 255);
                    indexG = Math.max(indexG, 0);
                    indexB = (int) blue;
                    indexB = Math.min(indexB, 255);
                    indexB = Math.max(indexB, 0);
                    newImage.setPixel(i, j, Color.rgb(indexR, indexG, indexB));
                }
            }
            double progress = (double)i/width * 100;
            publishProgress((int)progress);
        }
        return newImage;
    }

    private Bitmap linearContrast(Bitmap image, int threshold) {
        ByteBuffer pixelBuffer = ByteBuffer.allocate(image.getByteCount());
        ByteBuffer resultBuffer = ByteBuffer.allocate(image.getByteCount());
        image.copyPixelsToBuffer(pixelBuffer);
        Bitmap newImage = image.copy(image.getConfig(), true);
        double contrastLevel = Math.pow((100.0 + threshold) / 100.0, 2);


        double blue;
        double green;
        double red;
        int blueInt;
        int greenInt;
        int redInt;

        for (int k = 0; k + 4 < pixelBuffer.array().length; k += 4) {
            blue = ((((pixelBuffer.array()[k] / 255) - 0.5) *
                    contrastLevel) + 0.5);


            green = ((((pixelBuffer.array()[k + 1] / 255) - 0.5) *
                    contrastLevel) + 0.5);


            red = ((((pixelBuffer.array()[k + 2]/255) - 0.5) *
                    contrastLevel) + 0.5);


            if  (blue > 255)
            { blue = 255; }
            else if  (blue < 0)
            { blue = 0; }


            if (green > 255)
            { green = 255; }
            else if (green < 0)
            { green = 0; }


            if (red > 255) {
                red = 255;
            } else if (red < 0) {
                red = 0;
            }


            blueInt = (int) blue;
            greenInt = (int) green;
            redInt = (int) red;
            pixelBuffer.array()[k] = (byte) blueInt;
            pixelBuffer.array()[k + 1] = (byte) greenInt;
            pixelBuffer.array()[k + 2] = (byte) redInt;
        }

        newImage.copyPixelsFromBuffer(resultBuffer);
        return newImage;
    }


}
