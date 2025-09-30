package com.example.lingo.ui.main.translation.camera

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CameraOverlayView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private val dimPaint = Paint().apply { color = Color.parseColor("#99000000") }
    private val framePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = resources.displayMetrics.scaledDensity * 14
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    private val clearPath = Path()
    private val rc = RectF()
    private val radius by lazy { resources.displayMetrics.density * 10 }

    // A4 비율 1 : 1.414
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // 가능한 큰 직사각형을 중앙에 배치 (상단 앱바 제외는 레이아웃에서 marginTop으로 처리)
        val targetRatio = 1f / 1.414f
        val viewRatio = w / h

        val rectWidth: Float
        val rectHeight: Float
        if (viewRatio > targetRatio) {
            // 화면이 더 넓음 -> 높이에 맞춤
            rectHeight = h * 0.7f
            rectWidth = rectHeight * targetRatio
        } else {
            rectWidth = w * 0.9f
            rectHeight = rectWidth / targetRatio
        }
        rc.set((w - rectWidth) / 2f, (h - rectHeight) / 2f, (w + rectWidth) / 2f, (h + rectHeight) / 2f)

        // 바깥 어둡게 + 안쪽은 투명하게 파내기
        val layer = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawRect(0f, 0f, w, h, dimPaint)
        clearPath.reset()
        clearPath.addRoundRect(rc, radius, radius, Path.Direction.CW)
        val clearPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            isAntiAlias = true
        }
        canvas.drawPath(clearPath, clearPaint)
        canvas.restoreToCount(layer)

        // 프레임
        canvas.drawRoundRect(rc, radius, radius, framePaint)

        // 중앙 텍스트
        canvas.drawText("영역 안 투명하게\nA4 비율 1:1.414", rc.centerX(), rc.centerY() - (textPaint.descent() + textPaint.ascent())/2, textPaint)
    }

    fun getCaptureRect(): RectF = RectF(rc)
}
