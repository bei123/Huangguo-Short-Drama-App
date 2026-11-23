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
 *
 * show/hide 都丢到主线程串行，并用 desiredVisible 避免：
 * Tab 页 show 与二级页 hide 交叉时，hide 后把刚挂上的栏卸掉且不再重建。
 */
object GlassTabHost {
    private val TITLES = arrayOf("首页", "发现", "刷", "搜索", "我的")
    private val ICON_NAMES = arrayOf("hg_tab_home", "hg_tab_cate", "hg_tab_swipe", "hg_tab_search", "hg_tab_mine")
    private const val DEFAULT_TINT = 0xFFE8C27A.toInt()

    @Volatile
    private var tabBar: HgLiquidTabBar? = null

    @Volatile
    private var suppressCallback: Boolean = false

    @Volatile
    private var selectCallback: ((Number) -> Unit)? = null

    @Volatile
    private var desiredVisible: Boolean = false

    @Volatile
    private var lastTint: Int = DEFAULT_TINT

    @Volatile
    private var attachedTabCount: Int = 0

    fun show(activity: Activity, selectedTint: Number, onSelect: (Number) -> Unit) {
        selectCallback = onSelect
        desiredVisible = true
        val tintVal = selectedTint.toInt()
        lastTint = if (tintVal == 0) DEFAULT_TINT else tintVal

        activity.runOnUiThread {
            if (!desiredVisible) {
                console.log("[hg-lg] show aborted, hidden preferred")
                return@runOnUiThread
            }
            ensureAttached(activity, lastTint)
        }
    }

    private fun ensureAttached(activity: Activity, tint: Int) {
        val existing = tabBar
        if (existing != null) {
            // Tab 数量变更（如新增「刷」）时强制重建，避免旧 4 栏残留
            if (attachedTabCount != TITLES.size) {
                console.log("[hg-lg] tab count changed, rebuild")
                hideInternal()
            } else {
                val parent = existing.parent as? ViewGroup
                if (parent == null) {
                    console.log("[hg-lg] re-attach orphan tabbar")
                    attachBar(activity, existing)
                } else {
                    existing.bringToFront()
                    existing.visibility = android.view.View.VISIBLE
                    console.log("[hg-lg] refresh + bringToFront")
                }
                return
            }
        }

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

        attachBar(activity, bar)
        tabBar = bar
        attachedTabCount = TITLES.size
        console.log("[hg-lg] tabbar attached count=" + attachedTabCount)
    }

    private fun attachBar(activity: Activity, bar: HgLiquidTabBar) {
        val content = activity.window.decorView
            .findViewById<FrameLayout>(android.R.id.content) ?: return
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
        if (bar.parent != null) {
            (bar.parent as ViewGroup).removeView(bar)
        }
        content.addView(bar, lp)
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
        desiredVisible = false
        val act = activity
        if (act != null) {
            act.runOnUiThread {
                if (desiredVisible) {
                    console.log("[hg-lg] hide skipped, show preferred")
                    return@runOnUiThread
                }
                hideInternal()
            }
        } else {
            hideInternal()
        }
    }

    private fun hideInternal() {
        val bar = tabBar ?: return
        val parent = bar.parent as? ViewGroup
        parent?.removeView(bar)
        tabBar = null
        selectCallback = null
        attachedTabCount = 0
        console.log("[hg-lg] tabbar detached")
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
