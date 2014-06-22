package net.exent.flywithme.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class GestureImageView extends View {
    private static final float EDGE_CONNECT_PIXELS = 0.05f; // resize image to width/height when scaled to within this percent from width/height
    private Bitmap bitmap;
    private ScaleGestureDetector gestureDetector;
    private float posX;
    private float posY;
    private float downPosX;
    private float downPosY;
    private float scaleFactor;
    private boolean allowPan; // prevents image from jumping around when zooming

    public GestureImageView(Context context) {
        this(context, null, 0);
    }

    public GestureImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        gestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            public boolean onScale(ScaleGestureDetector detector) {
                float scale = detector.getScaleFactor();
                scaleFactor *= scale;
                allowPan = false;

                // we need to move posX and posY to center our zooming
                float diffX = detector.getFocusX() - posX;
                float diffY = detector.getFocusY() - posY;
                diffX = diffX * scale - diffX;
                diffY = diffY * scale - diffY;
                posX -= diffX;
                posY -= diffY;

                invalidate();
                return true;
            }
        });
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        invalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (bitmap == null)
            return;
        // set max/min scale factor
        float heightScaleFactor = (float) canvas.getHeight() / (float) bitmap.getHeight();
        float widthScaleFactor = (float) canvas.getWidth() / (float) bitmap.getWidth();
        scaleFactor = Math.max(heightScaleFactor, Math.min(scaleFactor, (float) Math.E));
        // set current scale factor, connecting to edge if close enough
        float tmpScaleFactor = scaleFactor;
        if (Math.abs(tmpScaleFactor * bitmap.getHeight() - canvas.getHeight()) < EDGE_CONNECT_PIXELS * canvas.getHeight())
            tmpScaleFactor = heightScaleFactor;
        if (Math.abs(tmpScaleFactor * bitmap.getWidth() - canvas.getWidth()) < EDGE_CONNECT_PIXELS * canvas.getWidth())
            tmpScaleFactor = widthScaleFactor;
        // set min/max posX & posY
        float bitmapHeight = bitmap.getHeight() * tmpScaleFactor;
        if (bitmapHeight < canvas.getHeight())
            posY = (canvas.getHeight() - bitmapHeight) / 2f;
        else
            posY = Math.min(0f, Math.max(posY, canvas.getHeight() - bitmapHeight));
        float bitmapWidth = bitmap.getWidth() * tmpScaleFactor;
        if (bitmapWidth < canvas.getWidth())
            posX = (canvas.getWidth() - bitmapWidth) / 2f;
        else
            posX = Math.min(0f, Math.max(posX, canvas.getWidth() - bitmapWidth));
        // draw
        canvas.save();
        canvas.translate(posX, posY);
        canvas.scale(tmpScaleFactor, tmpScaleFactor);
        canvas.drawBitmap(bitmap, 0, 0, null);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);

        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            downPosX = event.getX();
            downPosY = event.getY();
            allowPan = true;
            break;

        case MotionEvent.ACTION_MOVE:
            if (allowPan) {
                posX += event.getX() - downPosX;
                posY += event.getY() - downPosY;
                downPosX = event.getX();
                downPosY = event.getY();
                invalidate();
            }
            break;

        default:
            break;
        }
        return true;
    }
}
