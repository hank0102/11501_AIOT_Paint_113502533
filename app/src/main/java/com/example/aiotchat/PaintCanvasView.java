
package com.example.aiotchat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class PaintCanvasView extends View {

    private static class Stroke {
        String id;
        String owner;
        int color;
        List<PointF> points = new ArrayList<>();

        Stroke(String id, String owner, int color) {
            this.id = id;
            this.owner = owner;
            this.color = color;
        }
    }

    private final List<Stroke> strokes = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float penWidth;

    public PaintCanvasView(Context context) {
        super(context);
        init();
    }

    public PaintCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        penWidth = 4f * getResources()
                .getDisplayMetrics().density;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(penWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        setBackgroundColor(Color.WHITE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (Stroke stroke : strokes) {
            if (stroke.points.isEmpty()) {
                continue;
            }

            paint.setColor(stroke.color);

            PointF first = stroke.points.get(0);

            float x = first.x * getWidth();
            float y = first.y * getHeight();

            if (stroke.points.size() == 1) {
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(x, y, penWidth / 2f, paint);
                paint.setStyle(Paint.Style.STROKE);
                continue;
            }

            Path path = new Path();
            path.moveTo(x, y);

            for (int i = 1; i < stroke.points.size(); i++) {
                PointF point = stroke.points.get(i);

                path.lineTo(
                        point.x * getWidth(),
                        point.y * getHeight()
                );
            }

            canvas.drawPath(path, paint);
        }
    }

    public static boolean isValidPoints(String encoded) {
        if (encoded == null || encoded.isEmpty()
                || encoded.length() > 100000) {
            return false;
        }

        String[] pairs = encoded.split(";");

        if (pairs.length == 0 || pairs.length > 5000) {
            return false;
        }

        try {
            for (String pair : pairs) {
                String[] xy = pair.split(",");

                if (xy.length != 2) {
                    return false;
                }

                float x = Float.parseFloat(xy[0]);
                float y = Float.parseFloat(xy[1]);

                if (!Float.isFinite(x) || !Float.isFinite(y)
                        || x < 0 || x > 1
                        || y < 0 || y > 1) {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }

        return true;
    }

    public void addStroke(
            String owner,
            String id,
            int color,
            String encoded) {

        for (Stroke existing : strokes) {
            if (existing.id.equals(id)) {
                return;
            }
        }

        if (!isValidPoints(encoded)) {
            return;
        }

        Stroke stroke = new Stroke(id, owner, color);

        for (String pair : encoded.split(";")) {
            String[] xy = pair.split(",");

            stroke.points.add(new PointF(
                    Float.parseFloat(xy[0]),
                    Float.parseFloat(xy[1])
            ));
        }

        strokes.add(stroke);
        invalidate();
    }

    public void removeStroke(String id) {
        strokes.removeIf(stroke -> stroke.id.equals(id));
        invalidate();
    }

    public void clearOwnerStrokes(String owner) {
        strokes.removeIf(stroke -> stroke.owner.equals(owner));
        invalidate();
    }

    public void clearAllStrokes() {
        strokes.clear();
        invalidate();
    }
}
