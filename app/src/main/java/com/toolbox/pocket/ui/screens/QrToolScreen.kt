package com.toolbox.pocket.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.toolbox.pocket.tools.qr.QrCodeHelper
import com.toolbox.pocket.tools.qr.QrDotStyle
import com.toolbox.pocket.tools.qr.QrEccLevel
import com.toolbox.pocket.ui.theme.LocalAppThemeVisuals
import com.toolbox.pocket.ui.theme.liquidGlassSurface
import com.toolbox.pocket.ui.theme.liquidPress
import kotlinx.coroutines.launch

private enum class QrTab(val label: String) {
    GENERATE("生成与美化"),
    DECODE("识别解码")
}

private enum class QrTemplate(val label: String) {
    TEXT("文本 / 网址"),
    WIFI("Wi-Fi"),
    VCARD("名片"),
    PHONE("电话")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrToolScreen(
    onOpenCameraScan: () -> Unit,
    onOpenInBrowser: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val visuals = LocalAppThemeVisuals.current
    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Use rememberSaveable so returning from Camera/Gallery stays on the active tab!
    var currentTab by rememberSaveable { mutableStateOf(QrTab.GENERATE) }
    var template by rememberSaveable { mutableStateOf(QrTemplate.TEXT) }

    var plainInput by rememberSaveable { mutableStateOf("https://www.baidu.com") }
    var wifiSsid by rememberSaveable { mutableStateOf("") }
    var wifiPass by rememberSaveable { mutableStateOf("") }
    var wifiAuth by rememberSaveable { mutableStateOf("WPA") }
    var cardName by rememberSaveable { mutableStateOf("") }
    var cardPhone by rememberSaveable { mutableStateOf("") }
    var cardOrg by rememberSaveable { mutableStateOf("") }
    var phoneInput by rememberSaveable { mutableStateOf("") }

    val fgPalette = listOf(
        Color(0xFF000000),
        Color(0xFF0A4DA6),
        Color(0xFF0F766E),
        Color(0xFF6D28D9),
        Color(0xFFC2410C)
    )
    val bgPalette = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFFDF8F0),
        Color(0xFFEEF6FF),
        Color(0xFFF3E8FF)
    )
    var fgIdx by rememberSaveable { mutableIntStateOf(0) }
    var bgIdx by rememberSaveable { mutableIntStateOf(0) }
    var dotStyle by rememberSaveable { mutableStateOf(QrDotStyle.ROUNDED) }
    var eccLevel by rememberSaveable { mutableStateOf(QrEccLevel.M) }
    var caption by rememberSaveable { mutableStateOf("") }

    var decodedText by rememberSaveable { mutableStateOf("") }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val res = QrCodeHelper.decodeFromUri(context, uri)
            if (!res.isNullOrBlank()) {
                decodedText = res
            } else {
                scope.launch { snackbarHostState.showSnackbar("未在图片中识别到二维码") }
            }
        }
    }

    val qrPayload = remember(
        template, plainInput, wifiSsid, wifiPass, wifiAuth, cardName, cardPhone, cardOrg, phoneInput
    ) {
        when (template) {
            QrTemplate.TEXT -> plainInput
            QrTemplate.WIFI -> if (wifiSsid.isBlank()) "" else "WIFI:T:$wifiAuth;S:$wifiSsid;P:$wifiPass;;"
            QrTemplate.VCARD -> if (cardName.isBlank() && cardPhone.isBlank()) "" else
                "BEGIN:VCARD\nVERSION:3.0\nFN:$cardName\nORG:$cardOrg\nTEL:$cardPhone\nEND:VCARD"
            QrTemplate.PHONE -> if (phoneInput.isBlank()) "" else "tel:$phoneInput"
        }
    }

    val qrBitmap: Bitmap? = remember(qrPayload, fgIdx, bgIdx, dotStyle, eccLevel, caption) {
        QrCodeHelper.generateStyledQrBitmap(
            content = qrPayload,
            fgColor = fgPalette[fgIdx].toArgb(),
            bgColor = bgPalette[bgIdx].toArgb(),
            dotStyle = dotStyle,
            eccLevel = eccLevel,
            caption = caption
        )
    }

    // Smooth 1:1 scroll-driven collapse for pinned QR preview (200dp -> 100dp = 1/2 size)
    val maxCollapsePx = with(LocalDensity.current) { 120.dp.toPx() }
    var collapseOffsetPx by remember { mutableFloatStateOf(0f) }
    val collapseProgress = (if (maxCollapsePx > 0f) (-collapseOffsetPx / maxCollapsePx) else 0f).coerceIn(0f, 1f)

    val listState = rememberLazyListState()
    val nestedScrollConnection = remember(maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0f) {
                    val prev = collapseOffsetPx
                    collapseOffsetPx = (collapseOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                    return Offset(0f, collapseOffsetPx - prev)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta > 0f) {
                    val prev = collapseOffsetPx
                    collapseOffsetPx = (collapseOffsetPx + delta).coerceIn(-maxCollapsePx, 0f)
                    return Offset(0f, collapseOffsetPx - prev)
                }
                return Offset.Zero
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "二维码",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.liquidPress()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenCameraScan, modifier = Modifier.liquidPress()) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = "扫一扫")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                QrTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                        modifier = Modifier.liquidPress(pressedScale = 0.97f)
                    ) {
                        Text(tab.label, maxLines = 1, softWrap = false)
                    }
                }
            }

            if (currentTab == QrTab.GENERATE) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                ) {
                    // Continuous 1:1 interpolated pinned QR Preview Card (no layout jump!)
                    val qrSize = lerp(196.dp, 98.dp, collapseProgress)
                    val boxHeight = lerp(266.dp, 122.dp, collapseProgress)

                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = visuals.cardContainerColor,
                        contentColor = visuals.cardContentColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(MaterialTheme.shapes.large)
                            .liquidGlassSurface(26.dp)
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(boxHeight)
                        ) {
                            val expandedQrX = (maxWidth - qrSize) / 2f
                            val qrX = lerp(expandedQrX, 12.dp, collapseProgress)
                            val qrY = lerp(12.dp, (boxHeight - qrSize) / 2f, collapseProgress)

                            val btnX = lerp(14.dp, 122.dp, collapseProgress)
                            val btnY = lerp(216.dp, (boxHeight - 40.dp) / 2f, collapseProgress)
                            val btnWidth = lerp(maxWidth - 28.dp, maxWidth - 134.dp, collapseProgress)

                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "二维码预览",
                                    modifier = Modifier
                                        .offset(x = qrX, y = qrY)
                                        .size(qrSize)
                                        .clip(RoundedCornerShape(lerp(14.dp, 10.dp, collapseProgress)))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .offset(x = qrX, y = qrY)
                                        .size(qrSize)
                                        .clip(RoundedCornerShape(lerp(14.dp, 10.dp, collapseProgress)))
                                        .background(visuals.innerFieldColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "输入内容生成",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .offset(x = btnX, y = btnY)
                                    .width(btnWidth)
                                    .height(40.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    enabled = qrBitmap != null,
                                    onClick = {
                                        val ok = qrBitmap?.let { QrCodeHelper.saveBitmapToGallery(context, it) } == true
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (ok) "已保存到相册 (Pictures/PocketToolbox)" else "保存失败，请检查相册权限"
                                            )
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .liquidPress()
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("保存", fontSize = 13.sp, maxLines = 1, softWrap = false)
                                }

                                FilledTonalButton(
                                    enabled = qrPayload.isNotBlank(),
                                    onClick = {
                                        clipboard.setText(AnnotatedString(qrPayload))
                                        scope.launch { snackbarHostState.showSnackbar("已复制码内容") }
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .height(40.dp)
                                        .liquidPress()
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("复制", fontSize = 13.sp, maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }

                    // Scrollable Content & Beautify cards clipped cleanly beneath the pinned QR preview
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clipToBounds(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = visuals.cardContainerColor,
                                contentColor = visuals.cardContentColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .liquidGlassSurface(26.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "内容类型",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        QrTemplate.entries.forEach { item ->
                                            FilterChip(
                                                selected = template == item,
                                                onClick = { template = item },
                                                label = { Text(item.label, maxLines = 1, softWrap = false) }
                                            )
                                        }
                                    }

                                    when (template) {
                                        QrTemplate.TEXT -> {
                                            OutlinedTextField(
                                                value = plainInput,
                                                onValueChange = { plainInput = it },
                                                label = { Text("输入文本或网址") },
                                                minLines = 2,
                                                maxLines = 4,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        QrTemplate.WIFI -> {
                                            OutlinedTextField(
                                                value = wifiSsid,
                                                onValueChange = { wifiSsid = it },
                                                label = { Text("Wi-Fi 名称 (SSID)") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            OutlinedTextField(
                                                value = wifiPass,
                                                onValueChange = { wifiPass = it },
                                                label = { Text("Wi-Fi 密码") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf("WPA", "WEP", "nopass").forEach { auth ->
                                                    FilterChip(
                                                        selected = wifiAuth == auth,
                                                        onClick = { wifiAuth = auth },
                                                        label = { Text(if (auth == "nopass") "无密码" else auth) }
                                                    )
                                                }
                                            }
                                        }
                                        QrTemplate.VCARD -> {
                                            OutlinedTextField(
                                                value = cardName,
                                                onValueChange = { cardName = it },
                                                label = { Text("姓名") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            OutlinedTextField(
                                                value = cardPhone,
                                                onValueChange = { cardPhone = it },
                                                label = { Text("电话") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            OutlinedTextField(
                                                value = cardOrg,
                                                onValueChange = { cardOrg = it },
                                                label = { Text("公司 / 备注") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        QrTemplate.PHONE -> {
                                            OutlinedTextField(
                                                value = phoneInput,
                                                onValueChange = { phoneInput = it },
                                                label = { Text("电话号码") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                color = visuals.cardContainerColor,
                                contentColor = visuals.cardContentColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .liquidGlassSurface(26.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "二维码美化",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("前景色", fontSize = 13.5.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            fgPalette.forEachIndexed { idx, color ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                        .border(
                                                            width = if (fgIdx == idx) 2.5.dp else 1.dp,
                                                            color = if (fgIdx == idx) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                                            shape = CircleShape
                                                        )
                                                        .clickable { fgIdx = idx }
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("背景色", fontSize = 13.5.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            bgPalette.forEachIndexed { idx, color ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(color)
                                                        .border(
                                                            width = if (bgIdx == idx) 2.5.dp else 1.dp,
                                                            color = if (bgIdx == idx) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                                            shape = CircleShape
                                                        )
                                                        .clickable { bgIdx = idx }
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        QrDotStyle.entries.forEach { style ->
                                            FilterChip(
                                                selected = dotStyle == style,
                                                onClick = { dotStyle = style },
                                                label = { Text(style.label, maxLines = 1, softWrap = false) }
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        QrEccLevel.entries.forEach { ecc ->
                                            FilterChip(
                                                selected = eccLevel == ecc,
                                                onClick = { eccLevel = ecc },
                                                label = { Text("容错 ${ecc.label}", maxLines = 1, softWrap = false) }
                                            )
                                        }
                                    }

                                    OutlinedTextField(
                                        value = caption,
                                        onValueChange = { caption = it },
                                        label = { Text("底部标签文字（可选）") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Decode Tab
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = visuals.cardContainerColor,
                            contentColor = visuals.cardContentColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .liquidGlassSurface(26.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        galleryPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .weight(1f)
                                        .liquidPress()
                                ) {
                                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("相册选图识别", maxLines = 1, softWrap = false)
                                }

                                FilledTonalButton(
                                    onClick = onOpenCameraScan,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .weight(1f)
                                        .liquidPress()
                                ) {
                                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("相机扫一扫", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = visuals.cardContainerColor,
                            contentColor = visuals.cardContentColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.large)
                                .liquidGlassSurface(26.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "识别结果",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = visuals.innerFieldColor,
                                    contentColor = visuals.cardContentColor,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = decodedText.ifBlank { "尚未识别内容，请从相册选择二维码图片" },
                                        fontSize = 13.5.sp,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }

                                if (decodedText.isNotBlank()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        FilledTonalButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(decodedText))
                                                scope.launch { snackbarHostState.showSnackbar("已复制识别结果") }
                                            },
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("复制", maxLines = 1, softWrap = false)
                                        }
                                        Button(
                                            onClick = { onOpenInBrowser(decodedText) },
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("浏览器打开", maxLines = 1, softWrap = false)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
