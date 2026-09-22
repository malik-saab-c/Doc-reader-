package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    var animationStage by remember { mutableStateOf(0) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    LaunchedEffect(Unit) {
        delay(200)
        animationStage = 1 // Logo appearance
        delay(400)
        animationStage = 2 // Title appearance
        delay(500)
        animationStage = 3 // Developer name reveal
        delay(600)
        animationStage = 4 // Feature pills
        delay(1800)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("splash_screen_container"),
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient background circles
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFFF1F5F9),
                radius = size.width * 0.7f,
                center = Offset(size.width * 0.8f, size.height * 0.15f)
            )
            drawCircle(
                color = Color(0xFFF8FAFC),
                radius = size.width * 0.5f,
                center = Offset(size.width * 0.1f, size.height * 0.85f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            // Animated App Icon with rotating aura
            AnimatedVisibility(
                visible = animationStage >= 1,
                enter = scaleIn(animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) + fadeIn()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(130.dp)
                        .scale(pulseScale)
                ) {
                    // Rotating gradient ring
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .rotate(rotateAngle)
                            .border(
                                width = 3.dp,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color(0xFF2563EB),
                                        Color(0xFF16A34A),
                                        Color(0xFFEA580C),
                                        Color(0xFF7C3AED),
                                        Color(0xFF2563EB)
                                    )
                                ),
                                shape = CircleShape
                            )
                    )

                    // Inner Emblem Card
                    Surface(
                        modifier = Modifier.size(96.dp),
                        shape = CircleShape,
                        color = PureWhite,
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = "Universal Document Core",
                                tint = BrandAccent,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // App Title
            AnimatedVisibility(
                visible = animationStage >= 2,
                enter = slideInVertically(initialOffsetY = { 40 }) + fadeIn()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "All File Reader",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Universal Document Viewer & Editor Studio",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Developer Signature Card (Requested by user: "developer Sir Ghulam Mustafa")
            AnimatedVisibility(
                visible = animationStage >= 3,
                enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(tween(600))
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PureWhite,
                    shadowElevation = 6.dp,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .testTag("developer_signature_card")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 18.dp, horizontal = 20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified Architect",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ARCHITECT & LEAD DEVELOPER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB),
                                letterSpacing = 1.2.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Required Exact Phrase
                        Text(
                            text = "developer Sir Ghulam Mustafa",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A),
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Serif
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Masterwork Engine • 100% Offline Universal Suite",
                            fontSize = 12.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Tech Spec Badges
            AnimatedVisibility(
                visible = animationStage >= 4,
                enter = slideInVertically(initialOffsetY = { 30 }) + fadeIn()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SplashPill(
                        icon = Icons.Default.Security,
                        title = "100% Offline",
                        subtitle = "No Web Access"
                    )
                    SplashPill(
                        icon = Icons.Default.Speed,
                        title = "Ultra Fast",
                        subtitle = "Native Hardware"
                    )
                    SplashPill(
                        icon = Icons.Default.CheckCircle,
                        title = "Zero Bloat",
                        subtitle = "< 25 MB"
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Interactive Skip/Continue button
            AnimatedVisibility(
                visible = animationStage >= 3,
                enter = fadeIn()
            ) {
                Button(
                    onClick = onFinished,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandPrimary,
                        contentColor = PureWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("enter_studio_button")
                ) {
                    Text(
                        text = "Open Workspace",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Open",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = OffWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSubtle),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = TextMuted
            )
        }
    }
}
