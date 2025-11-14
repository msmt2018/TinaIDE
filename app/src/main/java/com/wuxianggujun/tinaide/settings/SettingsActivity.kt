package com.wuxianggujun.tinaide.settings

import android.os.Bundle
import androidx.appcompat.widget.Toolbar
import com.wuxianggujun.tinaide.base.BaseActivity
import com.wuxianggujun.tinaide.databinding.ActivitySettingsBinding
import com.wuxianggujun.tinaide.R

/**
 * 设置界面 Activity
 * Material Design 风格
 */
class SettingsActivity : BaseActivity<ActivitySettingsBinding>(ActivitySettingsBinding::inflate) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)  // BaseActivity 已处理主题和状态栏

        // 设置 Toolbar
        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "设置"
        }

        // 加载 SettingsFragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(binding.settingsContainer.id, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
