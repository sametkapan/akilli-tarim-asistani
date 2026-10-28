package com.sametkapan.akillitarimnlp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

public class LiquidWaveView extends View {
    private Paint wavePaintBack;
    private Paint wavePaintFront;
    private Path wavePathBack;
    private Path wavePathFront;
    
    private float progress = 0.0f; // 0.0 to 1.0
    private float tiltAngle = 0f; // degrees
    
    private float waveOffset = 0f;
    private float waveHeight = 20f;
    private float waveLength = 500f;
    
    public LiquidWaveView(Context context) {
        super(context);
        init();
    }

    public LiquidWaveView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LiquidWaveView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        wavePaintBack = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaintBack.setColor(ContextCompat.getColor(getContext(), R.color.water_blue_dark));
        wavePaintBack.setAlpha(150); // Semi-transparent
        wavePaintBack.setStyle(Paint.Style.FILL);
        
        wavePaintFront = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaintFront.setColor(ContextCompat.getColor(getContext(), R.color.water_blue));
        wavePaintFront.setAlpha(200); // Less transparent
        wavePaintFront.setStyle(Paint.Style.FILL);
        
        wavePathBack = new Path();
        wavePathFront = new Path();
    }
    
    public void setProgress(float progress) {
        // progress can be 0 to 100 or 0 to 1. Assume 0 to 100 for humidity.
        this.progress = Math.max(0, Math.min(100, progress)) / 100f;
        invalidate();
    }
    
    public void setTiltAngle(float angle) {
        this.tiltAngle = angle;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (progress <= 0.0f) {
            return;
        }
        
        int width = getWidth();
        int height = getHeight();
        
        waveOffset += 4f; // Speed of the wave
        if (waveOffset > waveLength * 2) {
            waveOffset -= waveLength * 2;
        }
        
        float waterHeightY = height * (1f - progress);
        
        canvas.save();
        // Pivot around the center of the water surface
        canvas.rotate(tiltAngle, width / 2f, waterHeightY);
        
        // Draw area needs to be much wider because of rotation
        int extendedSize = Math.max(width, height) * 2;
        int startX = (width / 2) - extendedSize;
        int endX = (width / 2) + extendedSize;
        
        int deepWaterY = height + extendedSize; // Draw deep enough to cover corners when rotated
        
        // Back Wave
        wavePathBack.reset();
        wavePathBack.moveTo(startX, deepWaterY);
        wavePathBack.lineTo(startX, waterHeightY);
        for (float x = startX; x <= endX; x += 15f) {
            float y = (float) (waterHeightY + waveHeight * Math.sin((x + waveOffset * 1.5f) * 2 * Math.PI / waveLength));
            wavePathBack.lineTo(x, y);
        }
        wavePathBack.lineTo(endX, deepWaterY);
        wavePathBack.close();
        canvas.drawPath(wavePathBack, wavePaintBack);
        
        // Front Wave
        wavePathFront.reset();
        wavePathFront.moveTo(startX, deepWaterY);
        wavePathFront.lineTo(startX, waterHeightY);
        for (float x = startX; x <= endX; x += 15f) {
            float y = (float) (waterHeightY + waveHeight * 1.2f * Math.sin((x + waveOffset + (waveLength / 3)) * 2 * Math.PI / waveLength));
            wavePathFront.lineTo(x, y);
        }
        wavePathFront.lineTo(endX, deepWaterY);
        wavePathFront.close();
        canvas.drawPath(wavePathFront, wavePaintFront);
        
        canvas.restore();
        
        postInvalidateDelayed(16); // ~60 FPS
    }
}
