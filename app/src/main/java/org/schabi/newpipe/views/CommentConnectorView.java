package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CommentConnectorView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path curvePath = new Path();

    public CommentConnectorView(@NonNull final Context context) {
        this(context, null);
    }

    public CommentConnectorView(@NonNull final Context context,
                                @Nullable final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CommentConnectorView(@NonNull final Context context,
                                @Nullable final AttributeSet attrs,
                                final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(final Context context) {
        final float density = context.getResources().getDisplayMetrics().density;
        linePaint.setColor(Color.parseColor("#555555"));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeWidth(1.5f * density);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        final float density = getContext().getResources().getDisplayMetrics().density;
        final float startX = 17f * density;
        final float startY = 0f;
        final float endY = getHeight() / 2f;
        final float endX = getWidth();
        final float radius = Math.min(12f * density, endY);

        curvePath.reset();
        curvePath.moveTo(startX, startY);
        curvePath.lineTo(startX, endY - radius);
        curvePath.quadTo(startX, endY, startX + radius, endY);
        curvePath.lineTo(endX, endY);

        canvas.drawPath(curvePath, linePaint);
    }
}
