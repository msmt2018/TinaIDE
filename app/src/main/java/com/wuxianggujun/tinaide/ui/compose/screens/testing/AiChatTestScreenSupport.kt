package com.wuxianggujun.tinaide.ui.compose.screens.testing

import com.wuxianggujun.tinaide.ai.api.ChatMessage
import com.wuxianggujun.tinaide.ai.api.ChatRole
import com.wuxianggujun.tinaide.ai.api.ChatUsage
import com.wuxianggujun.tinaide.ai.api.ToolCall
import com.wuxianggujun.tinaide.ai.api.ToolFunction

internal data class AiChatTestScenario(
    val title: String,
    val description: String,
    val messages: List<ChatMessage>
)

internal data class AiChatScenarioCatalogValidation(
    val scenarioCount: Int,
    val hasMarkdownCodeFence: Boolean,
    val hasToolCallScenario: Boolean,
    val hasToolResponseScenario: Boolean,
    val hasUsageScenario: Boolean,
    val hasTableScenario: Boolean,
    val issues: List<String>
) {
    val isValid: Boolean
        get() = issues.isEmpty() &&
            hasMarkdownCodeFence &&
            hasToolCallScenario &&
            hasToolResponseScenario &&
            hasUsageScenario &&
            hasTableScenario
}

