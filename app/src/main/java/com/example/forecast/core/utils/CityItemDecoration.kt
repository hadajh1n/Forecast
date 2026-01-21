package com.example.forecast.core.utils

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class CityItemDecoration(
    private val spaceHorizontal: Int,
    private val spaceVertical: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.left = spaceHorizontal
        outRect.right = spaceHorizontal
        outRect.top = spaceVertical / 2
        outRect.bottom = spaceVertical / 2
    }
}