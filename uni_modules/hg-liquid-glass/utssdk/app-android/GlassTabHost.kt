package uts.sdk.modules.hgLiquidGlass

import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import io.dcloud.uts.console
import io.dcloud.uts.setTimeout

/**
 * 把液态玻璃 TabBar 挂到 Activity content 底部。
 * 回调必须经 UTS setTimeout 抛回，才能安全调用 uni.switchTab。
 */
object GlassTabHost {
    private val TITLES = arrayOf("首页", "发现", "搜索", "我的")
    private val ICON_NAMES = arrayOf("hg_tab_home", "hg_tab_cate", "hg_tab_search", "hg_tab_mine")
    private const val DEFAULT_TINT = 0xFFE8C27A.toInt()

    @Volatile
    private var tabBar: HgLiquidTabBar? = null

    @Volatile
    private var suppressCallback: Boolean = false

    @Volatile
    private var selectCallback: ((Number) -> Unit)? = null

    fun show(activity: Activity, selectedTint: Number, onSelect: (Number) -> Unit) {
        // 每次安装都刷新回调（页面 onShow 会重复调用）
        selectCallback = onSelect
        if (tabBar != null) {
            console.log("[hg-lg] refresh callback only")
            return
        }

        activity.runOnUiThread {
            if (tabBar != null) {
                return@runOnUiThread
            }
            val content = activity.window.decorView
                .findViewById<FrameLayout>(android.R.id.content) ?: return@runOnUiThread

            val tintVal = selectedTint.toInt()
            val tint = if (tintVal == 0) DEFAULT_TINT else tintVal
            val bar = HgLiquidTabBar(activity).apply {
                selectedTintColor = tint
            }

            val items = ArrayList<com.example.liquidglass.LiquidGlassTabBar.TabItem>()
            val pkg = activity.packageName
            val res = activity.resources
            for (i in TITLES.indices) {
                var icon: Drawable? = null
                val id = res.getIdentifier(ICON_NAMES[i], "drawable", pkg)
                if (id != 0) {
                    try {
                        icon = activity.getDrawable(id)
                    } catch (_: Throwable) {
                    }
                }
                items.add(
                    com.example.liquidglass.LiquidGlassTabBar.TabItem(TITLES[i], icon)
                )
            }
            bar.setTabs(items)
            bar.onTabSelected = { index ->
                onNativeTabSelected(index)
            }

            val density = activity.resources.displayMetrics.density
            fun dp(v: Int): Int = (v * density + 0.5f).toInt()

            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM
                leftMargin = dp(14)
                rightMargin = dp(14)
                bottomMargin = dp(10) + navBarInset(activity)
            }
            bar.elevation = dp(12).toFloat()
            bar.isClickable = true
            bar.isFocusable = true
            content.addView(bar, lp)
            tabBar = bar
            console.log("[hg-lg] tabbar attached")
        }
    }

    private fun onNativeTabSelected(index: Int) {
        if (suppressCallback) {
            console.log("[hg-lg] suppress " + index)
            return
        }
        console.log("[hg-lg] native select " + index)
        // 必须进 UTS 定时器上下文，才能安全调用 uni.switchTab
        setTimeout({
            val cb = selectCallback
            if (cb != null && !suppressCallback) {
                console.log("[hg-lg] fire callback " + index)
                cb(index)
            }
        }, 0)
    }

    fun setIndex(index: Number) {
        val bar = tabBar ?: return
        val act = bar.context as? Activity ?: return
        val idx = index.toInt()
        act.runOnUiThread {
            // 同步抑制：setter 内会同步触发 onTabSelected
            suppressCallback = true
            try {
                if (bar.selectedIndex != idx) {
                    bar.selectedIndex = idx
                }
            } finally {
                suppressCallback = false
            }
        }
    }

    fun hide(activity: Activity?) {
        activity?.runOnUiThread { hideInternal() } ?: hideInternal()
    }

    private fun hideInternal() {
        val bar = tabBar ?: return
        val parent = bar.parent as? ViewGroup
        parent?.removeView(bar)
        tabBar = null
        selectCallback = null
    }

    private fun navBarInset(activity: Activity): Int {
        return try {
            val res = activity.resources
            val id = res.getIdentifier("navigation_bar_height", "dimen", "android")
            if (id > 0) res.getDimensionPixelSize(id) else 0
        } catch (_: Throwable) {
            0
        }
    }
}
