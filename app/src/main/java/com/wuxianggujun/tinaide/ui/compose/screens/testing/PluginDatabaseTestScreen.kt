package com.wuxianggujun.tinaide.ui.compose.screens.testing

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.i18n.Strings
import com.wuxianggujun.tinaide.ui.compose.components.TinaTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object PluginDatabaseTestScreenSupport {
    fun appendLog(current: String, msg: String, timestamp: String): String {
        val newLine = "[$timestamp] $msg"
        return if (current.isEmpty()) newLine else "$newLine\n$current"
    }

    fun isSelectQuery(sql: String): Boolean = sql.trimStart().startsWith("SELECT", ignoreCase = true)

    fun buildQueryResultTable(
        columnNames: List<String>,
        rows: List<List<String>>
    ): String {
        if (columnNames.isEmpty()) {
            return ""
        }

        val header = columnNames.joinToString(" | ")
        return buildString {
            appendLine(header)
            appendLine("-".repeat(header.length))
            rows.forEach { row ->
                appendLine(row.joinToString(" | "))
            }
        }
    }
}

/**
 * 插件数据库 API 测试页面
 *
 * 用于测试 DatabaseApiModule 的功能：
 * - 创建表
 * - 插入数据
 * - 查询数据
 * - 更新/删除数据
 * - 事务操作
 */
