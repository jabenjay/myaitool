package com.jaben.tools.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaben.tools.data.AppSettings
import com.jaben.tools.ui.theme.LocalAppThemeVisuals
import com.jaben.tools.ui.theme.liquidGlassSurface
import com.jaben.tools.ui.theme.liquidPress

@Composable
fun StorageSettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onSelectOutputRoot: (Uri, String) -> Unit,
    onResetOutputRoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalAppThemeVisuals.current
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val rawSeg = treeUri.lastPathSegment.orEmpty()
            val friendly = rawSeg
                .removePrefix("primary:")
                .replace(":", "/")
                .ifBlank { "自定义文件夹" }
            onSelectOutputRoot(treeUri, friendly)
            Toast.makeText(context, "保存主路径已设为: $friendly", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.liquidPress()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = visuals.cardContentColor
                )
            }
            Text(
                text = "保存路径",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = visuals.cardContentColor
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = visuals.cardContainerColor,
                contentColor = visuals.cardContentColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (visuals.isLiquidGlass) Modifier.liquidGlassSurface(22.dp) else Modifier)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "当前输出主路径",
                        fontSize = 13.sp,
                        color = visuals.cardContentColor.copy(alpha = 0.65f)
                    )
                    Text(
                        text = settings.outputRootLabel,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "各功能输出文件将自动分类保存到以下子目录：",
                        fontSize = 13.sp,
                        color = visuals.cardContentColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "• ${settings.outputRootLabel}/pictures  (图片、二维码)\n" +
                                "• ${settings.outputRootLabel}/videos    (视频、动图)\n" +
                                "• ${settings.outputRootLabel}/sounds    (音频)\n" +
                                "• ${settings.outputRootLabel}/files     (压缩归档文件)",
                        fontSize = 12.5.sp,
                        lineHeight = 20.sp,
                        color = visuals.cardContentColor.copy(alpha = 0.72f)
                    )
                }
            }

            Button(
                onClick = { folderPicker.launch(null) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .liquidPress()
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("选择其他主文件夹", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            if (settings.outputRootUri != null) {
                OutlinedButton(
                    onClick = {
                        onResetOutputRoot()
                        Toast.makeText(context, "已恢复默认路径: Download/Jools", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("恢复默认 (Download/Jools)")
                }
            }
        }
    }
}
