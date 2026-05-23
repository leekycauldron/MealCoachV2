/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.meal.MealFlowStep
import com.meta.wearable.dat.externalsampleapps.cameraaccess.meal.MealUiState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.meal.MenuRecommendation
import kotlin.math.roundToInt

@Composable
fun MealResultScreen(
    state: MealUiState,
    onRetry: () -> Unit,
    onRescan: () -> Unit,
    onStartOver: () -> Unit,
    onSelectRecommendation: (MenuRecommendation) -> Unit,
    onDismissDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
  when {
    state.step == MealFlowStep.ANALYZING -> AnalyzingView(modifier)
    state.recommendations.isEmpty() ->
        ErrorView(
            message = state.errorMessage,
            onRetry = onRetry,
            onRescan = onRescan,
            onStartOver = onStartOver,
            modifier = modifier,
        )
    else ->
        MenuMapView(
            state = state,
            onSelectRecommendation = onSelectRecommendation,
            onDismissDetail = onDismissDetail,
            onRescan = onRescan,
            onStartOver = onStartOver,
            modifier = modifier,
        )
  }
}

@Composable
private fun AnalyzingView(modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.fillMaxSize().systemBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    CircularProgressIndicator()
    Spacer(Modifier.size(16.dp))
    Text(
        text = stringResource(R.string.meal_analyzing),
        style = MaterialTheme.typography.titleMedium,
    )
  }
}

@Composable
private fun ErrorView(
    message: String?,
    onRetry: () -> Unit,
    onRescan: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier =
          modifier
              .fillMaxSize()
              .systemBarsPadding()
              .padding(horizontal = 24.dp)
              .navigationBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Spacer(Modifier.weight(1f))
    Text(
        text = stringResource(R.string.meal_result_error_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.size(8.dp))
    Text(
        text = message ?: stringResource(R.string.meal_result_empty),
        color = Color.Gray,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.weight(1f))
    ButtonStack(
        buttons =
            listOf(
                stringResource(R.string.meal_result_retry) to onRetry,
                stringResource(R.string.meal_result_rescan) to onRescan,
                stringResource(R.string.meal_result_start_over) to onStartOver,
            ),
    )
    Spacer(Modifier.size(16.dp))
  }
}

@Composable
private fun MenuMapView(
    state: MealUiState,
    onSelectRecommendation: (MenuRecommendation) -> Unit,
    onDismissDetail: () -> Unit,
    onRescan: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val bitmap = state.capturedPhotos.firstOrNull()
  Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
    if (bitmap != null) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val bw = bitmap.width.toFloat().coerceAtLeast(1f)
        val bh = bitmap.height.toFloat().coerceAtLeast(1f)
        // ContentScale.Fit: scale to the limiting axis and center (letterbox the other axis).
        val scale = minOf(cw / bw, ch / bh)
        val dispW = bw * scale
        val dispH = bh * scale
        val offX = (cw - dispW) / 2f
        val offY = (ch - dispH) / 2f

        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.meal_result_title),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        val dotSize = 30.dp
        state.recommendations.forEach { rec ->
          val box = rec.box ?: return@forEach
          val cx = offX + box.centerX * dispW
          val cy = offY + box.centerY * dispH
          PulsingDot(
              color = scoreColor(rec.score),
              modifier =
                  Modifier.offset {
                        IntOffset(
                            (cx - dotSize.toPx() / 2f).roundToInt(),
                            (cy - dotSize.toPx() / 2f).roundToInt(),
                        )
                      }
                      .size(dotSize)
                      .clickable { onSelectRecommendation(rec) },
          )
        }
      }
    }

    Row(
        modifier =
            Modifier.align(Alignment.TopCenter)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = stringResource(R.string.meal_result_title),
          color = Color.White,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(start = 8.dp),
      )
      Spacer(Modifier.weight(1f))
      TextButton(onClick = onRescan) {
        Text(stringResource(R.string.meal_result_rescan), color = Color.White)
      }
      TextButton(onClick = onStartOver) {
        Text(stringResource(R.string.meal_result_start_over), color = Color.White)
      }
    }

    val unplaced = state.recommendations.filter { it.box == null }
    Column(
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (unplaced.isNotEmpty()) {
        Text(
            text = stringResource(R.string.meal_result_unplaced),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.size(6.dp))
        UnplacedChips(items = unplaced, onSelect = onSelectRecommendation)
        Spacer(Modifier.size(10.dp))
      }
      PillHint(stringResource(R.string.meal_result_tap_hint))
    }

    state.selectedRecommendation?.let { rec ->
      RecommendationDetailSheet(
          rec = rec,
          imageUrl = state.dishImageUrls[rec.name],
          imageFetched = state.dishImageUrls.containsKey(rec.name),
          onDismiss = onDismissDetail,
      )
    }
  }
}

