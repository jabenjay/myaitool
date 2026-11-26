package com.toolbox.pocket.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.toolbox.pocket.ui.components.SmoothCollapsingHeader
import com.toolbox.pocket.ui.components.rememberSmoothHeaderScrollState
import com.toolbox.pocket.ui.theme.LocalAppThemeVisuals
import com.toolbox.pocket.ui.theme.liquidGlassSurface
import com.toolbox.pocket.ui.theme.liquidPress

data class ToolboxModule(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
fun ToolboxHomeScreen(
    homeColumns: Int,
    toolOrder: List<String>,
    bottomExtraPadding: Dp = 0.dp,
    onMoveTool: (Int, Int) -> Unit,
    onOpenTool: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val headerState = rememberSmoothHeaderScrollState()
    val visuals = LocalAppThemeVisuals.current
    val haptic = LocalHapticFeedback.current
    val columns = homeColumns.coerceIn(1, 4)

    val catalog = remember {
        mapOf(
            "browser" to ToolboxModule(
                id = "browser",
                title = "极简浏览器",
                subtitle = "轻量网页浏览，支持应用内代理",
                icon = Icons.Rounded.Public
            ),
            "base64" to ToolboxModule(
                id = "base64",
                title = "Base64 编解码",
                subtitle = "长文本分段换行、行号定位与双指缩放",
                icon = Icons.Rounded.Code
            )
        )
    }

    val orderedTools = remember(toolOrder) {
        val list = toolOrder.mapNotNull { catalog[it] }.toMutableList()
        catalog.values.forEach { item ->
            if (list.none { it.id == item.id }) list.add(item)
        }
        list.toList()
    }

    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }

    val latestTools by rememberUpdatedState(orderedTools)
    val latestOnMove by rememberUpdatedState(onMoveTool)

    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(headerState.nestedScrollConnection)
    ) {
        SmoothCollapsingHeader(
            title = "工具箱",
            collapseProgress = headerState.progress
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            userScrollEnabled = draggedId == null,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 16.dp + bottomExtraPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(if (columns >= 3) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (columns >= 3) 10.dp else 12.dp)
        ) {
            itemsIndexed(orderedTools, key = { _, item -> item.id }) { _, tool ->
                val interactionSource = remember { MutableInteractionSource() }
                val corner = if (columns >= 3) 20.dp else 26.dp
                val isDragging = draggedId == tool.id

                val floatScale by animateFloatAsState(
                    targetValue = if (isDragging) 1.04f else 1f,
                    animationSpec = spring(
                        dampingRatio = 0.68f,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "toolFloatScale"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 10f else 0f)
                        .then(if (!isDragging) Modifier.animateItem() else Modifier)
                        .onGloballyPositioned { coords ->
                            itemBounds[tool.id] = coords.boundsInParent()
                        }
                        .pointerInput(tool.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedId = tool.id
                                    dragOffset = Offset.Zero
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = {
                                    draggedId = null
                                    dragOffset = Offset.Zero
                                },
                                onDragCancel = {
                                    draggedId = null
                                    dragOffset = Offset.Zero
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount

                                    val currentRect = itemBounds[tool.id] ?: return@detectDragGesturesAfterLongPress
                                    val draggedCenter = currentRect.center + dragOffset
                                    val currentList = latestTools
                                    val fromIndex = currentList.indexOfFirst { it.id == tool.id }
                                    if (fromIndex == -1) return@detectDragGesturesAfterLongPress

                                    val targetEntry = itemBounds.entries.firstOrNull { (otherId, rect) ->
                                        otherId != tool.id && rect.contains(draggedCenter)
                                    }
                                    if (targetEntry != null) {
                                        val toIndex = currentList.indexOfFirst { it.id == targetEntry.key }
                                        if (toIndex != -1 && toIndex != fromIndex) {
                                            latestOnMove(fromIndex, toIndex)
                                            dragOffset += (currentRect.topLeft - targetEntry.value.topLeft)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                Card(
                    onClick = { if (draggedId == null) onOpenTool(tool.id) },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(corner),
                    colors = CardDefaults.cardColors(
                        containerColor = visuals.cardContainerColor,
                        contentColor = visuals.cardContentColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            if (isDragging) {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                            }
                            scaleX = floatScale
                            scaleY = floatScale
                        }
                        .liquidPress(interactionSource, pressedScale = 0.96f)
                        .clip(RoundedCornerShape(corner))
                        .liquidGlassSurface(corner)
                ) {
                    if (columns == 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.title,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(13.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tool.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = visuals.cardContentColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = tool.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = if (columns == 4) 8.dp else 14.dp,
                                    vertical = if (columns == 4) 14.dp else 16.dp
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = RoundedCornerShape(if (columns >= 3) 14.dp else 16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(if (columns == 4) 42.dp else 48.dp)
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.title,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(if (columns == 4) 10.dp else 12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(if (columns == 4) 8.dp else 10.dp))

                            Text(
                                text = tool.title,
                                style = if (columns >= 3) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = visuals.cardContentColor,
                                textAlign = TextAlign.Center,
                                maxLines = if (columns >= 3) 2 else 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (columns == 2) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = tool.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}
