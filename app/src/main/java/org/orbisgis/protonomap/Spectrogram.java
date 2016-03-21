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
    private static final float min = 0;
    private static final float max = 20;

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
            canvas.drawColor(colorRamp[0]);
        }
    }

    /**
     * Add the spectrum as a new spectrogramImage column
     * @param spectrum FFT response
     */
    public void addTimeStep(float[] spectrum) {
        final int ticWidth = 5; // Timestep width in pixels
        spectrumData.add(0, spectrum);
        if(canvasWidth > 0 && canvasHeight > 0) {
            if (spectrogramBuffer == null ||spectrogramBuffer.getWidth() != canvasWidth ||
                    spectrogramBuffer.getHeight() != canvasHeight ) {
                spectrogramBuffer = Bitmap.createBitmap(canvasWidth, canvasHeight,
                        Bitmap.Config.ARGB_8888);
                spectrogramBuffer.eraseColor(colorRamp[0]);
            } else {
                // Move spectrum on the left
                Canvas canvas = new Canvas(spectrogramBuffer);
                canvas.drawBitmap(spectrogramBuffer, -(spectrumData.size() * ticWidth), 0, null);
            }
            Canvas canvas = new Canvas(spectrogramBuffer);
            int drawnTics = 0;
            while ( !spectrumData.isEmpty()) {
                float[] ticSpectrum = spectrumData.remove(0);
                // Rescale to the range of color ramp
                Bitmap ticBuffer = Bitmap.createBitmap(ticWidth, canvasHeight,
                        Bitmap.Config.ARGB_8888);
                int[] ticColors = new int[canvasHeight * ticWidth];

                // merge frequencies following the destination resolution
                double freqByPixel = ticSpectrum.length / (double)canvasHeight;
                for(int pixel = 0; pixel < canvasHeight; pixel++) {
                    // Compute frequency range covered by this pixel
                    int freqStart = (int)Math.floor(pixel * freqByPixel);
                    int freqEnd =(int)Math.min(freqStart + freqByPixel, ticSpectrum.length);
                    float sumVal = 0;
                    for (int idfreq = freqStart; idfreq < freqEnd; idfreq++) {
                        // Rescale value and pick the color in the color ramp
                        sumVal += ticSpectrum[idfreq];
                    }
                    sumVal /= (freqEnd - freqStart);
                    int pixColor = colorRamp[Math.min(colorRamp.length - 1, Math.max(0,
                            (int) (((sumVal - min) / (max - min)) * colorRamp.length)))];
                    for(int y = 0; y < ticWidth; y++) {
                        ticColors[((canvasHeight - 1) - pixel) * ticWidth + y] = pixColor;
                    }
                }
                ticBuffer.setPixels(ticColors, 0, ticWidth, 0, 0, ticWidth , canvasHeight);
                int leftPos = spectrogramBuffer.getWidth() - ((drawnTics + 1) * ticWidth);
                canvas.drawBitmap(ticBuffer, leftPos, 0, null);
                drawnTics++;
            }
            postInvalidate(); // redraws the view calling onDraw()
        }
    }
}