@Composable
private fun PulsingDot(color: Color, modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "dot")
  val scale by
      transition.animateFloat(
          initialValue = 1f,
          targetValue = 1.4f,
          animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
          label = "scale",
      )
  val haloAlpha by
      transition.animateFloat(
          initialValue = 0.7f,
          targetValue = 0.15f,
          animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
          label = "alpha",
      )
  Box(modifier, contentAlignment = Alignment.Center) {
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
              scaleX = scale
              scaleY = scale
              alpha = haloAlpha
            }
            .background(color, CircleShape),
    )
    Box(
        Modifier.fillMaxSize(0.5f)
            .background(color, CircleShape)
            .border(2.dp, Color.White, CircleShape),
    )
  }
}

@Composable
private fun UnplacedChips(items: List<MenuRecommendation>, onSelect: (MenuRecommendation) -> Unit) {
  Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    items.forEach { rec ->
      Row(
          modifier =
              Modifier.background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                  .clickable { onSelect(rec) }
                  .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.size(10.dp).background(scoreColor(rec.score), CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(rec.name, color = Color.White)
      }
    }
  }
}

@Composable
private fun PillHint(text: String) {
  Text(
      text = text,
      color = Color.White,
      modifier =
          Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
              .padding(horizontal = 14.dp, vertical = 8.dp),
  )
}

@Composable
private fun RecommendationDetailSheet(
    rec: MenuRecommendation,
    imageUrl: String?,
    imageFetched: Boolean,
    onDismiss: () -> Unit,
) {
  Box(Modifier.fillMaxSize()) {
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
              onDismiss()
            },
    )
    Surface(
        modifier =
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Swallow taps so they don't fall through to the dismiss scrim behind.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {},
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
      Column(
          modifier =
              Modifier.fillMaxWidth()
                  .padding(20.dp)
                  .navigationBarsPadding()
                  .verticalScroll(rememberScrollState()),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(14.dp).background(scoreColor(rec.score), CircleShape))
          Spacer(Modifier.width(8.dp))
          Text(
              text = rec.name,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.weight(1f),
          )
          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.meal_detail_close))
          }
        }
        Spacer(Modifier.size(12.dp))
        DishImage(imageUrl = imageUrl, fetched = imageFetched, name = rec.name)
        Spacer(Modifier.size(16.dp))
        Text(
            text = stringResource(R.string.meal_detail_why),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(text = rec.reason, style = MaterialTheme.typography.bodyMedium)
        rec.caveats?.let { caveats ->
          Spacer(Modifier.size(12.dp))
          Text(
              text = stringResource(R.string.meal_detail_caveats),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
          )
          Text(text = caveats, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.size(8.dp))
      }
    }
  }
}

@Composable
private fun DishImage(imageUrl: String?, fetched: Boolean, name: String) {
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .height(180.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color(0xFFEEEEEE)),
      contentAlignment = Alignment.Center,
  ) {
    when {
      imageUrl != null ->
          AsyncImage(
              model = imageUrl,
              contentDescription = name,
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Crop,
          )
      fetched -> Text(stringResource(R.string.meal_detail_no_image), color = Color.Gray)
      else -> CircularProgressIndicator()
    }
  }
}

@Composable
private fun ButtonStack(buttons: List<Pair<String, () -> Unit>>) {
  Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    buttons.forEachIndexed { index, (label, action) ->
      SwitchButton(
          label = label,
          onClick = action,
          isDestructive = index == buttons.lastIndex && buttons.size > 1,
      )
    }
  }
}

private fun scoreColor(score: Float): Color {
  val s = score.coerceIn(0f, 1f)
  // 0 = red (weak), 120 = green (strong).
  return Color.hsv(120f * s, 0.75f, 0.9f)
}