internal object AiChatTestScreenSupport {
    fun buildScenarios(): List<AiChatTestScenario> = listOf(
        AiChatTestScenario(
            title = "Markdown 基础渲染",
            description = "测试标题、列表、代码块、粗体、斜体等基础 Markdown 语法",
            messages = listOf(
                ChatMessage(
                    role = ChatRole.USER,
                    content = "请展示 Markdown 的基础语法"
                ),
                ChatMessage(
                    role = ChatRole.ASSISTANT,
                    content = """
# 一级标题
## 二级标题
### 三级标题

这是一段普通文本，包含 **粗体** 和 *斜体* 以及 `行内代码`。

## 列表

无序列表：
- 项目 1
- 项目 2
  - 子项目 2.1
  - 子项目 2.2
- 项目 3

有序列表：
1. 第一项
2. 第二项
3. 第三项

## 代码块

```kotlin
fun main() {
    println("Hello, World!")
}
```

## 引用

> 这是一段引用文本
> 可以跨越多行

## 链接

[TinaIDE](https://tinaide.com)
                    """.trimIndent()
                )
            )
        ),
        AiChatTestScenario(
            title = "多语言代码高亮",
            description = "测试不同编程语言的代码块渲染",
            messages = listOf(
                ChatMessage(
                    role = ChatRole.USER,
                    content = "展示不同语言的代码示例"
                ),
                ChatMessage(
                    role = ChatRole.ASSISTANT,
                    content = """
这里是几种常见编程语言的代码示例：

**Kotlin:**
```kotlin
data class User(val name: String, val age: Int)

fun greet(user: User) {
    println("Hello, ${'$'}{user.name}!")
}
```

**Java:**
```java
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}
```

**Python:**
```python
def fibonacci(n):
    if n <= 1:
        return n
    return fibonacci(n-1) + fibonacci(n-2)

print(fibonacci(10))
```

**JavaScript:**
```javascript
const fetchData = async () => {
    const response = await fetch('/api/data');
    const data = await response.json();
    return data;
};
```

**C++:**
```cpp
#include <iostream>
#include <vector>

int main() {
    std::vector<int> numbers = {1, 2, 3, 4, 5};
    for (const auto& num : numbers) {
        std::cout << num << " ";
    }
    return 0;
}
```
                    """.trimIndent()
                )
            )
        ),
        AiChatTestScenario(
            title = "工具调用测试",
            description = "测试 AI 工具调用的渲染和交互",
            messages = listOf(
                ChatMessage(
                    role = ChatRole.USER,
                    content = "请获取当前文件内容"
                ),
                ChatMessage(
                    role = ChatRole.ASSISTANT,
                    content = "",
                    toolCalls = listOf(
                        ToolCall(
                            id = "call_1",
                            type = "function",
                            function = ToolFunction(
                                name = "get_current_file",
                                arguments = "{}"
                            )
                        )
                    )
                ),
                ChatMessage(
                    role = ChatRole.TOOL,
                    content = """{"ok":true,"fileName":"MainActivity.kt","language":"kotlin","content":"package com.example\n\nclass MainActivity : AppCompatActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n    }\n}"}""",
                    toolCallId = "call_1"
                ),
                ChatMessage(
                    role = ChatRole.ASSISTANT,
                    content = """
当前文件是 `MainActivity.kt`，这是一个 Kotlin 文件。文件内容如下：

```kotlin
package com.example

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
```

这是一个标准的 Android Activity 类，继承自 `AppCompatActivity`。
                    """.trimIndent()
                )
            )
        ),
        AiChatTestScenario(
            title = "复杂对话流程",
            description = "测试多轮对话、代码解释、错误修复等场景",
            messages = listOf(
                ChatMessage(
                    role = ChatRole.USER,
                    content = "我的代码报错了：NullPointerException"
                ),
                ChatMessage(
                    role = ChatRole.ASSISTANT,
                    content = """
`NullPointerException` 是 Java/Kotlin 中最常见的运行时异常之一。让我帮你分析可能的原因：

## 常见原因

1. **访问 null 对象的方法或属性**
```kotlin
val user: User? = null
user.name // 💥 NullPointerException
```

2. **数组或集合元素为 null**
```kotlin
val list = listOf(null, "hello")
list[0].length // 💥 NullPointerException
```

3. **未初始化的变量**
```kotlin
lateinit var name: String
println(name) // 💥 UninitializedPropertyAccessException
```

## 解决方案

使用 Kotlin 的空安全特性：

```kotlin
// 安全调用
val length = user?.name?.length

// Elvis 操作符
val name = user?.name ?: "Unknown"

// 非空断言（确定不为 null 时使用）
val name = user!!.name

// let 函数
user?.let {
    println(it.name)
}
```

能否分享一下具体的代码片段？我可以帮你定位问题。
                    """.trimIndent()
                ),
                ChatMessage(
                    role = ChatRole.USER,
                    content = """
```kotlin
fun getUserName(userId: String): String {
    val user = database.findUser(userId)
    return user.name
}
```
                    """.trimIndent()
                ),
                ChatMessage(
                    role = ChatRole.ASSISTANT,
                    content = """
找到问题了！`database.findUser(userId)` 可能返回 `null`，但你直接访问了 `user.name`。

## 修复方案

**方案 1：使用安全调用和 Elvis 操作符**
```kotlin
fun getUserName(userId: String): String {
    val user = database.findUser(userId)
    return user?.name ?: "Unknown User"
}
```

**方案 2：使用 let 函数**
```kotlin
fun getUserName(userId: String): String {
    return database.findUser(userId)?.let { user ->
        user.name
    } ?: "Unknown User"
}
```

**方案 3：返回可空类型**
```kotlin
fun getUserName(userId: String): String? {
    val user = database.findUser(userId)
    return user?.name
}
```

推荐使用方案 1 或方案 2，它们更符合 Kotlin 的惯用写法。
                    """.trimIndent()
                )
            )
        ),
        AiChatTestScenario(
            title = "Token 使用统计",
            description = "测试带有 usage 信息的消息渲染",
            messages = listOf(
                ChatMessage(
                    role = ChatRole.USER,
                    content = "解释一下什么是协程"
                ),
                ChatMessage(
                    role = ChatRole.ASSISTANT,
                    content = """
# Kotlin 协程 (Coroutines)

协程是 Kotlin 提供的一种轻量级并发解决方案，用于简化异步编程。

## 核心概念

1. **挂起函数 (Suspend Function)**
   - 使用 `suspend` 关键字标记
   - 可以在不阻塞线程的情况下暂停执行

2. **协程作用域 (CoroutineScope)**
   - 管理协程的生命周期
   - 常见的有 `GlobalScope`、`viewModelScope`、`lifecycleScope`

3. **调度器 (Dispatcher)**
   - `Dispatchers.Main`: UI 线程
   - `Dispatchers.IO`: IO 操作
   - `Dispatchers.Default`: CPU 密集型任务

## 示例代码

```kotlin
class MyViewModel : ViewModel() {
    fun loadData() {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                // 在 IO 线程执行网络请求
                api.fetchData()
            }
            // 自动切回主线程更新 UI
            updateUI(data)
        }
    }
}
```

## 优势

- ✅ 代码简洁，避免回调地狱
- ✅ 结构化并发，易于管理
- ✅ 异常处理更直观
- ✅ 性能优秀，开销小
                    """.trimIndent(),
                    usage = ChatUsage(
                        promptTokens = 15,
                        completionTokens = 320,
                        totalTokens = 335
                    )
                )
            )
        ),
        AiChatTestScenario(
            title = "表格和复杂格式",
            description = "测试表格、任务列表等复杂 Markdown 格式",
            messages = listOf(
                ChatMessage(
                    role = ChatRole.USER,
                    content = "对比一下 Kotlin 和 Java"
                ),
                ChatMessage(
                    role = ChatRole.ASSISTANT,
                    content = """
# Kotlin vs Java 对比

## 特性对比表

| 特性 | Kotlin | Java |
|------|--------|------|
| 空安全 | ✅ 内置 | ❌ 需要注解 |
| 扩展函数 | ✅ 支持 | ❌ 不支持 |
| 数据类 | ✅ `data class` | ❌ 需手写 |
| 协程 | ✅ 原生支持 | ❌ 需要库 |
| Lambda | ✅ 简洁 | ⚠️ 冗长 |
| 类型推断 | ✅ 强大 | ⚠️ 有限 |

## 代码对比

**定义数据类：**

Kotlin:
```kotlin
data class User(val name: String, val age: Int)
```

Java:
```java
public class User {
    private final String name;
    private final int age;

    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // getters, equals, hashCode, toString...
}
```

## 总结

- ✅ Kotlin 更简洁、安全
- ✅ Java 生态成熟、稳定
- ✅ 两者可以无缝互操作
                    """.trimIndent()
                )
            )
        )
    )

