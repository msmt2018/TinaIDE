package com.wuxianggujun.tinaide.ui.compose.screens.testing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.components.TinaBackHandlers
import com.wuxianggujun.tinaide.ui.compose.components.TinaTopBar
import com.wuxianggujun.tinaide.ui.compose.components.tinaBackAction
import com.wuxianggujun.tinaide.ui.theme.TinaIDETheme

private const val EXTRA_TEST_ID = "test_id"
private const val EXTRA_FINISH_ON_BACK_IF_DIRECT = "finish_on_back_if_direct"

internal data class DevTestExitEffect(
    val nextTestId: String? = null,
    val finishActivity: Boolean = false
)

internal data class DevTestHostState(
    val currentTest: DevTestItem?,
    val tests: List<DevTestItem>,
    val interceptBack: Boolean
)

internal object DevTestActivitySupport {
    fun buildStartIntent(
        context: Context,
        testId: String? = null,
        finishOnBackIfDirect: Boolean = false
    ): Intent = Intent(context, DevTestActivity::class.java).apply {
        if (context !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        testId?.takeUnless { it.isBlank() }?.let {
            putExtra(EXTRA_TEST_ID, it)
            if (finishOnBackIfDirect) {
                putExtra(EXTRA_FINISH_ON_BACK_IF_DIRECT, true)
            }
        }
    }

    fun extractInitialTestId(intent: Intent): String? = intent.getStringExtra(EXTRA_TEST_ID)?.takeUnless { it.isBlank() }

    fun extractFinishOnBackIfDirect(intent: Intent): Boolean = intent.getBooleanExtra(EXTRA_FINISH_ON_BACK_IF_DIRECT, false)

    fun resolveCurrentTest(testId: String?): DevTestItem? = testId?.takeUnless { it.isBlank() }?.let(DevTestRegistry::findById)

    fun normalizeRequestedTestId(testId: String?): String? = resolveCurrentTest(testId)?.id

    fun resolveSelectedTestId(testId: String): String? = DevTestRegistry.findById(testId)?.id

    fun resolveHostState(currentTestId: String?): DevTestHostState {
        val currentTest = resolveCurrentTest(currentTestId)
        return DevTestHostState(
            currentTest = currentTest,
            tests = DevTestRegistry.getAllTests(),
            interceptBack = currentTest != null
        )
    }

    fun resolveExitEffect(
        currentTestId: String?,
        initialTestId: String?,
        finishOnBackIfDirect: Boolean
    ): DevTestExitEffect {
        val shouldFinishActivity = finishOnBackIfDirect &&
            currentTestId != null &&
            currentTestId == initialTestId
        return if (shouldFinishActivity) {
            DevTestExitEffect(finishActivity = true)
        } else {
            DevTestExitEffect(nextTestId = null)
        }
    }
}

/**
 * 统一的开发者测试入口 Activity
 *
 * 使用方式：
 * 1. 显示测试列表：DevTestActivity.start(context)
 * 2. 直接打开某个测试：DevTestActivity.start(context, "clangd")
 */
class DevTestActivity : ComponentActivity() {

    companion object {
        /**
         * 启动测试入口
         * @param testId 可选，指定直接打开某个测试
         */
        fun start(
            context: Context,
            testId: String? = null,
            finishOnBackIfDirect: Boolean = false
        ) {
            context.startActivity(
                DevTestActivitySupport.buildStartIntent(
                    context = context,
                    testId = testId,
                    finishOnBackIfDirect = finishOnBackIfDirect
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialTestId = DevTestActivitySupport.extractInitialTestId(intent)
        val finishOnBackIfDirect = DevTestActivitySupport.extractFinishOnBackIfDirect(intent)

        setContent {
            TinaIDETheme {
                DevTestHost(
                    initialTestId = initialTestId,
                    finishOnBackIfDirect = finishOnBackIfDirect,
                    onFinish = { finish() }
                )
            }
        }
    }
}

@Composable
private fun DevTestHost(
    initialTestId: String?,
    finishOnBackIfDirect: Boolean,
    onFinish: () -> Unit
) {
    var currentTestId by remember {
        mutableStateOf(DevTestActivitySupport.normalizeRequestedTestId(initialTestId))
    }
    val hostState = DevTestActivitySupport.resolveHostState(currentTestId)
    val exitCurrentTest = {
        val effect = DevTestActivitySupport.resolveExitEffect(
            currentTestId = currentTestId,
            initialTestId = initialTestId,
            finishOnBackIfDirect = finishOnBackIfDirect
        )
        currentTestId = effect.nextTestId
        if (effect.finishActivity) {
            onFinish()
        }
    }
    val handleBack = {
        if (hostState.interceptBack) {
            exitCurrentTest()
        } else {
            onFinish()
        }
    }

    TinaBackHandlers(
        tinaBackAction(enabled = hostState.interceptBack) {
            handleBack()
        }
    )

    val currentTest = hostState.currentTest

    if (currentTest != null) {
        // 显示具体测试界面
        currentTest.content(handleBack)
    } else {
        // 显示测试列表
        DevTestListScreen(
            tests = hostState.tests,
            onNavigateBack = handleBack,
            onTestSelected = { testId ->
                currentTestId = DevTestActivitySupport.resolveSelectedTestId(testId)
            }
        )
    }
}

@Composable
private fun DevTestListScreen(
    tests: List<DevTestItem>,
    onNavigateBack: () -> Unit,
    onTestSelected: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TinaTopBar(
                title = stringResource(Strings.dev_options_testing_tools),
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tests, key = { it.id }) { test ->
                TestItemCard(
                    test = test,
                    onClick = { onTestSelected(test.id) }
                )
            }
        }
    }
}

@Composable
private fun TestItemCard(
    test: DevTestItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(test.titleRes),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(test.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
