package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedCircularGoalRing(
    progress: Float,
    currentValue: Int,
    targetValue: Int,
    label: String,
    unitLabel: String,
    ringSize: Dp = 136.dp,
    strokeWidth: Dp = 12.dp,
    primaryColor: Color = Color(0xFF38BDF8),
    secondaryColor: Color = Color(0xFFFBBF24),
    modifier: Modifier = Modifier
) {
    val clampedTarget = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "goal_ring_progress"
    )
    val animatedNumber by animateIntAsState(
        targetValue = currentValue,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "goal_ring_number"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(ringSize)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val arcDiameter = size.minDimension - strokePx * 1.5f
            val topLeftOffset = Offset(
                (size.width - arcDiameter) / 2f,
                (size.height - arcDiameter) / 2f
            )

            // Track ring
            drawArc(
                color = primaryColor.copy(alpha = 0.16f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeftOffset,
                size = Size(arcDiameter, arcDiameter),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Animated gradient arc
            val sweep = 360f * animatedProgress
            if (sweep > 1f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(primaryColor, secondaryColor, primaryColor)
                    ),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeftOffset,
                    size = Size(arcDiameter, arcDiameter),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Glowing tip dot
                val angleRad = Math.toRadians((-90f + sweep).toDouble())
                val radius = arcDiameter / 2f
                val center = Offset(size.width / 2f, size.height / 2f)
                val tipX = center.x + (radius * cos(angleRad)).toFloat()
                val tipY = center.y + (radius * sin(angleRad)).toFloat()
                drawCircle(
                    color = Color.White,
                    radius = strokePx * 0.42f,
                    center = Offset(tipX, tipY)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$animatedNumber",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "/ $targetValue $unitLabel",
                fontSize = 11.sp,
                color = Color(0xFF93C5FD)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = secondaryColor
            )
        }
    }
}

@Composable
fun AnimatedMacroProgressBar(
    label: String,
    currentGrams: Double,
    targetGrams: Int,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val ratio = if (targetGrams > 0) (currentGrams.toFloat() / targetGrams.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedRatio by animateFloatAsState(
        targetValue = ratio,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "macro_bar_$label"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${currentGrams.toInt()}g / ${targetGrams}g (${(animatedRatio * 100).toInt()}%)",
                style = MaterialTheme.typography.bodyMedium,
                color = barColor,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(barColor.copy(alpha = 0.16f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedRatio)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(99.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(barColor.copy(alpha = 0.85f), barColor)
                        )
                    )
            )
        }
    }
}

@Composable
fun PulsingHeartBadge(
    bpm: Int?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heart_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (bpm != null && bpm > 0) 1.24f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart_scale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Color(0xFFEF4444).copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "Heart Rate Pulse",
            tint = Color(0xFFEF4444),
            modifier = Modifier
                .size(16.dp)
                .scale(pulseScale)
        )
        Text(
            text = if (bpm != null && bpm > 0) "$bpm bpm" else "— bpm",
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFF87171)
        )
    }
}

data class ChartBarPoint(
    val dateLabel: String,
    val value: Float
)

@Composable
fun AnimatedHealthBarChart(
    points: List<ChartBarPoint>,
    unitLabel: String,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    val maxVal = (points.maxOfOrNull { it.value } ?: 1f).coerceAtLeast(1f)
    val animatedMultiplier by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "bar_chart_growth"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            if (points.isEmpty()) return@Canvas
            val count = points.size
            val slotWidth = size.width / count.toFloat()
            val barWidth = (slotWidth * 0.52f).coerceIn(10f, 56f)
            val usableHeight = size.height - 20f

            // Baseline
            drawLine(
                color = barColor.copy(alpha = 0.25f),
                start = Offset(0f, usableHeight),
                end = Offset(size.width, usableHeight),
                strokeWidth = 2f
            )

            points.forEachIndexed { index, pt ->
                val xCenter = index * slotWidth + slotWidth / 2f
                val normalized = (pt.value / maxVal).coerceIn(0.05f, 1f) * animatedMultiplier
                val barHeight = usableHeight * normalized
                val topY = usableHeight - barHeight

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(barColor, barColor.copy(alpha = 0.55f))
                    ),
                    topLeft = Offset(xCenter - barWidth / 2f, topY),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(10f, 10f)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEach { pt ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pt.value.toInt().toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = barColor
                    )
                    Text(
                        text = pt.dateLabel,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Unit: $unitLabel",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
