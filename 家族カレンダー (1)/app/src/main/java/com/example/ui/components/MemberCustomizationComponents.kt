package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MemberColorPalette
import com.example.util.AvatarImageUtils
import com.example.util.parseHexColor
import kotlinx.coroutines.launch

@Composable
fun MemberAvatarBadge(
  symbol: String,
  colorHex: String,
  avatarImageBase64: String = "",
  size: Dp = 36.dp,
  modifier: Modifier = Modifier,
) {
  val bg = parseHexColor(colorHex)
  val decodedBitmap = remember(avatarImageBase64) {
    if (avatarImageBase64.isNotBlank()) {
      AvatarImageUtils.decodeBase64ToBitmap(avatarImageBase64)?.asImageBitmap()
    } else {
      null
    }
  }

  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .background(bg)
      .then(
        if (decodedBitmap != null) {
          Modifier.border(2.dp, bg, CircleShape)
        } else {
          Modifier
        }
      ),
    contentAlignment = Alignment.Center,
  ) {
    if (decodedBitmap != null) {
      Image(
        bitmap = decodedBitmap,
        contentDescription = "メンバーアイコン画像",
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .clip(CircleShape),
      )
    } else {
      Text(
        text = symbol.ifBlank { "👤" },
        color = Color.White,
        fontSize = (size.value * 0.48f).sp,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorAndSymbolPicker(
  selectedColorHex: String,
  onColorSelected: (String) -> Unit,
  selectedSymbol: String,
  onSymbolSelected: (String) -> Unit,
  selectedAvatarImageBase64: String = "",
  onAvatarImageChanged: ((String) -> Unit)? = null,
  availableSymbols: List<String> = MemberColorPalette.avatarSymbols,
  imageSectionLabel: String = "アイコン画像",
  colorSectionLabel: String = "アイコンカラー（プリセット＆カスタム調整）",
  symbolSectionLabel: String = "アイコン記号・マーク（画像未設定時に表示）",
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var isProcessingImage by remember { mutableStateOf(false) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null && onAvatarImageChanged != null) {
      isProcessingImage = true
      scope.launch {
        val encoded = AvatarImageUtils.encodeUriToAvatarBase64(context, uri)
        isProcessingImage = false
        if (encoded != null) {
          onAvatarImageChanged(encoded)
        } else {
          Toast.makeText(
            context,
            "画像の読み込みに失敗しました。別の画像をお試しください。",
            Toast.LENGTH_SHORT,
          ).show()
        }
      }
    }
  }

  val parsed = parseHexColor(selectedColorHex)
  var hue by remember(selectedColorHex) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(parsed.toArgb(), hsv)
    mutableFloatStateOf(hsv[0])
  }

  Column(modifier = modifier.fillMaxWidth()) {
    if (onAvatarImageChanged != null) {
      Text(
        text = imageSectionLabel,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        OutlinedButton(
          onClick = {
            photoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
          },
          enabled = !isProcessingImage,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.testTag("upload_avatar_image_button"),
        ) {
          Icon(
            imageVector = Icons.Filled.AddPhotoAlternate,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            if (isProcessingImage) "画像を処理中..."
            else if (selectedAvatarImageBase64.isNotBlank()) "画像を変更"
            else "画像からアップロード"
          )
        }

        if (selectedAvatarImageBase64.isNotBlank()) {
          TextButton(
            onClick = { onAvatarImageChanged("") },
            modifier = Modifier.testTag("clear_avatar_image_button"),
          ) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("画像を解除")
          }
        }
      }
      Spacer(modifier = Modifier.height(14.dp))
    }

    Text(
      text = colorSectionLabel,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(10.dp))

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      MemberColorPalette.googlePresetColors.forEach { hex ->
        val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
        val color = parseHexColor(hex)
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .then(
              if (isSelected) {
                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
              } else {
                Modifier
              }
            )
            .clickable { onColorSelected(hex) },
          contentAlignment = Alignment.Center,
        ) {
          if (isSelected) {
            Icon(
              imageVector = Icons.Filled.Check,
              contentDescription = "選択中の色",
              tint = Color.White,
              modifier = Modifier.size(20.dp),
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text = "色相を微調整",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Box(
        modifier = Modifier
          .size(24.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(parsed)
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = selectedColorHex.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Slider(
      value = hue,
      onValueChange = { newHue ->
        hue = newHue
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(newHue, 0.68f, 0.88f))
        val hex = String.format("#%06X", 0xFFFFFF and rgb)
        onColorSelected(hex)
      },
      valueRange = 0f..360f,
      colors = SliderDefaults.colors(
        thumbColor = parsed,
        activeTrackColor = parsed,
      ),
    )

    Spacer(modifier = Modifier.height(10.dp))

    Text(
      text = symbolSectionLabel,
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(8.dp))

    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      availableSymbols.forEach { symbol ->
        val isSelected = selectedSymbol == symbol
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
              if (isSelected) parsed.copy(alpha = 0.22f)
              else MaterialTheme.colorScheme.surfaceVariant
            )
            .then(
              if (isSelected) Modifier.border(2.dp, parsed, RoundedCornerShape(14.dp))
              else Modifier
            )
            .clickable { onSymbolSelected(symbol) },
          contentAlignment = Alignment.Center,
        ) {
          Text(
            text = symbol,
            fontSize = 20.sp,
          )
        }
      }
    }
  }
}
