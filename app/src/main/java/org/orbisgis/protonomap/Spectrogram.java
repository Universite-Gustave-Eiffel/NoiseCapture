package org.orbisgis.protonomap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * This canvas drawer receive thin or third octave frequency SPL and draw it.
 */
public class Spectrogram extends View {
    private static final int CAPACITY_HINT = 500;
    private final List<float[]> spectrumData = new ArrayList<>(CAPACITY_HINT);
    private Bitmap spectrogramBuffer = null;
    private int canvasHeight = -1;
    private int canvasWidth = -1;
    private float min = Float.MAX_VALUE;
    private float max = Float.MIN_VALUE;

    private static final int[] colorRamp = new int[]{p("#000000"), p("#170f79"), p("#301084"),
            p("#460f75"), p("#5c0f67"), p("#720f59"), p("#8a0e49"), p("#ad0d32"), p("#ee2209"),
            p("#d10c1b"), p("#f85e04"), p("#ff8800"), p("#f3d328")};

    public Spectrogram(Context context) {
        super(context);
    }

    public Spectrogram(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Spectrogram(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private static int p(String color) {
        return Color.parseColor(color);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvasWidth = canvas.getWidth();
        canvasHeight = canvas.getHeight();
        if(spectrogramBuffer != null) {
            canvas.drawBitmap(spectrogramBuffer, 0, 0, null);
        } else {
            canvas.drawARGB(255, 255, 0, 0);
        }
    }

    /**
     * Add the spectrum as a new spectrogramImage column
     * @param spectrum FFT response
     */
    public void addTimeStep(float[] spectrum) {
        // Copy data into a new array
        for(float val : spectrum) {
            min = Math.min(min, val);
            max = Math.max(max, val);
        }
        System.out.println("Min " + min + " Max " + max);
        spectrumData.add(0, spectrum);
        if(canvasWidth > 0 && canvasHeight > 0) {
            if (spectrogramBuffer == null ||spectrogramBuffer.getWidth() != canvasWidth ||
                    spectrogramBuffer.getHeight() != canvasHeight ) {
                spectrogramBuffer = Bitmap.createBitmap(canvasWidth, canvasHeight,
                        Bitmap.Config.ARGB_8888);
            }
            Canvas canvas = new Canvas(spectrogramBuffer);
            final int ticWidth = 4; // Timestep width in pixels
            // TODO do not redraw tics (move old tics on the left)
            int drawnTics = 0;
            for (float[] ticSpectrum : spectrumData) {
                // Rescale to the range of color ramp
                Bitmap ticBuffer = Bitmap.createBitmap(1, ticSpectrum.length,
                        Bitmap.Config.ARGB_8888);
                int[] ticColors = new int[ticSpectrum.length];
                for(int idfreq = 0; idfreq < ticSpectrum.length; idfreq++) {
                    // Rescale value and pick the color in the color ramp
                    ticColors[idfreq] = colorRamp[Math.min(colorRamp.length - 1, Math.max(0,
                            (int) (((ticSpectrum[idfreq] - min) / (max - min)) * colorRamp.length)))];
                }
                ticBuffer.setPixels(ticColors, 0, 1, 0, 0, 1, ticColors.length);
                int leftPos = spectrogramBuffer.getWidth() - 1 - (drawnTics * ticWidth);
                int rightPos = leftPos + ticWidth;
                Rect destRect = new Rect(leftPos, 0, rightPos, spectrogramBuffer.getHeight());
                canvas.drawBitmap(ticBuffer, null,destRect, null);
                drawnTics++;
            }
            invalidate(); // redraws the view calling onDraw()
        }
    }
}
