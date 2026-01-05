package com.google.ar.core.examples.java.helloar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class YuvToRgbConverter {

    public YuvToRgbConverter(Context context) {
        // RenderScript is no longer needed for this stable method
    }

    public void yuvToRgb(Image image, Bitmap output) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = new int[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int yIdx = y * planes[0].getRowStride() + x;
                int uvIdx = (y / 2) * planes[1].getRowStride() + (x / 2) * planes[1].getPixelStride();

                int Y = (yBuffer.get(yIdx) & 0xFF);
                int U = (uBuffer.get(uvIdx) & 0xFF) - 128;
                int V = (vBuffer.get(uvIdx) & 0xFF) - 128;

                int r = (int) (Y + 1.370705f * V);
                int g = (int) (Y - 0.337633f * U - 0.698001f * V);
                int b = (int) (Y + 1.732446f * U);

                // Clamp values to 0-255
                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                pixels[y * width + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private byte[] yuv420ToNv21(Image image) {
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        return nv21;
    }
}