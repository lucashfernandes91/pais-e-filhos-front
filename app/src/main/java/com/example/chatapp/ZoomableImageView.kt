package com.example.chatapp

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * ImageView com pinça pra zoom, duplo toque e arrastar quando ampliada —
 * mesmo padrão de visualizador de foto usado em apps de mensagens.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    companion object {
        private const val MIN_SCALE = 1f
        private const val MAX_SCALE = 4f
    }

    var onTap: (() -> Unit)? = null

    private val baseMatrix = Matrix()
    private val suppMatrix = Matrix()
    private val drawMatrix = Matrix()
    private var scale = MIN_SCALE
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val newScale = (scale * detector.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                val factor = newScale / scale
                scale = newScale
                suppMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
                applyMatrix()
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (scale > MIN_SCALE + 0.01f) {
                    resetZoom()
                } else {
                    val factor = MAX_SCALE / scale
                    scale = MAX_SCALE
                    suppMatrix.postScale(factor, factor, e.x, e.y)
                    applyMatrix()
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onTap?.invoke()
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        resetZoom()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetZoom()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (scale > MIN_SCALE + 0.01f && event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    suppMatrix.postTranslate(event.x - lastX, event.y - lastY)
                    applyMatrix()
                }
                lastX = event.x
                lastY = event.y
            }
        }
        return true
    }

    private fun resetZoom() {
        scale = MIN_SCALE
        suppMatrix.reset()
        computeBaseMatrix()
        applyMatrix()
    }

    private fun computeBaseMatrix() {
        baseMatrix.reset()
        val d = drawable ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val dWidth = d.intrinsicWidth.toFloat()
        val dHeight = d.intrinsicHeight.toFloat()
        if (viewWidth <= 0 || viewHeight <= 0 || dWidth <= 0 || dHeight <= 0) return

        val fitScale = minOf(viewWidth / dWidth, viewHeight / dHeight)
        baseMatrix.postScale(fitScale, fitScale)
        baseMatrix.postTranslate(
            (viewWidth - dWidth * fitScale) / 2f,
            (viewHeight - dHeight * fitScale) / 2f
        )
    }

    /** Impede que a imagem seja arrastada/zoomada pra fora da área visível. */
    private fun constrainTranslation() {
        val d = drawable ?: return
        val rect = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        val combined = Matrix(baseMatrix).apply { postConcat(suppMatrix) }
        combined.mapRect(rect)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        val dx = when {
            rect.width() <= viewWidth -> (viewWidth - rect.width()) / 2f - rect.left
            rect.left > 0 -> -rect.left
            rect.right < viewWidth -> viewWidth - rect.right
            else -> 0f
        }
        val dy = when {
            rect.height() <= viewHeight -> (viewHeight - rect.height()) / 2f - rect.top
            rect.top > 0 -> -rect.top
            rect.bottom < viewHeight -> viewHeight - rect.bottom
            else -> 0f
        }

        suppMatrix.postTranslate(dx, dy)
    }

    private fun applyMatrix() {
        constrainTranslation()
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(suppMatrix)
        imageMatrix = drawMatrix
    }
}