    fun validateScenarioCatalog(
        scenarios: List<AiChatTestScenario>
    ): AiChatScenarioCatalogValidation {
        val issues = mutableListOf<String>()
        val duplicateTitles = scenarios
            .groupBy { it.title }
            .filterValues { it.size > 1 }
            .keys
        duplicateTitles.forEach { title ->
            issues += "duplicate title: $title"
        }

        scenarios.forEachIndexed { index, scenario ->
            if (scenario.title.isBlank()) {
                issues += "scenario[$index] has blank title"
            }
            if (scenario.description.isBlank()) {
                issues += "scenario[$index] has blank description"
            }
            if (scenario.messages.isEmpty()) {
                issues += "scenario[$index] has no messages"
                return@forEachIndexed
            }
            if (scenario.messages.first().role != ChatRole.USER) {
                issues += "scenario[$index] must start with user message"
            }

            val declaredToolCallIds = scenario.messages
                .flatMap { message -> message.toolCalls.orEmpty() }
                .mapNotNull { toolCall -> toolCall.id }
                .toSet()
            scenario.messages
                .filter { message -> message.role == ChatRole.TOOL }
                .forEachIndexed { toolIndex, toolMessage ->
                    val toolCallId = toolMessage.toolCallId
                    if (toolCallId.isNullOrBlank()) {
                        issues += "scenario[$index] tool message[$toolIndex] missing toolCallId"
                    } else if (toolCallId !in declaredToolCallIds) {
                        issues += "scenario[$index] tool message[$toolIndex] references unknown toolCallId=$toolCallId"
                    }
                }
        }

        val assistantMessages = scenarios
            .flatMap { scenario -> scenario.messages }
            .filter { message -> message.role == ChatRole.ASSISTANT }
        val assistantContents = assistantMessages.map { message -> message.content }

        return AiChatScenarioCatalogValidation(
            scenarioCount = scenarios.size,
            hasMarkdownCodeFence = assistantContents.any { content -> "```" in content },
            hasToolCallScenario = assistantMessages.any { message -> !message.toolCalls.isNullOrEmpty() },
            hasToolResponseScenario = scenarios.any { scenario ->
                scenario.messages.any { message -> message.role == ChatRole.TOOL }
            },
            hasUsageScenario = scenarios.any { scenario ->
                scenario.messages.any { message -> message.usage != null }
            },
            hasTableScenario = assistantContents.any(::containsMarkdownTable),
            issues = issues
        )
    }

    private fun containsMarkdownTable(content: String): Boolean {
        val tableLines = content.lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.startsWith("|") && line.endsWith("|") }
            .count()
        return tableLines >= 2
    }
}
