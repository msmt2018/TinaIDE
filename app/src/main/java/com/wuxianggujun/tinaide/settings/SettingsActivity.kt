package com.wuxianggujun.tinaide.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.wuxianggujun.tinaide.R
import com.wuxianggujun.tinaide.base.BaseActivity
import com.wuxianggujun.tinaide.databinding.ActivitySettingsBinding
import com.wuxianggujun.tinaide.utils.Logger

/**
 * 设置界面 Activity。
 *
 * 参考 CodeAssist 的 SettingsActivity：
 * - 使用 Toolbar + Fragment 容器承载 PreferenceFragmentCompat；
 * - 实现 OnPreferenceStartFragmentCallback 处理多级设置导航；
 * - 点击子项时切换 Fragment 并更新标题，呈现更统一的 MD 风格。
 */
class SettingsActivity :
    BaseActivity<ActivitySettingsBinding>(ActivitySettingsBinding::inflate),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)  // BaseActivity 已处理主题和状态栏

        // 设置 Toolbar
        val toolbar = binding.toolbar
        Logger.i("SettingsActivity onCreate, toolbar=$toolbar", tag = "Settings")
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 默认标题：设置
        val initialTitle = getString(R.string.menu_settings)
        toolbar.title = initialTitle
        Logger.i("Initial toolbar title set to: $initialTitle", tag = "Settings")

        // 返回按钮：优先走系统 Back，配合 Fragment back stack
        toolbar.setNavigationOnClickListener {
            onSupportNavigateUp()
        }

        // 加载根设置 Fragment（根列表）
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    /**
     * 供 Fragment 调用，更新设置页标题（Toolbar + ActionBar）。
     */
    fun updateTitle(title: CharSequence) {
        Logger.i("updateTitle() called with: \"$title\"", tag = "Settings")
        Logger.i(
            "Before update: toolbar.title=\"${binding.toolbar.title}\", actionBar.title=\"${supportActionBar?.title}\"",
            tag = "Settings"
        )
        binding.toolbar.title = title
        supportActionBar?.title = title
        Logger.i(
            "After update: toolbar.title=\"${binding.toolbar.title}\", actionBar.title=\"${supportActionBar?.title}\"",
            tag = "Settings"
        )
    }

    /**
     * 处理 Preference 中声明的 fragment 跳转（与 CodeAssist 类似）。
     */
    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val fragmentClassName = pref.fragment ?: return false
        Logger.i("onPreferenceStartFragment: fragment=$fragmentClassName", tag = "Settings")

        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            fragmentClassName
        )
        fragment.arguments = args

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_container, fragment)
            .addToBackStack(null)
            .commit()

        pref.title?.let { updateTitle(it) }
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        // 先尝试弹出设置子页的 back stack；如果为空再走默认返回逻辑
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        }
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

