package com.lovoj.androidoffline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat


class LovojButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 48f
    }
    
    private var buttonText = "Lovoj Button"
    private var isPressed = false
    
    init {
         applyLovojColors()
        
        // Set click listener
        setOnClickListener {
            // Handle click
        }
        
         setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    isPressed = true
                    applyPressedColors()
                    invalidate()
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    isPressed = false
                    applyLovojColors()
                    invalidate()
                    performClick()
                    true
                }
                else -> false
            }
        }
    }
    
    private fun applyLovojColors() {
         val pinkColor = LovojAppColors.getColor(context, R.color.pink_button)
        val whiteColor = LovojAppColors.getColor(context, R.color.white_text)
        
        paint.color = pinkColor
        textPaint.color = whiteColor
    }
    
    private fun applyPressedColors() {
         val pinkColor = LovojAppColors.getColor(context, R.color.pink_button)
        val pressedColor = LovojAppColors.getColorWithAlpha(pinkColor, 0.7f)
        
        paint.color = pressedColor
        textPaint.color = LovojAppColors.getColor(context, R.color.white_text)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
         val cornerRadius = 24f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), cornerRadius, cornerRadius, paint)
        
        // Draw text
        val textX = width / 2f
        val textY = height / 2f + textPaint.textSize / 3
        canvas.drawText(buttonText, textX, textY, textPaint)
    }
    
    fun setButtonText(text: String) {
        buttonText = text
        invalidate()
    }
    

} 