package com.google.ar.core.examples.java.helloar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {
    private List<YoloDetector.Detection> detections = new ArrayList<>();
    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();

    // 1. ADD THIS: New paint for the validation dot
    private final Paint dotPaint = new Paint();

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        boxPaint.setColor(Color.parseColor("#00FF00"));
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8.0f);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(42.0f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setShadowLayer(5.0f, 2, 2, Color.BLACK);

        // 2. ADD THIS: Configure the red dot style
        dotPaint.setColor(Color.RED);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setAntiAlias(true);
    }

    public void setResults(List<YoloDetector.Detection> results) {
        this.detections = results;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (detections == null || detections.isEmpty()) return;

        float screenW = getWidth();
        float screenH = getHeight();

        // Assume original image dimensions are same as latest detected frame
        float imgW = YoloDetector.originalImgW;
        float imgH = YoloDetector.originalImgH;

        float scaleX = screenW / imgW;
        float scaleY = screenH / imgH;

        for (YoloDetector.Detection det : detections) {
            RectF screenRect = det.getScreenRect(screenW, screenH);

            // Draw bounding box
            canvas.drawRect(screenRect, boxPaint);

            // Draw center dot
            float cx = screenRect.centerX();
            float cy = screenRect.centerY();
            canvas.drawCircle(cx, cy, 15f, dotPaint);

            // Draw label + distance
            String labelText = det.label + (det.distance > 0 ? String.format(" %.2fm", det.distance) : "");
            canvas.drawText(labelText, screenRect.left, screenRect.top - 15, textPaint);

        }

    }

}