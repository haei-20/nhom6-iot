package com.xe.vaxrobot.View;

import static com.xe.vaxrobot.Model.MapModel.mapShapeSize;
import static com.xe.vaxrobot.Model.MapModel.numberGridBox;
import static com.xe.vaxrobot.Model.MapModel.squareSize;
import static com.xe.vaxrobot.Model.MapModel.squareSizeCm;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;

import com.xe.vaxrobot.Model.MapModel;
import com.xe.vaxrobot.Model.MarkerModel;
import com.xe.vaxrobot.Model.RobotModel;
import com.xe.vaxrobot.Model.SonicValue;
import com.xe.vaxrobot.R;

import java.util.ArrayList;
import java.util.List;

public class MapView extends View {

    private MapModel mapModel;
    private Bitmap robotBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.car);

    private Paint gridPaint, pathPaint, obstaclePaint, spacePaint, borderPaint;

    // --- PAINT CHO MARKER ---
    private Paint markerPaint, textPaint;
    private List<MarkerModel> markers = new ArrayList<>();

    private float scaleFactor = 0.5f;
    private float maxScale = 0.2f;
    private float minScale = 4f;
    private float translateX = 0f, translateY = 0f;
    private float lastTouchX, lastTouchY;
    private int activePointerId = -1;
    private ScaleGestureDetector scaleDetector;

    private float[] firstPoint = null;
    private float[] lastPoint = null;
    private boolean isCalculatingMode = false;

    public MapView(Context context) { super(context); init(context); }
    public MapView(Context context, AttributeSet attrs) { super(context, attrs); init(context); }
    public MapView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(context); }

    private void init(Context context) {
        mapModel = new MapModel();
        pathPaint = new Paint(); pathPaint.setColor(getResources().getColor(R.color.tealColor)); pathPaint.setStrokeWidth(5f);
        gridPaint = new Paint(); gridPaint.setColor(Color.LTGRAY); gridPaint.setStrokeWidth(1f); gridPaint.setAntiAlias(true);
        borderPaint = new Paint(); borderPaint.setColor(Color.BLACK); borderPaint.setStrokeWidth(10f);
        obstaclePaint = new Paint(); obstaclePaint.setColor(getResources().getColor(R.color.darkTealColor)); obstaclePaint.setStrokeWidth(5f);
        spacePaint = new Paint(); spacePaint.setColor(getResources().getColor(R.color.greenColor)); spacePaint.setStrokeWidth(5f);

        // Setup Marker Paint
        markerPaint = new Paint();
        markerPaint.setColor(Color.RED);
        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(40f);
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    public void setMarkers(List<MarkerModel> newMarkers) {
        this.markers = newMarkers;
        invalidate();
    }

    public void updateRobotModel(RobotModel r){
        mapModel.updateRobotModel(r);
        invalidate();
    }

    public void resetMap(){
        mapModel.resetMap();
        markers.clear(); // Xóa hết marker khi reset map
        invalidate();
    }

    @SuppressLint("ResourceAsColor")
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if(mapModel == null) return;

        float[][] map = mapModel.getMap();
        canvas.save();
        canvas.translate(translateX, translateY);
        canvas.scale(scaleFactor, scaleFactor);

        // Vẽ Map nền
        for(int i = 0; i<map.length; i++){
            for(int j = 0; j<map[i].length; j++){
                if(map[i][j] == 1) canvas.drawRect(i*squareSize, j*squareSize, i*squareSize + squareSize, j*squareSize + squareSize, pathPaint);
                // ... (Giữ các logic vẽ ô khác nếu cần)
            }
        }
        drawGrid(canvas);

        // --- VẼ MARKER ---
        // Vẽ điểm Bếp (Gốc)
        float startX = (0 * squareSize) + (squareSize / 2f);
        float startY = (0 * squareSize) + (squareSize / 2f);
        markerPaint.setColor(Color.BLUE);
        canvas.drawCircle(startX, startY, squareSize / 3f, markerPaint);
        canvas.drawText("Bếp", startX, startY - squareSize/2f, textPaint);

        // Vẽ các điểm đã lưu
        markerPaint.setColor(Color.RED);
        for (MarkerModel m : markers) {
            float drawX = (m.x * squareSize) + (squareSize / 2f);
            float drawY = (m.y * squareSize) + (squareSize / 2f);
            canvas.drawCircle(drawX, drawY, squareSize / 3f, markerPaint);
            canvas.drawText(m.name, drawX, drawY - squareSize/2f, textPaint);
        }

        drawRobot(canvas);
        canvas.restore();
    }

    private void drawRobot(Canvas canvas){
        RobotModel robotModel = mapModel.getRobotModel();
        if (robotModel != null && robotBitmap != null) {
            float centerX = robotModel.getXAxis() + (squareSize / 2f);
            float centerY = robotModel.getYAxis() + (squareSize / 2f);

            // Scale bitmap robot cho vừa ô
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(robotBitmap, (int)squareSize, (int)squareSize, false);

            Matrix matrix = new Matrix();
            matrix.postTranslate(-scaledBitmap.getWidth() / 2f, -scaledBitmap.getHeight() / 2f);
            matrix.postRotate(robotModel.getAngle());
            matrix.postTranslate(centerX, centerY);

            canvas.drawBitmap(scaledBitmap, matrix, null);
        }
    }

    private void drawGrid(Canvas canvas){
        int gridSize = (int) (mapShapeSize / numberGridBox);
        for (float x = 0; x <= mapShapeSize; x += gridSize) {
            canvas.drawLine(x, 0, x, mapShapeSize, gridPaint);
        }
        for (float y = 0; y <= mapShapeSize; y += gridSize) {
            canvas.drawLine(0, y, mapShapeSize, y, gridPaint);
        }
    }

    // ... (Phần TouchEvent và ScaleListener giữ nguyên)
    @Override
    public boolean onTouchEvent(MotionEvent event){
        scaleDetector.onTouchEvent(event);
        final int action = event.getAction();
        switch (action){
            case MotionEvent.ACTION_DOWN: {
                lastTouchX = event.getX(); lastTouchY = event.getY();
                activePointerId = event.getPointerId(0);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!scaleDetector.isInProgress()) {
                    final int pointerIndex = event.findPointerIndex(activePointerId);
                    final float x = event.getX(pointerIndex);
                    final float y = event.getY(pointerIndex);
                    final float dx = x - lastTouchX;
                    final float dy = y - lastTouchY;
                    translateX += dx; translateY += dy;
                    invalidate();
                    lastTouchX = x; lastTouchY = y;
                }
                break;
            }
            // ... Các case khác giữ nguyên
        }
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener{
        @Override
        public boolean onScale(@NonNull ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(maxScale, Math.min(scaleFactor, minScale));
            invalidate();
            return true;
        }
    }

    public void centerOnRobotPosition() {
        RobotModel robotModel = mapModel.getRobotModel();
        float viewWidth = getWidth(); float viewHeight = getHeight();
        translateX = (viewWidth / 2f) - (robotModel.getXAxis() * scaleFactor);
        translateY = (viewHeight / 2f) - (robotModel.getYAxis() * scaleFactor);
        invalidate();
    }

    public void setCalculatingMode(boolean calculatingMode) { isCalculatingMode = calculatingMode; invalidate(); }
    public boolean isCalculatingMode() { return isCalculatingMode; }
}