@Composable
fun PluginDatabaseTestScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current

    var dbHelper by remember { mutableStateOf<TestDatabaseHelper?>(null) }
    var database by remember { mutableStateOf<SQLiteDatabase?>(null) }

    var sqlInput by remember { mutableStateOf("SELECT * FROM test_todos") }
    var resultText by remember { mutableStateOf("") }
    var logText by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        dbHelper = TestDatabaseHelper(context)
        database = dbHelper?.writableDatabase?.also(::ensureTestTable)
        logText = appendLog(
            current = logText,
            msg = context.getString(
                Strings.dev_options_plugin_db_initialized,
                TestDatabaseHelper.DATABASE_NAME
            )
        )

        onDispose {
            database?.close()
            dbHelper?.close()
        }
    }

    fun appendResult(msg: String) {
        resultText = msg
    }

    fun log(msg: String) {
        logText = appendLog(logText, msg)
    }

    fun errorMessage(throwable: Throwable): String {
        val detail = throwable.message
            ?: throwable::class.simpleName
            ?: throwable.javaClass.name
        return context.getString(Strings.dev_options_plugin_db_error, detail)
    }

    fun databaseOrReport(): SQLiteDatabase? {
        val activeDatabase = database
        if (activeDatabase == null) {
            val message = context.getString(Strings.dev_options_plugin_db_database_not_ready)
            log(message)
            appendResult(message)
            return null
        }
        return activeDatabase
    }

    fun executeSelectQuery(
        sql: String,
        buildLogMessage: (Int) -> String
    ) {
        val activeDatabase = databaseOrReport() ?: return
        val snapshot = activeDatabase.rawQuery(sql, null).use { it.toQuerySnapshot() }
        val rowCount = snapshot.rows.size
        val renderedTable = PluginDatabaseTestScreenSupport.buildQueryResultTable(
            columnNames = snapshot.columnNames,
            rows = snapshot.rows
        )
        log(buildLogMessage(rowCount))
        appendResult(
            if (renderedTable.isNotEmpty()) {
                renderedTable
            } else {
                context.getString(Strings.dev_options_plugin_db_no_data)
            }
        )
    }

    Scaffold(
        topBar = {
            TinaTopBar(
                title = stringResource(Strings.dev_options_plugin_db_test),
                onNavigateBack = onNavigateBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(Strings.dev_options_plugin_db_quick_actions),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = {
                            try {
                                val activeDatabase = databaseOrReport() ?: return@Button
                                ensureTestTable(activeDatabase)
                                log(context.getString(Strings.dev_options_plugin_db_table_created_log))
                                appendResult(context.getString(Strings.dev_options_plugin_db_table_created_result))
                            } catch (e: Exception) {
                                val message = errorMessage(e)
                                log(message)
                                appendResult(message)
                            }
                        }) {
                            Text(stringResource(Strings.dev_options_plugin_db_create_table))
                        }

                        Button(onClick = {
                            try {
                                val activeDatabase = databaseOrReport() ?: return@Button
                                ensureTestTable(activeDatabase)
                                val title = context.getString(
                                    Strings.dev_options_plugin_db_task_title,
                                    System.currentTimeMillis() % 10000
                                )
                                activeDatabase.execSQL(
                                    "INSERT INTO test_todos (title, completed) VALUES (?, ?)",
                                    arrayOf<Any>(title, 0)
                                )
                                val message = context.getString(
                                    Strings.dev_options_plugin_db_inserted,
                                    title
                                )
                                log(message)
                                appendResult(message)
                            } catch (e: Exception) {
                                val message = errorMessage(e)
                                log(message)
                                appendResult(message)
                            }
                        }) {
                            Text(stringResource(Strings.dev_options_plugin_db_insert))
                        }

                        Button(onClick = {
                            try {
                                val activeDatabase = databaseOrReport() ?: return@Button
                                ensureTestTable(activeDatabase)
                                executeSelectQuery(
                                    sql = "SELECT * FROM test_todos ORDER BY id DESC LIMIT 10",
                                    buildLogMessage = { count ->
                                        context.getString(
                                            Strings.dev_options_plugin_db_query_rows,
                                            count
                                        )
                                    }
                                )
                            } catch (e: Exception) {
                                val message = errorMessage(e)
                                log(message)
                                appendResult(message)
                            }
                        }) {
                            Text(stringResource(Strings.dev_options_plugin_db_query))
                        }

                        OutlinedButton(onClick = {
                            try {
                                val activeDatabase = databaseOrReport() ?: return@OutlinedButton
                                activeDatabase.execSQL("DROP TABLE IF EXISTS test_todos")
                                log(context.getString(Strings.dev_options_plugin_db_table_dropped_log))
                                appendResult(context.getString(Strings.dev_options_plugin_db_table_dropped_result))
                            } catch (e: Exception) {
                                val message = errorMessage(e)
                                log(message)
                                appendResult(message)
                            }
                        }) {
                            Text(stringResource(Strings.dev_options_plugin_db_drop_table))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = {
                            try {
                                val activeDatabase = databaseOrReport() ?: return@Button
                                ensureTestTable(activeDatabase)
                                activeDatabase.beginTransaction()
                                try {
                                    for (i in 1..5) {
                                        activeDatabase.execSQL(
                                            "INSERT INTO test_todos (title) VALUES (?)",
                                            arrayOf(context.getString(Strings.dev_options_plugin_db_batch_task_title, i))
                                        )
                                    }
                                    activeDatabase.setTransactionSuccessful()
                                    log(context.getString(Strings.dev_options_plugin_db_transaction_inserted_log))
                                    appendResult(
                                        context.getString(
                                            Strings.dev_options_plugin_db_transaction_inserted_result
                                        )
                                    )
                                } finally {
                                    activeDatabase.endTransaction()
                                }
                            } catch (e: Exception) {
                                val message = context.getString(
                                    Strings.dev_options_plugin_db_transaction_error,
                                    e.message ?: e.javaClass.simpleName
                                )
                                log(message)
                                appendResult(message)
                            }
                        }) {
                            Text(stringResource(Strings.dev_options_plugin_db_transaction))
                        }

                        OutlinedButton(onClick = {
                            try {
                                val activeDatabase = databaseOrReport() ?: return@OutlinedButton
                                ensureTestTable(activeDatabase)
                                activeDatabase.execSQL("DELETE FROM test_todos")
                                val message = context.getString(Strings.dev_options_plugin_db_all_data_deleted)
                                log(message)
                                appendResult(message)
                            } catch (e: Exception) {
                                val message = errorMessage(e)
                                log(message)
                                appendResult(message)
                            }
                        }) {
                            Text(stringResource(Strings.dev_options_plugin_db_clear))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(Strings.dev_options_plugin_db_custom_sql),
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = sqlInput,
                        onValueChange = { sqlInput = it },
                        label = { Text(stringResource(Strings.dev_options_plugin_db_sql_label)) },
                        minLines = 3,
                        maxLines = 5
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            try {
                                val activeDatabase = databaseOrReport() ?: return@Button
                                val sql = sqlInput.trim()
                                if (PluginDatabaseTestScreenSupport.isSelectQuery(sql)) {
                                    executeSelectQuery(
                                        sql = sql,
                                        buildLogMessage = { count ->
                                            context.getString(
                                                Strings.dev_options_plugin_db_query_rows,
                                                count
                                            )
                                        }
                                    )
                                } else {
                                    activeDatabase.execSQL(sql)
                                    log(context.getString(Strings.dev_options_plugin_db_sql_executed, sql))
                                    appendResult(
                                        context.getString(
                                            Strings.dev_options_plugin_db_sql_executed_success
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                val detail = e.message ?: e.javaClass.simpleName
                                val message = context.getString(
                                    Strings.dev_options_plugin_db_sql_error,
                                    detail
                                )
                                log(message)
                                appendResult(message)
                            }
                        }) {
                            Text(stringResource(Strings.dev_options_plugin_db_execute))
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(Strings.dev_options_plugin_db_result),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = resultText.ifEmpty { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(Strings.dev_options_plugin_db_log),
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedButton(onClick = { logText = "" }) {
                            Text(stringResource(Strings.dev_options_plugin_db_clear_log))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = logText.ifEmpty { "-" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun appendLog(current: String, msg: String): String {
    val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    return PluginDatabaseTestScreenSupport.appendLog(
        current = current,
        msg = msg,
        timestamp = timestamp
    )
}

private fun ensureTestTable(database: SQLiteDatabase) {
    database.execSQL(
        """
            CREATE TABLE IF NOT EXISTS test_todos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                completed INTEGER DEFAULT 0,
                created_at INTEGER DEFAULT (strftime('%s', 'now'))
            )
        """.trimIndent()
    )
}

private data class PluginDatabaseQuerySnapshot(
    val columnNames: List<String>,
    val rows: List<List<String>>
)

private fun Cursor.toQuerySnapshot(): PluginDatabaseQuerySnapshot {
    val rows = mutableListOf<List<String>>()
    while (moveToNext()) {
        rows += (0 until columnCount).map(::readCellText)
    }
    return PluginDatabaseQuerySnapshot(
        columnNames = columnNames.toList(),
        rows = rows
    )
}

private fun Cursor.readCellText(index: Int): String = when (getType(index)) {
    Cursor.FIELD_TYPE_NULL -> "NULL"
    Cursor.FIELD_TYPE_INTEGER -> getLong(index).toString()
    Cursor.FIELD_TYPE_FLOAT -> getDouble(index).toString()
    else -> getString(index) ?: "NULL"
}

private class TestDatabaseHelper(
    context: android.content.Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, 1) {
    override fun onCreate(db: SQLiteDatabase) = Unit

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        const val DATABASE_NAME = "plugin_test_db.db"
    }
}
