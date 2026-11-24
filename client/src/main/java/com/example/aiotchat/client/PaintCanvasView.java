
package com.example.aiotchat.client;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PaintCanvasView extends View {

    // 每筆線條的資料
    public static class Stroke {

        public final String id;
        public final String owner;
        public final int color;

        public final List<PointF> points = new ArrayList<>();

        public Stroke(String id, String owner, int color) {
            this.id = id;
            this.owner = owner;
            this.color = color;
        }
    }

    public interface OnStrokeFinishedListener {
        void onStrokeFinished(Stroke stroke);
    }

    public interface OnEraseStrokeListener {
        void onEraseRequested(String strokeId);
    }

    private final List<Stroke> strokes = new ArrayList<>();

    private final Paint paint =
            new Paint(Paint.ANTI_ALIAS_FLAG);

    private Stroke currentStroke;

    private OnStrokeFinishedListener strokeListener;
    private OnEraseStrokeListener eraseListener;

    private String localOwner = "Client";

    private int penColor = Color.rgb(115, 90, 200);

    private float penWidth;

    private boolean eraserMode = false;

    // 記錄橡皮擦上一個位置
    private float lastEraseX;
    private float lastEraseY;

    // 橡皮擦觸碰線條的有效半徑
    private float eraseRadius;

    // 拖曳時補點的間距
    private float eraseStep;

    public PaintCanvasView(Context context) {
        super(context);
        init();
    }

    public PaintCanvasView(
            Context context,
            AttributeSet attrs
    ) {
        super(context, attrs);
        init();
    }

    private void init() {

        float density = getResources()
                .getDisplayMetrics().density;

        penWidth = 4f * density;

        eraseRadius = 18f * density;

        // 每 8dp 檢查一次，避免快速移動漏擦
        eraseStep = 8f * density;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(penWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        setBackgroundColor(Color.WHITE);
    }

    public void setLocalOwner(String name) {
        localOwner = name;
    }

    public void setPenColor(int color) {
        penColor = color;
        invalidate();
    }

    public void setEraserMode(boolean enabled) {

        if (currentStroke != null) {
            strokes.remove(currentStroke);
            currentStroke = null;
            invalidate();
        }

        eraserMode = enabled;
    }

    public boolean isEraserMode() {
        return eraserMode;
    }

    public void setOnStrokeFinishedListener(
            OnStrokeFinishedListener listener
    ) {
        strokeListener = listener;
    }

    public void setOnEraseStrokeListener(
            OnEraseStrokeListener listener
    ) {
        eraseListener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (Stroke stroke : strokes) {
            drawStroke(canvas, stroke);
        }
    }

    private void drawStroke(Canvas canvas, Stroke stroke) {

        if (stroke.points.isEmpty()) {
            return;
        }

        paint.setColor(stroke.color);

        PointF first = stroke.points.get(0);

        float firstX = first.x * getWidth();
        float firstY = first.y * getHeight();

        if (stroke.points.size() == 1) {

            paint.setStyle(Paint.Style.FILL);

            canvas.drawCircle(
                    firstX,
                    firstY,
                    penWidth / 2f,
                    paint
            );

            paint.setStyle(Paint.Style.STROKE);
            return;
        }

        Path path = new Path();
        path.moveTo(firstX, firstY);

        for (int i = 1; i < stroke.points.size(); i++) {

            PointF point = stroke.points.get(i);

            path.lineTo(
                    point.x * getWidth(),
                    point.y * getHeight()
            );
        }

        canvas.drawPath(path, paint);
    }

    // 轉換成 0～1 的相對座標
    private PointF normalizedPoint(float x, float y) {

        float nx = Math.max(
                0f,
                Math.min(1f, x / getWidth())
        );

        float ny = Math.max(
                0f,
                Math.min(1f, y / getHeight())
        );

        return new PointF(nx, ny);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if (!isEnabled() ||
                getWidth() <= 0 ||
                getHeight() <= 0) {
            return false;
        }

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:

                if (getParent() != null) {
                    getParent()
                            .requestDisallowInterceptTouchEvent(true);
                }

                if (eraserMode) {

                    // 記錄滑鼠按下的位置
                    lastEraseX = x;
                    lastEraseY = y;

                    // 按下瞬間就可以擦除
                    eraseBetween(x, y, x, y);

                    return true;
                }

                // 畫筆模式：開始新的筆跡
                currentStroke = new Stroke(
                        UUID.randomUUID().toString(),
                        localOwner,
                        penColor
                );

                currentStroke.points.add(
                        normalizedPoint(x, y)
                );

                strokes.add(currentStroke);
                invalidate();

                return true;

            case MotionEvent.ACTION_MOVE:

                if (eraserMode) {

                    // 檢查 Android 保存的中間觸控位置
                    for (int i = 0;
                         i < event.getHistorySize();
                         i++) {

                        float hx = event.getHistoricalX(i);
                        float hy = event.getHistoricalY(i);

                        eraseBetween(
                                lastEraseX,
                                lastEraseY,
                                hx,
                                hy
                        );

                        lastEraseX = hx;
                        lastEraseY = hy;
                    }

                    // 從上一位置擦到目前位置
                    eraseBetween(
                            lastEraseX,
                            lastEraseY,
                            x,
                            y
                    );

                    lastEraseX = x;
                    lastEraseY = y;

                    return true;
                }

                // 畫筆模式：持續增加座標
                if (currentStroke != null) {

                    for (int i = 0;
                         i < event.getHistorySize();
                         i++) {

                        currentStroke.points.add(
                                normalizedPoint(
                                        event.getHistoricalX(i),
                                        event.getHistoricalY(i)
                                )
                        );
                    }

                    currentStroke.points.add(
                            normalizedPoint(x, y)
                    );

                    invalidate();
                }

                return true;

            case MotionEvent.ACTION_UP:

                if (eraserMode) {

                    // 擦除最後一小段移動路徑
                    eraseBetween(
                            lastEraseX,
                            lastEraseY,
                            x,
                            y
                    );

                    performClick();
                    return true;
                }

                // 畫筆模式：完成這一筆
                if (currentStroke != null) {

                    currentStroke.points.add(
                            normalizedPoint(x, y)
                    );

                    Stroke finished = currentStroke;
                    currentStroke = null;

                    invalidate();

                    if (strokeListener != null) {
                        strokeListener.onStrokeFinished(finished);
                    }
                }

                performClick();
                return true;

            case MotionEvent.ACTION_CANCEL:

                if (currentStroke != null) {

                    strokes.remove(currentStroke);
                    currentStroke = null;

                    invalidate();
                }

                return true;
        }

        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    // ==========================================
    // 橡皮擦：沿著滑鼠拖曳路徑連續擦除
    // ==========================================

    private void eraseBetween(
            float startX,
            float startY,
            float endX,
            float endY
    ) {

        float dx = endX - startX;
        float dy = endY - startY;

        float distance = (float) Math.hypot(dx, dy);

        // 補足中間檢查點，避免高速移動漏擦
        int steps = Math.max(
                1,
                (int) Math.ceil(distance / eraseStep)
        );

        for (int i = 0; i <= steps; i++) {

            float t = (float) i / steps;

            float x = startX + dx * t;
            float y = startY + dy * t;

            // 同一位置可能重疊多筆自己的線條
            // 全部擦除，而不是只擦最上層一筆
            while (true) {

                String strokeId = findOwnStrokeAt(x, y);

                if (strokeId == null) {
                    break;
                }

                // 本機立即刪除，避免等待網路造成延遲
                removeStroke(strokeId);

                // 通知 Activity 傳送刪除指令
                if (eraseListener != null) {
                    eraseListener.onEraseRequested(strokeId);
                }
            }
        }
    }

    // 找出橡皮擦目前碰到的自己的筆跡
    private String findOwnStrokeAt(float x, float y) {

        float radiusSquared =
                eraseRadius * eraseRadius;

        // 從最後畫的線條往前檢查
        for (int i = strokes.size() - 1; i >= 0; i--) {

            Stroke stroke = strokes.get(i);

            // 只允許擦除自己的線條
            if (!stroke.owner.equals(localOwner)) {
                continue;
            }

            if (isNearStroke(
                    stroke,
                    x,
                    y,
                    radiusSquared
            )) {
                return stroke.id;
            }
        }

        return null;
    }

    // 判斷橡皮擦是否碰到整筆線條的任何一段
    private boolean isNearStroke(
            Stroke stroke,
            float x,
            float y,
            float radiusSquared
    ) {

        List<PointF> points = stroke.points;

        if (points.isEmpty()) {
            return false;
        }

        PointF first = points.get(0);

        float previousX = first.x * getWidth();
        float previousY = first.y * getHeight();

        // 單一圓點也能擦除
        if (points.size() == 1) {

            return distanceSquared(
                    x,
                    y,
                    previousX,
                    previousY
            ) <= radiusSquared;
        }

        // 檢查每一段線條
        for (int i = 1; i < points.size(); i++) {

            PointF point = points.get(i);

            float nextX = point.x * getWidth();
            float nextY = point.y * getHeight();

            float distance =
                    distanceToSegmentSquared(
                            x,
                            y,
                            previousX,
                            previousY,
                            nextX,
                            nextY
                    );

            if (distance <= radiusSquared) {
                return true;
            }

            previousX = nextX;
            previousY = nextY;
        }

        return false;
    }

    private float distanceSquared(
            float x1,
            float y1,
            float x2,
            float y2
    ) {

        float dx = x1 - x2;
        float dy = y1 - y2;

        return dx * dx + dy * dy;
    }

    // 計算橡皮擦中心到線段的最短距離平方
    private float distanceToSegmentSquared(
            float px,
            float py,
            float ax,
            float ay,
            float bx,
            float by
    ) {

        float dx = bx - ax;
        float dy = by - ay;

        float lengthSquared = dx * dx + dy * dy;

        if (lengthSquared == 0f) {
            return distanceSquared(px, py, ax, ay);
        }

        float t = (
                (px - ax) * dx +
                        (py - ay) * dy
        ) / lengthSquared;

        t = Math.max(0f, Math.min(1f, t));

        float nearestX = ax + t * dx;
        float nearestY = ay + t * dy;

        return distanceSquared(
                px,
                py,
                nearestX,
                nearestY
        );
    }

    // ==========================================
    // Socket 筆跡同步
    // ==========================================

    public String encodePoints(Stroke stroke) {

        StringBuilder builder = new StringBuilder();

        for (PointF point : stroke.points) {

            if (builder.length() > 0) {
                builder.append(";");
            }

            builder.append(point.x)
                    .append(",")
                    .append(point.y);
        }

        return builder.toString();
    }

    public boolean hasStroke(String id) {

        for (Stroke stroke : strokes) {

            if (stroke.id.equals(id)) {
                return true;
            }
        }

        return false;
    }

    public void addRemoteStroke(
            String owner,
            String id,
            int color,
            String encodedPoints
    ) {

        if (hasStroke(id) ||
                encodedPoints == null ||
                encodedPoints.isEmpty() ||
                encodedPoints.length() > 100000) {
            return;
        }

        Stroke stroke = new Stroke(id, owner, color);

        String[] pairs = encodedPoints.split(";");

        if (pairs.length == 0 || pairs.length > 5000) {
            return;
        }

        try {

            for (String pair : pairs) {

                String[] xy = pair.split(",");

                if (xy.length != 2) {
                    return;
                }

                float x = Float.parseFloat(xy[0]);
                float y = Float.parseFloat(xy[1]);

                if (!Float.isFinite(x) ||
                        !Float.isFinite(y) ||
                        x < 0f || x > 1f ||
                        y < 0f || y > 1f) {
                    return;
                }

                stroke.points.add(new PointF(x, y));
            }

        } catch (NumberFormatException e) {
            return;
        }

        if (!stroke.points.isEmpty()) {
            strokes.add(stroke);
            invalidate();
        }
    }

    // 撤銷自己的最後一筆
    public String undoLastStroke() {

        for (int i = strokes.size() - 1; i >= 0; i--) {

            Stroke stroke = strokes.get(i);

            if (stroke.owner.equals(localOwner)) {

                strokes.remove(i);
                invalidate();

                return stroke.id;
            }
        }

        return null;
    }

    // 刪除指定 ID 的整筆線條
    public void removeStroke(String id) {

        for (int i = strokes.size() - 1; i >= 0; i--) {

            if (strokes.get(i).id.equals(id)) {

                strokes.remove(i);
                invalidate();

                return;
            }
        }
    }

    // 清除指定使用者的所有筆跡
    public void clearOwnerStrokes(String owner) {

        for (int i = strokes.size() - 1; i >= 0; i--) {

            if (strokes.get(i).owner.equals(owner)) {
                strokes.remove(i);
            }
        }

        invalidate();
    }

    // 清除整張畫布
    public void clearAllStrokes() {

        strokes.clear();
        currentStroke = null;

        invalidate();
    }
}
