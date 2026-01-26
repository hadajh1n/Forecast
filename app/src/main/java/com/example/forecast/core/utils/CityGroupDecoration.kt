package com.example.forecast.core.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.forecast.ui.adapter.CityAdapter

class CityGroupDecoration(
    private val strokeWidth: Float,
    private val cornerRadius: Float,
    private val verticalPadding: Float,
    strokeColor: Int,
    fillColor: Int
) : RecyclerView.ItemDecoration() {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = this@CityGroupDecoration.strokeWidth
        color = strokeColor
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        var firstCityView: View? = null
        var lastCityView: View? = null

        for (i in 0 until parent.childCount) {
            val view = parent.getChildAt(i)
            val holder = parent.getChildViewHolder(view)

            if (holder is CityAdapter.CityViewHolder) {
                if (firstCityView == null) firstCityView = view
                lastCityView = view
            }
        }

        if (firstCityView == null || lastCityView == null) return

        val left = parent.paddingLeft.toFloat()
        val right = (parent.width - parent.paddingRight).toFloat()
        val top = firstCityView.top.toFloat()
        val bottom = lastCityView.bottom.toFloat()

        val rect = RectF(
            left + strokeWidth,
            top - verticalPadding + strokeWidth,
            right - strokeWidth,
            bottom + verticalPadding - strokeWidth
        )

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)
    }
}