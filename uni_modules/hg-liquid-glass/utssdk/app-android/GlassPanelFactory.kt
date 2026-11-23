package uts.sdk.modules.hgLiquidGlass

import android.app.Activity
import android.view.ViewGroup
import com.example.liquidglass.LiquidGlassView

/**
 * 创建可嵌入 native-view 的 LiquidGlassView。
 * cornerRadius / tint 用 Number，方便 UTS 桥接。
 */
object GlassPanelFactory {
    /** 默认深色半透明金调 tint：ARGB（signed Int） */
    const val TINT_DARK_GOLD: Int = 0x3DE8C27A
    const val TINT_DARK: Int = (0x991A1D26).toInt()
    const val TINT_CLEAR: Int = 0x33FFFFFF

    fun create(
        activity: Activity,
        cornerRadiusDp: Number,
        tintArgb: Number
    ): LiquidGlassView {
        val density = activity.resources.displayMetrics.density
        val radiusPx = cornerRadiusDp.toFloat() * density
        val tint = tintArgb.toInt()
        return LiquidGlassView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            enableDynamicBackground = true
            cornerRadius = radiusPx
            glassTint = if (tint == 0) TINT_DARK else tint
            enableAdaptiveTint = false
            enableSensorHighlight = false
            enablePressEffect = false
            // 面板场景用稍轻折射，避免文字发糊
            try {
                refractionHeight = 10f * density
                bevelWidth = 16f * density
                dispersionStrength = 0.06f
            } catch (_: Throwable) {
            }
        }
    }
}
