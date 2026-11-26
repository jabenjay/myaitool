package com.jaben.tools.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.jaben.tools.data.AppUpdateChecker
import com.jaben.tools.data.OutputStorageHelper
import com.jaben.tools.tools.qr.QrCodeHelper
import com.jaben.tools.tools.qr.QrDotStyle
import com.jaben.tools.ui.theme.LocalAppThemeVisuals
import com.jaben.tools.ui.theme.liquidGlassSurface
import com.jaben.tools.ui.theme.liquidPress
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

private const val ALIPAY_DONATE_URL = "https://qr.alipay.com/fkx19214trgp2tndpljhv7f"
private const val WECHAT_DONATE_URL = "https://payapp.wechatpay.cn/sjt/qr/AQGxLTUPxACTHPvYfBhlq6Nh"

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenRepoInBrowser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val visuals = LocalAppThemeVisuals.current
    val context = LocalContext.current
    val versionName = remember { AppUpdateChecker.getCurrentVersionName(context) }

    // 0 = 支付宝, 1 = 微信支付
    var selectedPayTab by rememberSaveable { mutableIntStateOf(0) }
    val isAlipay = selectedPayTab == 0
    val brandColor = if (isAlipay) Color(0xFF1677FF) else Color(0xFF07C160)
    val payTitle = if (isAlipay) "支付宝" else "微信支付"
    val payUrl = if (isAlipay) ALIPAY_DONATE_URL else WECHAT_DONATE_URL

    val qrBitmap = remember(selectedPayTab) {
        QrCodeHelper.generateQrBitmap(
            content = payUrl,
            sizePx = 720,
            fgColor = android.graphics.Color.BLACK,
            bgColor = android.graphics.Color.WHITE,
            ecc = ErrorCorrectionLevel.M,
            dotStyle = QrDotStyle.SQUARE
        )
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
                text = "关于",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = visuals.cardContentColor
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App & Repo Info Card
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Jools",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "v$versionName",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "轻量、纯粹的个人手机工具箱",
                        fontSize = 13.5.sp,
                        color = visuals.cardContentColor.copy(alpha = 0.72f)
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = visuals.cardContentColor.copy(alpha = 0.06f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onOpenRepoInBrowser(AppUpdateChecker.REPO_URL) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "开源地址",
                                    fontSize = 11.sp,
                                    color = visuals.cardContentColor.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = AppUpdateChecker.REPO_URL,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("repo", AppUpdateChecker.REPO_URL))
                                    Toast.makeText(context, "已复制开源地址", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "复制",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Donation Card
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "捐赠支持",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Alipay / WeChat Pay Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(visuals.cardContentColor.copy(alpha = 0.06f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            0 to ("支付宝" to Color(0xFF1677FF)),
                            1 to ("微信支付" to Color(0xFF07C160))
                        ).forEach { (idx, meta) ->
                            val (label, color) = meta
                            val selected = selectedPayTab == idx
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) color else Color.Transparent,
                                contentColor = if (selected) Color.White else visuals.cardContentColor,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedPayTab = idx }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 14.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Clean Pure QR Code (no avatar, no merchant name)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        border = BorderStroke(2.dp, brandColor.copy(alpha = 0.75f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "${payTitle}收款码",
                                modifier = Modifier.size(210.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = payTitle,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = brandColor
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val fileName = if (isAlipay) "Jools_Alipay_Donate.png" else "Jools_WeChat_Donate.png"
                                val baos = ByteArrayOutputStream()
                                qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                                val uri = OutputStorageHelper.saveStream(
                                    context = context,
                                    subFolder = OutputStorageHelper.SUB_PICTURES,
                                    fileName = fileName,
                                    mimeType = "image/png",
                                    inputStream = ByteArrayInputStream(baos.toByteArray())
                                )
                                if (uri != null) {
                                    val path = OutputStorageHelper.getDisplayPath(context, OutputStorageHelper.SUB_PICTURES)
                                    Toast.makeText(context, "已保存收款码到 $path", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "保存失败，请检查存储权限", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("保存收款码")
                        }

                        if (isAlipay) {
                            Button(
                                onClick = {
                                    val launched = runCatching {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("alipays://platformapi/startapp?saId=10000007&qrcode=${Uri.encode(ALIPAY_DONATE_URL)}")
                                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        context.startActivity(intent)
                                        true
                                    }.getOrDefault(false)
                                    if (!launched) {
                                        Toast.makeText(context, "未检测到支付宝，请扫码捐赠", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = brandColor),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("打开支付宝")
                            }
                        }
                    }
                }
            }
        }
    }
}
