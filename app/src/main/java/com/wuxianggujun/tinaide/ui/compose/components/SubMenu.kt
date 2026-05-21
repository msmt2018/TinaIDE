package com.wuxianggujun.tinaide.ui.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * 带二级子菜单的菜单项
 *
 * @param text 菜单项文本
 * @param leadingIcon 左侧图标（可选）
 * @param enabled 是否启用
 * @param content 子菜单内容
 */
@Composable
fun SubMenuItem(
    text: String,
    leadingIcon: Painter? = null,
    enabled: Boolean = true,
    onParentDismiss: () -> Unit,
    content: @Composable SubMenuScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val scope = remember(onParentDismiss) {
        SubMenuScopeImpl(
            dismissSubMenu = { expanded = false },
            dismissAll = {
                expanded = false
                onParentDismiss()
            }
        )
    }

    Box {
        TinaDropdownMenuItem(
            text = { Text(text) },
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            leadingIcon = leadingIcon?.let { icon ->
                {
                    Icon(
                        painter = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        TinaDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(x = 180.dp, y = (-48).dp)
        ) {
            scope.content()
        }
    }
}

/**
 * 子菜单作用域，提供关闭菜单的方法
 */
interface SubMenuScope {
    /**
     * 仅关闭当前子菜单
     */
    fun dismissSubMenu()

    /**
     * 关闭所有菜单（子菜单和父菜单）
     */
    fun dismissAll()
}

private class SubMenuScopeImpl(
    private val dismissSubMenu: () -> Unit,
    private val dismissAll: () -> Unit
) : SubMenuScope {
    override fun dismissSubMenu() = dismissSubMenu.invoke()
    override fun dismissAll() = dismissAll.invoke()
}

/**
 * 子菜单中的菜单项
 */
@Composable
fun SubMenuScope.SubMenuDropdownItem(
    text: String,
    onClick: () -> Unit,
    leadingIcon: Painter? = null,
    enabled: Boolean = true,
    dismissOnClick: Boolean = true
) {
    TinaDropdownMenuItem(
        text = { Text(text) },
        onClick = {
            onClick()
            if (dismissOnClick) {
                dismissAll()
            }
        },
        enabled = enabled,
        leadingIcon = leadingIcon?.let { icon ->
            {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
}
