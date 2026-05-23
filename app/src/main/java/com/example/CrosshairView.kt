package com.example

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class CrosshairView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var shape: String = "cross"
        set(value) { field = value; invalidate() }

    var crosshairSize: Int = 40 // In DP
        set(value) { field = value; invalidate() }

    var crosshairColor: Int = 0xFF00FF00.toInt() // Neon Green default
        set(value) { field = value; invalidate() }

    var thickness: Float = 3f // In DP
        set(value) { field = value; invalidate() }

    var opacity: Float = 1.0f
        set(value) { field = value; invalidate() }

    var gap: Int = 4 // In DP
        set(value) { field = value; invalidate() }

    var outline: Boolean = true
        set(value) { field = value; invalidate() }

    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF000000.toInt() // Black contrast outline
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val sizePx = (crosshairSize * density).toInt()
        val thicknessPx = thickness * density
        val gapPx = gap * density

        val cx = width / 2f
        val cy = height / 2f

        mainPaint.strokeWidth = thicknessPx
        mainPaint.color = crosshairColor
        mainPaint.alpha = (opacity * 255).toInt()

        outlinePaint.strokeWidth = thicknessPx + (1.5f * density) // Outline is slightly wider
        outlinePaint.alpha = mainPaint.alpha

        when (shape) {
            "dot" -> {
                val radiusPx = thicknessPx * 1.5f
                if (outline) {
                    canvas.drawCircle(cx, cy, radiusPx + (0.75f * density), outlinePaint.apply { style = Paint.Style.FILL })
                }
                fillPaint.color = crosshairColor
                fillPaint.alpha = mainPaint.alpha
                canvas.drawCircle(cx, cy, radiusPx, fillPaint)
            }
            "cross" -> {
                val halfSize = sizePx / 2f
                if (outline) {
                    // Draw horizontal outline lines
                    canvas.drawLine(cx - halfSize - 1f, cy, cx - gapPx + 1f, cy, outlinePaint)
                    canvas.drawLine(cx + gapPx - 1f, cy, cx + halfSize + 1f, cy, outlinePaint)
                    // Draw vertical outline lines
                    canvas.drawLine(cx, cy - halfSize - 1f, cx, cy - gapPx + 1f, outlinePaint)
                    canvas.drawLine(cx, cy + gapPx - 1f, cx, cy + halfSize + 1f, outlinePaint)
                }

                canvas.drawLine(cx - halfSize, cy, cx - gapPx, cy, mainPaint)
                canvas.drawLine(cx + gapPx, cy, cx + halfSize, cy, mainPaint)
                canvas.drawLine(cx, cy - halfSize, cx, cy - gapPx, mainPaint)
                canvas.drawLine(cx, cy + gapPx, cx, cy + halfSize, mainPaint)
            }
            "t_cross" -> {
                val halfSize = sizePx / 2f
                if (outline) {
                    // Left outline
                    canvas.drawLine(cx - halfSize - 1f, cy, cx - gapPx + 1f, cy, outlinePaint)
                    // Right outline
                    canvas.drawLine(cx + gapPx - 1f, cy, cx + halfSize + 1f, cy, outlinePaint)
                    // Bottom outline (T-cross has no top line)
                    canvas.drawLine(cx, cy + gapPx - 1f, cx, cy + halfSize + 1f, outlinePaint)
                }

                canvas.drawLine(cx - halfSize, cy, cx - gapPx, cy, mainPaint)
                canvas.drawLine(cx + gapPx, cy, cx + halfSize, cy, mainPaint)
                canvas.drawLine(cx, cy + gapPx, cx, cy + halfSize, mainPaint)
            }
            "circle" -> {
                val radius = sizePx / 3f
                if (outline) {
                    outlinePaint.style = Paint.Style.STROKE
                    canvas.drawCircle(cx, cy, radius, outlinePaint)
                }
                canvas.drawCircle(cx, cy, radius, mainPaint)
            }
            "square" -> {
                val halfSize = sizePx / 3f
                if (outline) {
                    outlinePaint.style = Paint.Style.STROKE
                    canvas.drawRect(cx - halfSize - 0.75f * density, cy - halfSize - 0.75f * density, cx + halfSize + 0.75f * density, cy + halfSize + 0.75f * density, outlinePaint)
                }
                canvas.drawRect(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize, mainPaint)
            }
            "plus_dot" -> {
                val halfSize = sizePx / 2f
                val dotRadiusPx = thicknessPx * 1.5f
                
                if (outline) {
                    // Dot outline
                    canvas.drawCircle(cx, cy, dotRadiusPx + (0.75f * density), outlinePaint.apply { style = Paint.Style.FILL })
                    // Lines outline
                    canvas.drawLine(cx - halfSize - 1f, cy, cx - gapPx + 1f, cy, outlinePaint.apply { style = Paint.Style.STROKE })
                    canvas.drawLine(cx + gapPx - 1f, cy, cx + halfSize + 1f, cy, outlinePaint)
                    canvas.drawLine(cx, cy - halfSize - 1f, cx, cy - gapPx + 1f, outlinePaint)
                    canvas.drawLine(cx, cy + gapPx - 1f, cx, cy + halfSize + 1f, outlinePaint)
                }

                // Inner Dot
                fillPaint.color = crosshairColor
                fillPaint.alpha = mainPaint.alpha
                canvas.drawCircle(cx, cy, dotRadiusPx, fillPaint)

                // Plus Lines
                canvas.drawLine(cx - halfSize, cy, cx - gapPx, cy, mainPaint)
                canvas.drawLine(cx + gapPx, cy, cx + halfSize, cy, mainPaint)
                canvas.drawLine(cx, cy - halfSize, cx, cy - gapPx, mainPaint)
                canvas.drawLine(cx, cy + gapPx, cx, cy + halfSize, mainPaint)
            }
        }
    }
}
