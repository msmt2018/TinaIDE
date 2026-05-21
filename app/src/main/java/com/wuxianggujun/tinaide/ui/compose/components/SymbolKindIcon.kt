package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.wuxianggujun.tinaide.editor.symbol.SymbolKind
import java.util.Locale

internal fun symbolKindIcon(kind: SymbolKind): ImageVector = when (kind) {
    SymbolKind.Class,
    SymbolKind.Struct,
    SymbolKind.Enum,
    SymbolKind.Interface,
    SymbolKind.Trait,
    -> Icons.Default.Code

    SymbolKind.Namespace,
    SymbolKind.Module,
    -> Icons.Default.Source

    SymbolKind.Function,
    SymbolKind.Method,
    -> Icons.Default.Functions

    SymbolKind.Field,
    SymbolKind.Variable,
    SymbolKind.Constant,
    SymbolKind.Property,
    -> Icons.Default.Tag
}

internal fun lspSymbolKindIcon(
    kindValue: Int?,
    kindName: String?,
): ImageVector = when (kindValue) {
    1 -> Icons.AutoMirrored.Filled.InsertDriveFile // File
    2 -> Icons.Default.Source // Module
    3 -> Icons.Default.Source // Namespace
    4 -> Icons.Default.Folder // Package

    5 -> Icons.Default.Code // Class
    6 -> Icons.Default.Functions // Method
    7 -> Icons.Default.Tag // Property
    8 -> Icons.Default.Tag // Field
    9 -> Icons.Default.Functions // Constructor
    10 -> Icons.Default.Code // Enum
    11 -> Icons.Default.Code // Interface
    12 -> Icons.Default.Functions // Function
    13 -> Icons.Default.Tag // Variable
    14 -> Icons.Default.Tag // Constant
    15 -> Icons.Default.Description // String
    16 -> Icons.Default.Description // Number
    17 -> Icons.Default.Description // Boolean
    18 -> Icons.Default.Description // Array
    19 -> Icons.Default.Description // Object
    20 -> Icons.Default.Tag // Key
    21 -> Icons.Default.Description // Null
    22 -> Icons.Default.Tag // EnumMember
    23 -> Icons.Default.Code // Struct
    24 -> Icons.Default.Warning // Event
    25 -> Icons.Default.Build // Operator
    26 -> Icons.Default.Code // TypeParameter
    else -> symbolKindIcon(kindName)
}

internal fun symbolKindIcon(kindName: String?): ImageVector {
    val normalized = kindName
        ?.trim()
        ?.lowercase(Locale.US)
        ?: return Icons.Default.Code

    return when (normalized) {
        "file" -> Icons.AutoMirrored.Filled.InsertDriveFile
        "module", "namespace" -> Icons.Default.Source
        "package" -> Icons.Default.Folder

        "class", "struct", "interface", "enum", "object", "typeparameter" -> Icons.Default.Code

        "method", "constructor", "function" -> Icons.Default.Functions

        "property", "field", "variable", "constant", "enummember", "key" -> Icons.Default.Tag

        "string", "number", "boolean", "null", "array" -> Icons.Default.Description

        "event" -> Icons.Default.Warning
        "operator" -> Icons.Default.Build
        else -> Icons.Default.Code
    }
}
