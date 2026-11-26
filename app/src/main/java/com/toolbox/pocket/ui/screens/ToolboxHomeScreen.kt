package com.toolbox.pocket.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.toolbox.pocket.ui.theme.LocalAppThemeVisuals
import com.toolbox.pocket.ui.theme.liquidGlassSurface
import com.toolbox.pocket.ui.theme.liquidPress

data class ToolboxModule(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxHomeScreen(
    homeColumns: Int,
    bottomExtraPadding: Dp = 0.dp,
    onOpenTool: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val visuals = LocalAppThemeVisuals.current
    val titleFontSize = lerp(24.sp, 18.sp, scrollBehavior.state.collapsedFraction)
    val columns = homeColumns.coerceIn(1, 4)

    val tools = listOf(
        ToolboxModule(
            id = "base64",
            title = "Base64 编解码",
            subtitle = "长文本分段换行、行号定位与双指缩放",
            icon = Icons.Rounded.Code
        ),
        ToolboxModule(
            id = "browser",
            title = "极简浏览器",
            subtitle = "轻量网页浏览，支持应用内代理",
            icon = Icons.Rounded.Public
        )
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            MediumTopAppBar(
                title = {
                    Text(
                        text = "工具箱",
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Bold
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
                        alpha = if (visuals.isLiquidGlass || visuals.hasCustomBackground) 0.85f else 1f
                    )
                )
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 16.dp + bottomExtraPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(if (columns >= 3) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (columns >= 3) 10.dp else 12.dp)
        ) {
            items(tools, key = { it.id }) { tool ->
                val interactionSource = remember { MutableInteractionSource() }
                val corner = if (columns >= 3) 20.dp else 26.dp

                Card(
                    onClick = { onOpenTool(tool.id) },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(corner),
                    colors = CardDefaults.cardColors(
                        containerColor = visuals.cardContainerColor,
                        contentColor = visuals.cardContentColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidPress(interactionSource, pressedScale = 0.95f)
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
                                    color = MaterialTheme.colorScheme.onSurface
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
                                color = MaterialTheme.colorScheme.onSurface,
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
