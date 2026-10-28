package com.sametkapan.akillitarimnlp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

public class IvyProgressView extends View {

    private Paint vinePaint;
    private Paint leafPaint;
    
    private Path vinePath;
    
    private float targetProgress = 0f;
    private float currentProgress = 0f;
    private ValueAnimator animator;

    public IvyProgressView(Context context) {
        super(context);
        init();
    }

    public IvyProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public IvyProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        vinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        vinePaint.setColor(ContextCompat.getColor(getContext(), R.color.nature_green_dark));
        vinePaint.setStyle(Paint.Style.STROKE);
        vinePaint.setStrokeWidth(6f);
        vinePaint.setStrokeCap(Paint.Cap.ROUND);

        leafPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        leafPaint.setColor(ContextCompat.getColor(getContext(), R.color.nature_green_light));
        leafPaint.setStyle(Paint.Style.FILL);
        
        vinePath = new Path();
    }

    public void setProgress(float progress) {
        this.targetProgress = Math.max(0, Math.min(100, progress));
        
        if (animator != null) {
            animator.cancel();
        }
        
        animator = ValueAnimator.ofFloat(currentProgress, targetProgress);
        animator.setDuration(1200); // 1.2 saniye uzama animasyonu
        animator.addUpdateListener(animation -> {
            currentProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        int width = getWidth();
        int height = getHeight();
        
        if (width == 0 || height == 0) return;
        
        float centerY = height / 2f;
        
        // İlerleme miktarına göre çizim alanını kırp
        canvas.save();
        float clipRight = width * (currentProgress / 100f);
        canvas.clipRect(0, 0, clipRight, height);
        
        // Sarmaşık Gövdesi (Sine Wave)
        vinePath.reset();
        vinePath.moveTo(0, centerY);
        
        float amplitude = height / 5f;
        float frequency = width / 5f; // Ekranda 5 dalga döngüsü
        
        for (float x = 0; x <= width; x += 5f) {
            float y = centerY + amplitude * (float) Math.sin(x / frequency * 2 * Math.PI);
            vinePath.lineTo(x, y);
        }
        canvas.drawPath(vinePath, vinePaint);
        
        // Yapraklar
        int leafCount = 0;
        for (float x = 15; x <= width - 10; x += frequency / 1.8f) {
            float y = centerY + amplitude * (float) Math.sin(x / frequency * 2 * Math.PI);
            
            // Çizginin eğimi (türev)
            float derivative = amplitude * (float) Math.cos(x / frequency * 2 * Math.PI) * (2 * (float) Math.PI / frequency);
            float angle = (float) Math.atan(derivative);
            
            // Bir sağa bir sola yaprak
            boolean isUp = (leafCount % 2 == 0);
            float leafAngle = angle + (isUp ? -0.6f : 0.6f);
            
            drawLeaf(canvas, x, y, leafAngle, height / 2.5f);
            leafCount++;
        }
        
        canvas.restore();
    }
    
    private void drawLeaf(Canvas canvas, float cx, float cy, float angle, float size) {
        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate((float) Math.toDegrees(angle));
        
        Path leafPath = new Path();
        leafPath.moveTo(0, 0);
        // Basit bir yaprak şekli
        leafPath.quadTo(size / 2, -size / 2, size, 0);
        leafPath.quadTo(size / 2, size / 2, 0, 0);
        leafPath.close();
        
        canvas.drawPath(leafPath, leafPaint);
        
        // Yaprağın ortasına damar çizgisi (isteğe bağlı güzelleştirme)
        Paint veinPaint = new Paint(vinePaint);
        veinPaint.setStrokeWidth(2f);
        veinPaint.setColor(ContextCompat.getColor(getContext(), R.color.nature_green_dark));
        canvas.drawLine(0, 0, size * 0.8f, 0, veinPaint);
        
        canvas.restore();
    }
}
