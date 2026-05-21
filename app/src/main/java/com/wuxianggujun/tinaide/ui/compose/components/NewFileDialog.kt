package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wuxianggujun.tinaide.core.common.simplifyPath
import com.wuxianggujun.tinaide.core.i18n.Strings
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 文件类型枚举
 */
enum class FileType(
    val icon: ImageVector,
    val iconColor: Color,
    val extensions: List<String>,
    val generatesPair: Boolean = false
) {
    CPP_CLASS(
        icon = Icons.Default.Code,
        iconColor = Color(0xFF00599C),
        extensions = listOf("h", "cpp"),
        generatesPair = true
    ),
    CPP_SOURCE(
        icon = Icons.Default.Functions,
        iconColor = Color(0xFF00599C),
        extensions = listOf("cpp")
    ),
    C_SOURCE(
        icon = Icons.Default.Functions,
        iconColor = Color(0xFF00599C),
        extensions = listOf("c")
    ),
    HEADER(
        icon = Icons.Default.Description,
        iconColor = Color(0xFF9C27B0),
        extensions = listOf("h")
    ),
    PLAIN_FILE(
        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
        iconColor = Color(0xFF607D8B),
        extensions = emptyList()
    ),
    TEXT_FILE(
        icon = Icons.AutoMirrored.Filled.TextSnippet,
        iconColor = Color(0xFF607D8B),
        extensions = listOf("txt")
    );

    @Composable
    fun getLocalizedDisplayName(): String = when (this) {
        CPP_CLASS -> stringResource(Strings.file_type_cpp_class)
        CPP_SOURCE -> stringResource(Strings.file_type_cpp_source)
        C_SOURCE -> stringResource(Strings.file_type_c_source)
        HEADER -> stringResource(Strings.file_type_header)
        PLAIN_FILE -> stringResource(Strings.file_type_plain)
        TEXT_FILE -> stringResource(Strings.file_type_text)
    }

    @Composable
    fun getLocalizedDescription(): String = when (this) {
        CPP_CLASS -> stringResource(Strings.file_type_cpp_class_desc)
        CPP_SOURCE -> stringResource(Strings.file_type_cpp_source_desc)
        C_SOURCE -> stringResource(Strings.file_type_c_source_desc)
        HEADER -> stringResource(Strings.file_type_header_desc)
        PLAIN_FILE -> stringResource(Strings.file_type_plain_desc)
        TEXT_FILE -> stringResource(Strings.file_type_text_desc)
    }
}

/**
 * 新建文件的结果
 */
data class NewFileResult(
    val files: List<File>,
    val openFile: File?
)

/**
 * 新建文件对话框
 */
@Composable
fun NewFileDialog(
    targetDir: File,
    onDismiss: () -> Unit,
    onConfirm: (NewFileResult) -> Unit
) {
    var selectedType by remember { mutableStateOf(FileType.CPP_CLASS) }
    var fileName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val targetDirDisplay = simplifyPath(targetDir.absolutePath, context)
    val previewItems = remember(selectedType, fileName) {
        buildPreviewNames(fileName.trim(), selectedType)
    }

    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val errorNameEmpty = stringResource(Strings.error_name_empty)
    val errorNameInvalidChars = stringResource(Strings.error_name_invalid_chars)
    val errorFilenameInvalidChars = stringResource(Strings.error_filename_invalid_chars)
    val errorFileAlreadyExistsTemplate = stringResource(Strings.error_file_already_exists)
    val errorCreateFile = stringResource(Strings.error_create_file)

    fun submit() {
        keyboardController?.hide()
        val trimmedName = fileName.trim()
        val validationError = validateNewFileName(
            name = trimmedName,
            type = selectedType,
            targetDir = targetDir,
            errorNameEmpty = errorNameEmpty,
            errorNameInvalidChars = errorNameInvalidChars,
            errorFilenameInvalidChars = errorFilenameInvalidChars,
            errorFileAlreadyExistsTemplate = errorFileAlreadyExistsTemplate
        )
        if (validationError != null) {
            errorMessage = validationError
            return
        }

        val result = createFiles(targetDir, trimmedName, selectedType)
        if (result != null) {
            onConfirm(result)
        } else {
            errorMessage = errorCreateFile
        }
    }

    TinaAlertDialog(
        onDismissRequest = onDismiss,
        title = { TinaDialogTitleText(stringResource(Strings.new_file_title)) },
        confirmButton = {
            TinaPrimaryButton(
                text = stringResource(Strings.btn_create),
                onClick = ::submit
            )
        },
        dismissButton = {
            TinaTextButton(
                text = stringResource(Strings.btn_cancel),
                onClick = onDismiss
            )
        },
        text = {
            TinaDialogContentColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CreationTargetSection(
                    title = stringResource(Strings.label_target_directory),
                    value = targetDirDisplay
                )

                Text(
                    text = stringResource(Strings.new_file_type),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )

                FileTypeGrid(
                    selectedType = selectedType,
                    onSelect = {
                        selectedType = it
                        errorMessage = null
                    }
                )

                val label = when (selectedType) {
                    FileType.CPP_CLASS -> stringResource(Strings.new_file_class_name)
                    FileType.PLAIN_FILE -> stringResource(Strings.new_file_name_with_ext)
                    else -> stringResource(Strings.new_file_name_without_ext)
                }

                val placeholder = when (selectedType) {
                    FileType.CPP_CLASS -> stringResource(Strings.new_file_example_class)
                    FileType.CPP_SOURCE -> stringResource(Strings.new_file_example_cpp)
                    FileType.C_SOURCE -> stringResource(Strings.new_file_example_c)
                    FileType.HEADER -> stringResource(Strings.new_file_example_header)
                    FileType.PLAIN_FILE -> stringResource(Strings.new_file_example_plain)
                    FileType.TEXT_FILE -> stringResource(Strings.new_file_example_text)
                }

                OutlinedTextField(
                    value = fileName,
                    onValueChange = {
                        fileName = it
                        errorMessage = null
                    },
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )

                if (previewItems.isNotEmpty()) {
                    CreationPreviewSection(items = previewItems)
                }
            }
        }
    )
}

