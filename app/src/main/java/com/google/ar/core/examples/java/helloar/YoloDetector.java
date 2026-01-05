package com.google.ar.core.examples.java.helloar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class YoloDetector {
    private Interpreter tfLite;
    private List<String> labels;
    private final int inputSize = 640;
    private final float confThreshold = 0.5f;
    private final float iouThreshold = 0.45f;

    // --- CONSTANTS FOR YOUR MODEL ---
    private final int OUTPUT_ROWS = 25200;
    private final int NUM_CLASSES = 19;
    private final int OUTPUT_COLUMNS = NUM_CLASSES + 5; // 24

    public static float latestXOffset = 0f;
    public static float latestYOffset = 0f;
    public static float latestScale = 1f;
    public static float originalImgW = 1f;
    public static float originalImgH = 1f;

    public YoloDetector(Context context, String modelPath, String labelPath) throws IOException {
        MappedByteBuffer model = FileUtil.loadMappedFile(context, modelPath);
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4); // Uses 4 CPU cores for faster AI
        options.addDelegate(new org.tensorflow.lite.nnapi.NnApiDelegate());

        tfLite = new Interpreter(model, options);
        labels = FileUtil.loadLabels(context, labelPath);
    }

    public List<Detection> detect(Bitmap bitmap) {
        // 1. Save original dimensions IMMEDIATELY (This should be 480x640)
        originalImgW = (float) bitmap.getWidth();
        originalImgH = (float) bitmap.getHeight();

        // 2. Run Preprocess (This creates the 640x640 buffer with 80px black bars)
        PreprocessResult result = preprocessBitmap(bitmap, 640);

        Log.d("FrameCheck", "Original Bitmap W,H: " + bitmap.getWidth() + "x" + bitmap.getHeight() +
                " | Preprocessed Bitmap W,H: " + result.bitmap.getWidth() + "x" + result.bitmap.getHeight() +
                " | xOffset: " + result.xOffset + " yOffset: " + result.yOffset + " scale: " + result.scale);

        // 3. Keep these updated so other parts of your app can see them
        latestXOffset = result.xOffset; // This will now correctly be 80
        latestYOffset = result.yOffset; // This will be 0
        latestScale = result.scale;     // This will be 1.0

        // 4. Run Inference
        float[][][] output = new float[1][OUTPUT_ROWS][OUTPUT_COLUMNS];
        tfLite.run(result.buffer, output);

        // 5. Return results using the RAW AI-space logic
        return applyNMS(output[0]);
    }

    private List<Detection> applyNMS(float[][] results) {
        List<Detection> allDetections = new ArrayList<>();

        for (float[] row : results) {

            float confidence = row[4];
            if (confidence < confThreshold) continue;

            float maxClassScore = 0f;
            int classId = 0;

            for (int i = 5; i < OUTPUT_COLUMNS; i++) {
                if (row[i] > maxClassScore) {
                    maxClassScore = row[i];
                    classId = i - 5;
                }
            }

            // Inside applyNMS(float[][] results)
            if (maxClassScore > confThreshold) {
                // 1. Get raw YOLO normalized coordinates (0.0 to 1.0)
                float x = row[0];
                float y = row[1];
                float w = row[2];
                float h = row[3];

                // 2. Convert to raw 640-pixel space without ANY offsets or scaling
                float centerX = x * inputSize;
                float centerY = y * inputSize;
                float width   = w * inputSize;
                float height  = h * inputSize;

                // 3. Create a RectF in raw AI-space
                float left   = centerX - width / 2f;
                float top    = centerY - height / 2f;
                float right  = centerX + width / 2f;
                float bottom = centerY + height / 2f;
                Log.d(
                        "YOLO_DETECT",
                        "Label=" + labels.get(classId) +
                                " | conf=" + maxClassScore +
                                " | cx=" + centerX +
                                " | cy=" + centerY +
                                " | w=" + width +
                                " | h=" + height
                );

                allDetections.add(new Detection(labels.get(classId), maxClassScore,
                        new RectF(left, top, right, bottom), 0f, 0f));
            }

        }

        // Sort by confidence descending
        allDetections.sort((a, b) -> Float.compare(b.confidence, a.confidence));

        List<Detection> nmsDetections = new ArrayList<>();
        while (!allDetections.isEmpty()) {
            Detection best = allDetections.remove(0);
            nmsDetections.add(best);

            allDetections.removeIf(
                    next -> calculateIoU(best.boundingBox, next.boundingBox) > iouThreshold
            );
        }

        return nmsDetections;
    }


    private float calculateIoU(RectF a, RectF b) {
        float intersectionArea = Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left)) *
                Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
        float unionArea = (a.width() * a.height()) + (b.width() * b.height()) - intersectionArea;
        return intersectionArea / unionArea;
    }

    public enum Direction {
        LEFT,
        CENTER,
        RIGHT
    }

    // --- UPDATED DETECTION CLASS ---
    public static class Detection {
        public String label;
        public float confidence;
        public RectF boundingBox;
        public float distance; // New field for depth integration
        public Direction direction;
        public final float frameXOff;
        public final float frameYOff;
        public Detection(String label, float confidence, RectF boundingBox, float xOff, float yOff) {
            this.label = label;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
            this.frameXOff = xOff;
            this.frameYOff = yOff;
            this.distance = 0f; // Initialize it so it's ready for your depth math later
        }
        // ADD THE MATH HERE (This is the #1 you are looking for)
        // Inside YoloDetector.java -> static class Detection

        public RectF getScreenRect(float screenW, float screenH) {
            // Actual camera frame dimensions
            float camW = YoloDetector.originalImgW;
            float camH = YoloDetector.originalImgH;

            // Latest scale & offsets from preprocess
            float scale = YoloDetector.latestScale;
            float offsetX = YoloDetector.latestXOffset;
            float offsetY = YoloDetector.latestYOffset;

            // --- DEBUG LOG ---
            Log.d("WallCheck",
                    "RawLeft: " + boundingBox.left +
                            " | RawRight: " + boundingBox.right +
                            " | RawTop: " + boundingBox.top +
                            " | RawBottom: " + boundingBox.bottom +
                            " | offsetX: " + offsetX + " | scale: " + scale
            );

            // 1. Remove letterboxing & scaling
            float x1 = (boundingBox.left - offsetX) / scale;
            float y1 = (boundingBox.top  - offsetY) / scale;
            float x2 = (boundingBox.right - offsetX) / scale;
            float y2 = (boundingBox.bottom - offsetY) / scale;

            // 2. Map to actual screen size
            float screenX1 = x1 * (screenW / camW);
            float screenY1 = y1 * (screenH / camH);
            float screenX2 = x2 * (screenW / camW);
            float screenY2 = y2 * (screenH / camH);

            return new RectF(screenX1, screenY1, screenX2, screenY2);
        }



    }
    public class PreprocessResult {
        public ByteBuffer buffer;
        public Bitmap bitmap; // 1. Added this slot
        public int xOffset;
        public int yOffset;
        public float scale;

        // 2. Updated the constructor to accept the Bitmap as the 2nd item
        public PreprocessResult(ByteBuffer buffer, Bitmap bitmap, int xOffset, int yOffset, float scale) {
            this.buffer = buffer;
            this.bitmap = bitmap;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
            this.scale = scale;
        }
    }

    public PreprocessResult preprocessBitmap(Bitmap bitmap, int targetSize) {
        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();

        // 1. Calculate Scale (to fit longest side)
        float scale = Math.min((float) targetSize / originalWidth, (float) targetSize / originalHeight);
        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);

        // 2. Calculate Offsets (the black bars)
        int xOffset = (targetSize - newWidth) / 2;
        int yOffset = (targetSize - newHeight) / 2;

        // 3. Create the Square Canvas (Letterbox)
        Bitmap squareBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(squareBitmap);
        canvas.drawColor(Color.BLACK); // Add the black bars

        // 4. Draw original bitmap scaled into the center
        Rect src = new Rect(0, 0, originalWidth, originalHeight);
        Rect dst = new Rect(xOffset, yOffset, xOffset + newWidth, yOffset + newHeight);
        canvas.drawBitmap(bitmap, src, dst, null);

        // 5. Convert to ByteBuffer (assuming Float32 and 0-255 normalization)
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * targetSize * targetSize * 3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[targetSize * targetSize];
        squareBitmap.getPixels(intValues, 0, squareBitmap.getWidth(), 0, 0, squareBitmap.getWidth(), squareBitmap.getHeight());

        for (int pixelValue : intValues) {
            byteBuffer.putFloat(((pixelValue >> 16) & 0xFF) / 255.0f);
            byteBuffer.putFloat(((pixelValue >> 8) & 0xFF) / 255.0f);
            byteBuffer.putFloat((pixelValue & 0xFF) / 255.0f);
        }

        // This will now match the "Required type" shown in your error screenshot
        return new PreprocessResult(byteBuffer, squareBitmap, xOffset, yOffset, scale);
    }
}