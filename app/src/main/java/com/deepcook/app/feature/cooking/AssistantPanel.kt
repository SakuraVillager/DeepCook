package com.deepcook.app.feature.cooking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** What the cooking assistant is doing right now; drives the bottom panel. */
enum class AssistantPhase { Hidden, Greeting, Listening, Recognizing, Answering, Failed }

/**
 * The hands-free assistant panel: a half sheet that rises over the step, keeps the current step
 * visible behind it, and shows only a breathing orb while the microphone is open. Partial
 * transcripts are deliberately not shown — the streaming model is an internal endpoint detector,
 * and the SenseVoice pass decides what was actually said.
 */
@Composable
fun AssistantPanel(
    phase: AssistantPhase,
    level: Float,
    question: String,
    answer: String,
    thinking: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onType: () -> Unit,
) {
    val visible = phase != AssistantPhase.Hidden
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(onClickLabel = "取消", onClick = onCancel),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(tween(220)) { it / 2 } + fadeIn(tween(220)),
            exit = slideOutVertically(tween(180)) { it / 2 } + fadeOut(tween(180)),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "厨房助手",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, "关闭")
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    AssistantOrb(phase, level)
                    Spacer(Modifier.height(18.dp))
                    when (phase) {
                        AssistantPhase.Greeting -> Text("我在，请说", style = MaterialTheme.typography.headlineSmall)
                        AssistantPhase.Listening -> Text("正在听…", style = MaterialTheme.typography.headlineSmall)
                        AssistantPhase.Recognizing -> {
                            Text("正在识别…", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth(0.5f))
                        }
                        AssistantPhase.Answering -> Column(
                            Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (question.isNotEmpty()) {
                                Text(
                                    question,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                if (answer.isNotEmpty()) answer else if (thinking) "正在想…" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                        AssistantPhase.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                error ?: "没听清",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FilledTonalButton(onClick = onRetry) { Text("再说一次") }
                                OutlinedButton(onClick = onType) { Text("打字") }
                            }
                        }
                        AssistantPhase.Hidden -> Unit
                    }
                    Spacer(Modifier.height(12.dp))
                    if (phase == AssistantPhase.Greeting || phase == AssistantPhase.Listening) {
                        Text(
                            "说完会自动结束，也可以点右上角关掉",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantOrb(phase: AssistantPhase, level: Float) {
    val active = phase == AssistantPhase.Greeting || phase == AssistantPhase.Listening ||
        phase == AssistantPhase.Recognizing
    val breathing = rememberInfiniteTransition(label = "orb")
    val breath by breathing.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "breath",
    )
    val live by animateFloatAsState(
        targetValue = 0.9f + level.coerceIn(0f, 1f) * 0.5f,
        animationSpec = tween(120),
        label = "level",
    )
    val scale = if (phase == AssistantPhase.Listening) live else if (active) breath else 0.9f
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                Modifier
                    .size(132.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.02f),
                            ),
                        ),
                    ),
            )
        }
        Box(
            Modifier
                .size(84.dp)
                .scale(if (active) scale else 1f)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (phase == AssistantPhase.Recognizing) Icons.Rounded.Cloud else Icons.Rounded.Mic,
                null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}