@Composable
internal fun CreationTargetSection(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        TinaDialogCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 14.dp,
                vertical = 12.dp
            ),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun CreationPreviewSection(
    items: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(Strings.label_creation_preview),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        TinaDialogCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 14.dp,
                vertical = 12.dp
            ),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                Text(
                    text = "\u2022 $item",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun FileTypeGrid(
    selectedType: FileType,
    onSelect: (FileType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FileType.entries.chunked(2).forEach { rowTypes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowTypes.forEach { type ->
                    FileTypeCard(
                        type = type,
                        selected = selectedType == type,
                        onSelect = { onSelect(type) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (rowTypes.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FileTypeCard(
    type: FileType,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .heightIn(min = 108.dp)
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(TinaShapes.CardCorner),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = type.icon,
                    contentDescription = null,
                    tint = type.iconColor
                )
                Spacer(modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = type.getLocalizedDisplayName(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = type.getLocalizedDescription(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun validateNewFileName(
    name: String,
    type: FileType,
    targetDir: File,
    errorNameEmpty: String,
    errorNameInvalidChars: String,
    errorFilenameInvalidChars: String,
    errorFileAlreadyExistsTemplate: String
): String? = when {
    name.isEmpty() -> errorNameEmpty
    type != FileType.PLAIN_FILE && !name.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*")) ->
        errorNameInvalidChars
    type == FileType.PLAIN_FILE && !name.matches(Regex("[a-zA-Z0-9_.-]+")) ->
        errorFilenameInvalidChars
    else -> {
        val existingFiles = getFilesToCreate(targetDir, name, type).filter { it.exists() }
        if (existingFiles.isNotEmpty()) {
            String.format(
                Locale.getDefault(),
                errorFileAlreadyExistsTemplate,
                existingFiles.joinToString { it.name }
            )
        } else {
            null
        }
    }
}

private fun buildPreviewNames(
    name: String,
    type: FileType
): List<String> {
    if (name.isBlank()) return emptyList()
    return when (type) {
        FileType.CPP_CLASS -> listOf("$name.h", "$name.cpp")
        FileType.PLAIN_FILE -> listOf(name)
        else -> type.extensions.map { ext -> "$name.$ext" }
    }
}

private fun getFilesToCreate(dir: File, name: String, type: FileType): List<File> = when (type) {
    FileType.CPP_CLASS -> listOf(
        File(dir, "$name.h"),
        File(dir, "$name.cpp")
    )
    FileType.PLAIN_FILE -> listOf(File(dir, name))
    else -> type.extensions.map { ext -> File(dir, "$name.$ext") }
}

private fun createFiles(dir: File, name: String, type: FileType): NewFileResult? = try {
    val files = mutableListOf<File>()
    var openFile: File? = null

    when (type) {
        FileType.CPP_CLASS -> {
            val headerFile = File(dir, "$name.h")
            headerFile.writeText(generateHeaderContent(name))
            files.add(headerFile)

            val sourceFile = File(dir, "$name.cpp")
            sourceFile.writeText(generateCppClassContent(name))
            files.add(sourceFile)

            openFile = headerFile
        }

        FileType.CPP_SOURCE -> {
            val file = File(dir, "$name.cpp")
            file.writeText(generateCppSourceContent())
            files.add(file)
            openFile = file
        }

        FileType.C_SOURCE -> {
            val file = File(dir, "$name.c")
            file.writeText(generateCSourceContent())
            files.add(file)
            openFile = file
        }

        FileType.HEADER -> {
            val file = File(dir, "$name.h")
            file.writeText(generateHeaderContent(name))
            files.add(file)
            openFile = file
        }

        FileType.PLAIN_FILE -> {
            val file = File(dir, name)
            file.createNewFile()
            files.add(file)
            openFile = file
        }

        FileType.TEXT_FILE -> {
            val file = File(dir, "$name.txt")
            file.createNewFile()
            files.add(file)
            openFile = file
        }
    }

    NewFileResult(files = files, openFile = openFile)
} catch (_: Exception) {
    null
}

private fun generateHeaderContent(className: String): String {
    val guardName = "${className.uppercase()}_H"
    return """#ifndef $guardName
#define $guardName

class $className {
public:
    $className();
    ~$className();

private:

};

#endif // $guardName
"""
}

private fun generateCppClassContent(className: String): String = """#include "$className.h"

$className::$className() {
    // 构造函数
}

$className::~$className() {
    // 析构函数
}
"""

private fun generateCppSourceContent(): String = """#include <iostream>

"""

private fun generateCSourceContent(): String = """#include <stdio.h>

"""
