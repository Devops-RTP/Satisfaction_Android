package ca.rtp.satisfaction

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

/** A vector-drawn face. level 1 = very unhappy (red) ... 5 = very happy (green). */
@SuppressLint("ViewConstructor")
class SmileyView(context: Context, val level: Int) : View(context) {

    private val faceColors = intArrayOf(
        Color.parseColor("#E53935"), Color.parseColor("#FB8C00"), Color.parseColor("#FDD835"),
        Color.parseColor("#7CB342"), Color.parseColor("#2E7D32"),
    )
    private val curves = floatArrayOf(-0.55f, -0.28f, 0f, 0.3f, 0.6f)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val feature = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#263238") }
    private val mouth = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#263238")
    }
    private val path = Path()

    init {
        isClickable = true
        isFocusable = true
        contentDescription = "Rating $level of 5"
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val r = size / 2f * 0.92f
        val idx = (level - 1).coerceIn(0, 4)

        fill.color = faceColors[idx]
        canvas.drawCircle(cx, cy, r, fill)
        outline.strokeWidth = r * 0.05f
        outline.color = darker(faceColors[idx])
        canvas.drawCircle(cx, cy, r - outline.strokeWidth / 2, outline)

        // Eyes
        val eyeR = r * 0.11f
        canvas.drawCircle(cx - r * 0.34f, cy - r * 0.22f, eyeR, feature)
        canvas.drawCircle(cx + r * 0.34f, cy - r * 0.22f, eyeR, feature)

        // Eyebrows on the angriest face
        if (level == 1) {
            mouth.strokeWidth = r * 0.07f
            canvas.drawLine(cx - r * 0.50f, cy - r * 0.48f, cx - r * 0.20f, cy - r * 0.38f, mouth)
            canvas.drawLine(cx + r * 0.50f, cy - r * 0.48f, cx + r * 0.20f, cy - r * 0.38f, mouth)
        }

        // Mouth: quadratic curve, control point above (frown) or below (smile)
        val curve = curves[idx]
        val mouthY = cy + r * 0.38f - curve * r * 0.18f
        mouth.strokeWidth = r * 0.10f
        path.reset()
        path.moveTo(cx - r * 0.45f, mouthY)
        path.quadTo(cx, mouthY + curve * r * 0.75f, cx + r * 0.45f, mouthY)
        canvas.drawPath(path, mouth)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        return super.onTouchEvent(event)
    }

    private fun darker(c: Int): Int =
        Color.rgb((Color.red(c) * 0.75).toInt(), (Color.green(c) * 0.75).toInt(), (Color.blue(c) * 0.75).toInt())
}
