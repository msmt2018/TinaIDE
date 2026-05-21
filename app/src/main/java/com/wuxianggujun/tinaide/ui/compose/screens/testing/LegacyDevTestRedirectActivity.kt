package com.wuxianggujun.tinaide.ui.compose.screens.testing

import android.os.Bundle
import androidx.activity.ComponentActivity

abstract class LegacyDevTestRedirectActivity : ComponentActivity() {

    protected abstract val targetTestId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            DevTestActivitySupport.buildStartIntent(
                context = this,
                testId = targetTestId,
                finishOnBackIfDirect = true
            )
        )
        finish()
    }
}
