package com.jaben.tools.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.jaben.tools.data.NoticeType
import com.jaben.tools.data.NoticeDonationConfig
import com.jaben.tools.data.PendingNoticeItem
import com.jaben.tools.tools.qr.QrCodeHelper
import com.jaben.tools.tools.qr.QrDotStyle
import com.jaben.tools.tools.qr.QrEccLevel

private const val NOTICE_ALIPAY_URL = "https://qr.alipay.com/fkx19214trgp2tndpljhv7f"
private const val NOTICE_WECHAT_URL = "https://payapp.wechatpay.cn/sjt/qr/AQGxLTUPxACTHPvYfBhlq6Nh"

@Composable
fun NoticeConfirmDialog(
    item: PendingNoticeItem,
    onAcknowledge: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var hasReachedBottom by remember(item.contentHash) { mutableStateOf(false) }

    val donationCfg = remember(item) {
        if (item.type == NoticeType.USAGE_NOTICE) (item.donationConfig ?: NoticeDonationConfig()) else null
    }
    val alipayQr = remember(donationCfg?.alipayUrl) {
        val url = donationCfg?.alipayUrl?.trim().orEmpty()
        if (donationCfg?.enabled == true && url.isNotEmpty()) {
            QrCodeHelper.generateStyledQrBitmap(
                content = url,
                sizePx = 420,
                fgColor = android.graphics.Color.BLACK,
                bgColor = android.graphics.Color.WHITE,
                dotStyle = QrDotStyle.SQUARE,
                eccLevel = QrEccLevel.M
            )
        } else null
    }
    val wechatQr = remember(donationCfg?.wechatUrl) {
        val url = donationCfg?.wechatUrl?.trim().orEmpty()
        if (donationCfg?.enabled == true && url.isNotEmpty()) {
            QrCodeHelper.generateStyledQrBitmap(
                content = url,
                sizePx = 420,
                fgColor = android.graphics.Color.BLACK,
                bgColor = android.graphics.Color.WHITE,
                dotStyle = QrDotStyle.SQUARE,
                eccLevel = QrEccLevel.M
            )
        } else null
    }

    LaunchedEffect(scrollState.value, scrollState.maxValue, item.contentHash) {
        if (scrollState.maxValue <= 0 || scrollState.value >= scrollState.maxValue - 16) {
            hasReachedBottom = true
        }
    }

    AlertDialog(
        onDismissRequest = { /* Non-dismissible until scrolled to bottom & acknowledged */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = item.title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(scrollState)
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item.content.lines().forEach { rawLine ->
                    val line = rawLine.trim()
                    when {
                        line.isEmpty() -> {}
                        line.startsWith("# ") -> {
                            Text(
                                text = line.removePrefix("# ").trim(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        line.startsWith("## ") -> {
                            Text(
                                text = line.removePrefix("## ").trim(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        line.startsWith("- ") -> {
                            Text(
                                text = "• ${line.removePrefix("- ").replace("**", "").trim()}",
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            Text(
                                text = line.replace("**", ""),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (donationCfg != null && donationCfg.enabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = donationCfg.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (donationCfg.description.isNotBlank()) {
                        Text(
                            text = donationCfg.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.5.dp, Color(0xFF1677FF).copy(alpha = 0.65f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                alipayQr?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "支付宝收款码",
                                        modifier = Modifier.size(104.dp)
                                    )
                                }
                                Text(
                                    text = "支付宝",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1677FF)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White,
                            border = BorderStroke(1.5.dp, Color(0xFF07C160).copy(alpha = 0.65f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                wechatQr?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "微信收款码",
                                        modifier = Modifier.size(104.dp)
                                    )
                                }
                                Text(
                                    text = "微信支付",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF07C160)
                                )
                            }
                        }
                    }
                    if (donationCfg.alipayUrl.isNotBlank()) {
                        Text(
                            text = donationCfg.alipayLinkText,
                            fontSize = 12.sp,
                            color = Color(0xFF1677FF),
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .clickable {
                                    val launched = runCatching {
                                        val intent = Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("alipays://platformapi/startapp?saId=10000007&qrcode=${Uri.encode(donationCfg.alipayUrl)}")
                                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    context.startActivity(intent)
                                    true
                                }.getOrDefault(false)
                                    if (!launched) {
                                        Toast.makeText(context, "未安装支付宝", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = hasReachedBottom,
                onClick = onAcknowledge,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (hasReachedBottom) "我已知晓" else "请滑动阅读至末尾",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}
