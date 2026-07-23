package com.example.ui

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.GameEngine
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.GameViewModel
import com.example.viewmodel.HackingMinigameState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TerminalScreen(viewModel: GameViewModel) {
    val playerStats by viewModel.playerStats.collectAsState()
    val worldState by viewModel.worldState.collectAsState()
    val combatState by viewModel.combatState.collectAsState()
    val weatherState by viewModel.weatherState.collectAsState()
    val hackingState by viewModel.hackingState.collectAsState()
    val shopActive by viewModel.shopActive.collectAsState()
    val logs by viewModel.terminalLogs.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBg)
    ) {
        // Aesthetic Grid Background with Scanlines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val rows = size.height / 30f
            val cols = size.width / 30f
            for (i in 0..rows.toInt()) {
                drawLine(
                    color = Color(0xFF0F0F24),
                    start = Offset(0f, i * 30f),
                    end = Offset(size.width, i * 30f),
                    strokeWidth = 1f
                )
            }
            for (i in 0..cols.toInt()) {
                drawLine(
                    color = Color(0xFF0F0F24),
                    start = Offset(i * 30f, 0f),
                    end = Offset(i * 30f, size.height),
                    strokeWidth = 1f
                )
            }
            for (y in 0..size.height.toInt() step 8) {
                drawLine(
                    color = Color(0x04CEF7FF),
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 2f
                )
            }
        }

        // Render Portrait or Landscape Layout dynamically
        if (isLandscape) {
            TerminalLandscapeLayout(
                playerStats = playerStats,
                worldState = worldState,
                weatherState = weatherState,
                viewModel = viewModel
            )
        } else {
            TerminalPortraitLayout(
                playerStats = playerStats,
                worldState = worldState,
                weatherState = weatherState,
                viewModel = viewModel
            )
        }

        // --- Modals & Overlays ---
        if (combatState.inCombat) {
            CombatOverlay(combatState, playerStats, viewModel, isLandscape)
        }

        if (hackingState.active) {
            HackingOverlay(hackingState, playerStats, viewModel, isLandscape)
        }

        if (shopActive) {
            ShopOverlay(playerStats, viewModel, isLandscape)
        }
    }
}

// --- PORTRAIT LAYOUT ---
@Composable
fun TerminalPortraitLayout(
    playerStats: PlayerStats,
    worldState: WorldState,
    weatherState: WeatherState,
    viewModel: GameViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. Top Atmospheric Weather Banner
        TopStatusBar(playerStats, weatherState, worldState)

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Middle Grid Partitions: Map with Floating Minimap, and full-height system stats block
        Row(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 2D Level Graphical Map Engine Viewport with Swipe Support
            Box(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .border(BorderStroke(1.5.dp, CyberPrimary.copy(alpha = 0.4f)), RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .mapSwipeControls { dx, dy -> viewModel.movePlayer(dx, dy) }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Cyber2DMapCanvas(
                    worldState = worldState,
                    weatherState = weatherState,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Sidebar Status Chip
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(BorderStroke(1.dp, CyberPrimary.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
                        .background(CyberSurface)
                        .padding(10.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Text(
                            text = "░ STATUS CHIP ░",
                            color = CyberPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "DECK_LVL: ${playerStats.level}",
                            color = CyberText,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        
                        ProgressBar("HP", playerStats.hp, playerStats.maxHp, CyberTertiary)
                        ProgressBar("SHD", playerStats.shield, playerStats.maxShield, CyberPrimary)

                        Spacer(modifier = Modifier.height(2.dp))
                        
                        Text(
                            text = "CREDITS: ${playerStats.credits} ⛀",
                            color = CyberSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "WEAPON: ${playerStats.equippedWeapon}",
                            color = CyberText,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "HACK_SKILL: ${playerStats.hackingSkill}",
                            color = CyberPrimary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        val expRatio = playerStats.exp.toFloat() / playerStats.expToNextLevel.toFloat()
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "EXP LEVEL",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${playerStats.exp}/${playerStats.expToNextLevel}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(Color(0xFF14142B))
                                    .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(1.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(expRatio.coerceIn(0f, 1f))
                                        .background(CyberSecondary)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Lower Division: Full-Width Dedicated Control Console & D-Pad (Replacing Engine Output & Term Log)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .border(BorderStroke(1.5.dp, CyberPrimary.copy(alpha = 0.4f)), RoundedCornerShape(16.dp))
                .background(CyberSurface)
                .padding(10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Inventory Utilities Column
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Button(
                        onClick = { viewModel.useMedkit() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberTertiary.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, CyberTertiary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("medkit_button")
                    ) {
                        Text("MEDKIT (${playerStats.medkits})", fontSize = 10.sp, color = CyberTertiary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = { viewModel.useShieldCell() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, CyberPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("shield_cell_button")
                    ) {
                        Text("SHIELD (${playerStats.shieldCells})", fontSize = 10.sp, color = CyberPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = { viewModel.toggleShop(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, CyberSecondary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("BLACK_MKT", fontSize = 10.sp, color = CyberSecondary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                // Center Column: Prominent Ergonomic Continuous D-Pad
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    DPadButton("▲", "move_up") { viewModel.movePlayer(0, -1) }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DPadButton("◀", "move_left") { viewModel.movePlayer(-1, 0) }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberSecondary.copy(alpha = 0.2f))
                                .border(BorderStroke(1.5.dp, CyberSecondary), RoundedCornerShape(8.dp))
                                .clickable { viewModel.activateElevator() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("LIFT", fontSize = 11.sp, color = CyberSecondary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        DPadButton("▶", "move_right") { viewModel.movePlayer(1, 0) }
                    }
                    DPadButton("▼", "move_down") { viewModel.movePlayer(0, 1) }
                }

                // Right Column: Quick Interactive Actions & Status Info
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Button(
                        onClick = { viewModel.activateElevator() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, CyberSecondary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("ELEVATOR ⛛", fontSize = 10.sp, color = CyberSecondary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .border(BorderStroke(1.dp, CyberPrimary.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("KEYCARDS: ${playerStats.keycards}", fontSize = 10.sp, color = CyberPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .border(BorderStroke(1.dp, CyberTertiary.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ATK POWER: ${playerStats.attack}", fontSize = 10.sp, color = CyberTertiary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

// --- LANDSCAPE LAYOUT ---
@Composable
fun TerminalLandscapeLayout(
    playerStats: PlayerStats,
    worldState: WorldState,
    weatherState: WeatherState,
    viewModel: GameViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Compact Status Header for Landscape
        TopStatusBar(playerStats, weatherState, worldState)

        Spacer(modifier = Modifier.height(4.dp))

        // Main Widescreen Split: Left Map, Right Info & Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // LEFT COLUMN: 2D Level Graphical Map Engine Viewport with Touch Swipe
            Box(
                modifier = Modifier
                    .weight(1.25f)
                    .fillMaxHeight()
                    .border(BorderStroke(1.5.dp, CyberPrimary.copy(alpha = 0.4f)), RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .mapSwipeControls { dx, dy -> viewModel.movePlayer(dx, dy) }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Cyber2DMapCanvas(
                    worldState = worldState,
                    weatherState = weatherState,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // RIGHT COLUMN: Dedicated Controls & D-Pad Console Panel
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top: Compact Status & Vitals Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, CyberPrimary.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                        .background(CyberSurface)
                        .padding(6.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "░ DECK CHIP LVL ${playerStats.level} ░",
                                color = CyberPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "${playerStats.credits} ⛀",
                                color = CyberSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        ProgressBar("HP", playerStats.hp, playerStats.maxHp, CyberTertiary)
                        ProgressBar("SHD", playerStats.shield, playerStats.maxShield, CyberPrimary)
                    }
                }

                // Bottom: Full Dual Thumb Touch Controls Panel
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(BorderStroke(1.dp, CyberPrimary.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
                        .background(CyberSurface)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Thumb: Quick Utility Action Buttons
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Button(
                            onClick = { viewModel.useMedkit() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberTertiary.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, CyberTertiary),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("medkit_button")
                        ) {
                            Text("MEDKIT (${playerStats.medkits})", fontSize = 8.sp, color = CyberTertiary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = { viewModel.useShieldCell() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, CyberPrimary),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("shield_cell_button")
                        ) {
                            Text("SHIELD (${playerStats.shieldCells})", fontSize = 8.sp, color = CyberPrimary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Button(
                            onClick = { viewModel.toggleShop(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, CyberSecondary),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("BLACK_MKT", fontSize = 8.sp, color = CyberSecondary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Right Thumb: Ergonomic Continuous D-Pad + LIFT
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DPadButton("▲", "move_up") { viewModel.movePlayer(0, -1) }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DPadButton("◀", "move_left") { viewModel.movePlayer(-1, 0) }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CyberSecondary.copy(alpha = 0.15f))
                                    .border(BorderStroke(1.dp, CyberSecondary), RoundedCornerShape(6.dp))
                                    .clickable { viewModel.activateElevator() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("LIFT", fontSize = 9.sp, color = CyberSecondary, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                            DPadButton("▶", "move_right") { viewModel.movePlayer(1, 0) }
                        }
                        DPadButton("▼", "move_down") { viewModel.movePlayer(0, 1) }
                    }
                }
            }
        }
    }
}

// Map Swipe Control Modifier Helper
fun Modifier.mapSwipeControls(onSwipe: (dx: Int, dy: Int) -> Unit): Modifier = this.pointerInput(Unit) {
    var totalDx = 0f
    var totalDy = 0f
    val swipeThreshold = 20.dp.toPx()

    detectDragGestures(
        onDragStart = {
            totalDx = 0f
            totalDy = 0f
        },
        onDrag = { change, dragAmount ->
            change.consume()
            totalDx += dragAmount.x
            totalDy += dragAmount.y

            if (kotlin.math.abs(totalDx) > swipeThreshold || kotlin.math.abs(totalDy) > swipeThreshold) {
                if (kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy)) {
                    if (totalDx > 0) onSwipe(1, 0) else onSwipe(-1, 0)
                } else {
                    if (totalDy > 0) onSwipe(0, 1) else onSwipe(0, -1)
                }
                totalDx = 0f
                totalDy = 0f
            }
        }
    )
}

// Custom D-Pad continuous tactile button helper (Hold down to step continuously)
@Composable
fun DPadButton(label: String, testTag: String, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            onClick()
            delay(180) // Initial hold delay
            while (isPressed) {
                onClick()
                delay(110) // Continuous movement interval
            }
        }
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPressed) CyberPrimary.copy(alpha = 0.35f) else CyberPrimary.copy(alpha = 0.1f))
            .border(
                BorderStroke(
                    1.5.dp,
                    if (isPressed) CyberPrimary else CyberPrimary.copy(alpha = 0.5f)
                ),
                RoundedCornerShape(8.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    }
                )
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) Color.White else CyberPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// Custom status bars
@Composable
fun TopStatusBar(stats: PlayerStats, ws: WeatherState, world: WorldState) {
    val zoneName = when (world.zone) {
        ZoneType.BUILDING -> "BUILDING_ZONE_0${world.currentFloor}"
        ZoneType.COLLECTORS -> "COLLECT_ZONE_0${world.currentFloor}"
        ZoneType.CITY -> "CITY_ZONE_0${world.currentFloor}"
    }

    val isStorm = ws.condition == WeatherCondition.DATA_STORM
    val statusText = if (isStorm) "DATA STORM ACTIVE" else "SYS_SECURE_LINK"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0A0F))
            .border(BorderStroke(1.dp, CyberPrimary.copy(alpha = 0.2f)), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Vitals
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "VITALS",
                    fontSize = 9.sp,
                    color = CyberText.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Box(
                    modifier = Modifier
                        .width(30.dp)
                        .height(1.dp)
                        .background(CyberText.copy(alpha = 0.15f))
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(CyberTertiary, RoundedCornerShape(1.dp))
                    )
                    Text(
                        text = "${stats.hp} HP",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(CyberPrimary, RoundedCornerShape(1.dp))
                    )
                    Text(
                        text = "${stats.shield} EN",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Right side: Area / State
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = statusText,
                fontSize = 8.sp,
                color = if (isStorm) CyberWarning else CyberSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = zoneName,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// Horizontal standard progress bars
@Composable
fun ProgressBar(label: String, valNow: Int, valMax: Int, barColor: Color) {
    val ratio = if (valMax > 0) valNow.toFloat() / valMax.toFloat() else 0f
    val ratioClamped = ratio.coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 9.sp, color = CyberText, fontFamily = FontFamily.Monospace)
            Text(text = "$valNow/$valMax", fontSize = 9.sp, color = Color.White, fontFamily = FontFamily.Monospace)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .background(Color(0xFF14142B))
                .border(0.5.dp, Color.Gray, RoundedCornerShape(1.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(ratioClamped)
                    .background(barColor)
            )
        }
    }
}

fun getZoneMapColor(zone: ZoneType): Color {
    return when (zone) {
        ZoneType.BUILDING -> CyberPrimary
        ZoneType.COLLECTORS -> CyberWarning
        ZoneType.CITY -> CyberTertiary
    }
}

fun getLogColor(msg: String): Color {
    return when {
        msg.startsWith("WARNING") || msg.startsWith("STORM") -> CyberTertiary
        msg.startsWith("SYS_FAIL") -> CyberTertiary
        msg.startsWith("VICTORY") || msg.startsWith("SUCCESS") || msg.startsWith("SEC_PASS") -> CyberSecondary
        msg.startsWith("JACK_IN") || msg.startsWith("PURCHASED") -> CyberSecondary
        msg.startsWith("MOVE_ERR") || msg.startsWith("SEC_BLOCK") || msg.startsWith("USE_ERR") -> CyberWarning
        else -> CyberText
    }
}

// --- Combat Holographic Overlay (Landscape-aware) ---
@Composable
fun CombatOverlay(cs: CombatState, stats: PlayerStats, viewModel: GameViewModel, isLandscape: Boolean = false) {
    val enemy = cs.activeEnemy ?: return
    val consoleListState = rememberLazyListState()

    LaunchedEffect(cs.logs.size) {
        if (cs.logs.isNotEmpty()) {
            consoleListState.animateScrollToItem(cs.logs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEE04040A))
                .padding(if (isLandscape) 8.dp else 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.85f else 0.95f)
                    .verticalScroll(rememberScrollState())
                    .border(2.dp, CyberTertiary, RoundedCornerShape(8.dp))
                    .background(CyberSurface)
                    .padding(if (isLandscape) 10.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚔ SECURITY ENGAGEMENT CONSOLE ⚔",
                        color = CyberTertiary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "TURN: ${cs.turn.name}",
                        color = if (cs.turn == CombatTurn.PLAYER) CyberSecondary else CyberWarning,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                HorizontalDivider(color = CyberTertiary, thickness = 1.dp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberWarning, RoundedCornerShape(4.dp))
                        .background(CyberGridBg)
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            text = "HOSTILE CODES: ${enemy.name}",
                            color = CyberWarning,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        ProgressBar("HOSTILE HP", enemy.hp, enemy.maxHp, CyberTertiary)
                        ProgressBar("HOSTILE SHIELD", enemy.shield, enemy.maxShield, CyberPrimary)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 70.dp else 110.dp)
                        .border(1.dp, CyberPrimary, RoundedCornerShape(4.dp))
                        .background(CyberBg)
                        .padding(6.dp)
                ) {
                    LazyColumn(state = consoleListState, modifier = Modifier.fillMaxSize()) {
                        items(cs.logs) { log ->
                            Text(
                                text = "» $log",
                                color = getLogColor(log),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "DECK_HP: ${stats.hp}/${stats.maxHp}", color = CyberTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    Text(text = "DECK_SHD: ${stats.shield}/${stats.maxShield}", color = CyberPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                if (cs.turn == CombatTurn.PLAYER) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.executeCombatAction("QUICK_HACK") },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).testTag("quick_hack")
                            ) {
                                Text("QUICK HACK", fontSize = 11.sp, color = CyberBg, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.executeCombatAction("BRUTE_FORCE") },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberTertiary),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).testTag("brute_force")
                            ) {
                                Text("BRUTE FORCE", fontSize = 11.sp, color = CyberBg, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.executeCombatAction("SHIELD_BREAK") },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberPrimary),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f).testTag("shield_break")
                            ) {
                                Text("PULSE EMP", fontSize = 11.sp, color = CyberBg, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.useMedkit() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF882222)),
                                    contentPadding = PaddingValues(1.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("HEAL (${stats.medkits})", fontSize = 9.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { viewModel.useShieldCell() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF224488)),
                                    contentPadding = PaddingValues(1.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("SHD (${stats.shieldCells})", fontSize = 9.sp, color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(Color(0xFF161622)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "░ PROCESSING HOSTILE ACTION CYCLE ░",
                            color = CyberWarning,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// --- Breach Protocol Interactive Hacking Puzzle Overlay (Landscape-aware) ---
@Composable
fun HackingOverlay(hs: HackingMinigameState, stats: PlayerStats, viewModel: GameViewModel, isLandscape: Boolean = false) {
    Dialog(
        onDismissRequest = { viewModel.cancelHacking() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0040409))
                .padding(if (isLandscape) 8.dp else 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.85f else 0.95f)
                    .verticalScroll(rememberScrollState())
                    .border(2.dp, CyberPrimary, RoundedCornerShape(8.dp))
                    .background(CyberSurface)
                    .padding(if (isLandscape) 10.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "💾 BREACH_PROTOCOL // CRYPTO BYPASS",
                        color = CyberPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "BUFFER: ${hs.buffer.size}/4",
                        color = CyberSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                HorizontalDivider(color = CyberPrimary, thickness = 1.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CyberGridBg)
                        .border(0.5.dp, CyberPrimary, RoundedCornerShape(2.dp))
                        .padding(6.dp)
                ) {
                    Text(
                        text = "TARGET CHIP COMPILER SEQUENCE:",
                        color = CyberPrimary,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        hs.sequence.forEach { code ->
                            Box(
                                modifier = Modifier
                                    .border(1.dp, CyberSecondary, RoundedCornerShape(2.dp))
                                    .background(Color(0xFF0C1D0F))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = code,
                                    color = CyberSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Interactive Matrix
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberPrimary, RoundedCornerShape(4.dp))
                        .background(CyberGridBg)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (r in 0 until 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (c in 0 until 4) {
                                val cellVal = hs.matrix[r][c]
                                val cellPos = Pair(r, c)
                                val isSelected = hs.selectedCells.contains(cellPos)
                                val isCellSelectable = if (hs.isRowSelection) {
                                    r == hs.activeIndex
                                } else {
                                    c == hs.activeIndex
                                } && !isSelected

                                val borderStrokeColor = when {
                                    isSelected -> Color.Transparent
                                    isCellSelectable -> CyberSecondary
                                    else -> Color(0xFF1E1E3A)
                                }

                                val bgColour = when {
                                    isSelected -> CyberDarkMuted
                                    isCellSelectable -> Color(0xFF0F2414)
                                    else -> CyberBg
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(bgColour)
                                        .border(1.dp, borderStrokeColor, RoundedCornerShape(4.dp))
                                        .clickable(enabled = isCellSelectable) {
                                            viewModel.clickHackingCell(r, c)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cellVal,
                                        color = if (isCellSelectable) CyberSecondary else CyberText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.resetHackingMinigame() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberWarning),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("REBOOT (20 ⛀)", color = CyberBg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { viewModel.cancelHacking() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberTertiary),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ABORT BYPASS", color = CyberBg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- Black Market Shop (Proxy Console Dialog) (Landscape-aware) ---
@Composable
fun ShopOverlay(stats: PlayerStats, viewModel: GameViewModel, isLandscape: Boolean = false) {
    val items = listOf(
        Triple("MEDKIT", "Nanosuture Injector (+50 HP)", 30),
        Triple("SHIELD_CELL", "Electro Shield Cell (+40 EN)", 25),
        Triple("WEAPON_UPGRADE", "Overload Blade (+4 ATK)", 80),
        Triple("DECK_CHIP", "A.I. Chipset (+6 Hack)", 90),
        Triple("KEYCARD", "Gateway Override Keycard", 50)
    )

    Dialog(
        onDismissRequest = { viewModel.toggleShop(false) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xEE04040A))
                .padding(if (isLandscape) 8.dp else 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.85f else 0.95f)
                    .verticalScroll(rememberScrollState())
                    .border(2.dp, CyberSecondary, RoundedCornerShape(8.dp))
                    .background(CyberSurface)
                    .padding(if (isLandscape) 10.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🛒 BLACK_MARKET // COMM-LINK",
                        color = CyberSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "CREDITS: ${stats.credits} ⛀",
                        color = CyberSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                HorizontalDivider(color = CyberSecondary, thickness = 1.dp)

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items.forEach { (id, desc, cost) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, Color(0xFF264C30), RoundedCornerShape(4.dp))
                                .background(CyberGridBg)
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = id,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = desc,
                                    color = CyberText,
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Button(
                                onClick = { viewModel.buyShopItem(id, cost) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSecondary),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("$cost ⛀", color = CyberBg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Button(
                    onClick = { viewModel.toggleShop(false) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberTertiary),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("DECOUPLE SHOP LINK", color = CyberBg, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}


// --- Cyberpunk Pseudo-3D Isometric Level Map Canvas Engine ---
@Composable
fun Cyber2DMapCanvas(
    worldState: WorldState,
    weatherState: WeatherState,
    modifier: Modifier = Modifier
) {
    // 1. Smooth 120ms Movement Tweening for smooth 2.5D position transitions
    val animatedPlayerX by animateFloatAsState(
        targetValue = worldState.playerX.toFloat(),
        animationSpec = tween(durationMillis = 120, easing = LinearOutSlowInEasing),
        label = "player_x"
    )
    val animatedPlayerY by animateFloatAsState(
        targetValue = worldState.playerY.toFloat(),
        animationSpec = tween(durationMillis = 120, easing = LinearOutSlowInEasing),
        label = "player_y"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "map_anim")
    val pulseAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val rotateAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    val zoneThemeColor = when (worldState.zone) {
        ZoneType.BUILDING -> CyberPrimary
        ZoneType.COLLECTORS -> CyberWarning
        ZoneType.CITY -> CyberSecondary
    }

    // 2. Wall Occlusion Logic (Project Zomboid style: detects if player is behind a wall)
    val playerTileX = animatedPlayerX.toInt()
    val playerTileY = animatedPlayerY.toInt()
    val isPlayerBehindWall = worldState.walls.any { (wx, wy) ->
        val wallDepth = wx + wy
        val playerDepth = playerTileX + playerTileY
        wallDepth > playerDepth &&
        (wallDepth - playerDepth) <= 3 &&
        kotlin.math.abs((wx - wy) - (playerTileX - playerTileY)) <= 2
    }
    val heroAlpha = if (isPlayerBehindWall) 0.6f else 1.0f

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridW = worldState.width
            val gridH = worldState.height

            // Base isometric tile dimensions (2:1 classic perspective)
            val maxIsoSpan = gridW + gridH
            val baseIsoTileW = (size.width / (maxIsoSpan * 0.55f)).coerceIn(32f, 60f)
            val baseIsoTileH = baseIsoTileW * 0.5f
            val baseWallHeight = baseIsoTileH * 2.2f

            // Center camera smoothly around interpolated player position
            val centerX = size.width / 2f
            val centerY = size.height / 2f

            val playerIsoX = (animatedPlayerX - animatedPlayerY) * (baseIsoTileW / 2f)
            val playerIsoY = (animatedPlayerX + animatedPlayerY) * (baseIsoTileH / 2f)
            
            val camOffsetX = centerX - playerIsoX
            val camOffsetY = centerY - playerIsoY + baseIsoTileH * 1.5f

            // Background Digital Grid Lines
            val bgRows = (size.height / 24f).toInt()
            val bgCols = (size.width / 24f).toInt()
            for (r in 0..bgRows) {
                drawLine(
                    color = Color(0x0A00FFCC),
                    start = Offset(0f, r * 24f),
                    end = Offset(size.width, r * 24f),
                    strokeWidth = 1f
                )
            }
            for (c in 0..bgCols) {
                drawLine(
                    color = Color(0x0A00FFCC),
                    start = Offset(c * 24f, 0f),
                    end = Offset(c * 24f, size.height),
                    strokeWidth = 1f
                )
            }

            // Helper to build diamond polygon path
            fun drawDiamond(
                top: Offset, right: Offset, bottom: Offset, left: Offset,
                fillColor: Color, borderColor: Color? = null, borderWidth: Float = 1f
            ) {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(top.x, top.y)
                    lineTo(right.x, right.y)
                    lineTo(bottom.x, bottom.y)
                    lineTo(left.x, left.y)
                    close()
                }
                drawPath(path = p, color = fillColor)
                borderColor?.let {
                    drawPath(path = p, color = it, style = Stroke(width = borderWidth))
                }
            }

            // Helper to draw quad face
            fun drawQuad(
                p1: Offset, p2: Offset, p3: Offset, p4: Offset,
                fillColor: Color, borderColor: Color? = null, borderWidth: Float = 1f
            ) {
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    lineTo(p4.x, p4.y)
                    close()
                }
                drawPath(path = p, color = fillColor)
                borderColor?.let {
                    drawPath(path = p, color = it, style = Stroke(width = borderWidth))
                }
            }

            // Helper to render the Hero character sprite unit
            fun drawHeroUnit(hBx: Float, hBy: Float, hIsoW: Float, hIsoH: Float, alphaFactor: Float) {
                val heroH = hIsoH * 1.9f
                val perspDx = (hBx - centerX) * 0.06f
                val perspDy = (hBy - centerY) * 0.04f
                val heroCenter = Offset(hBx + perspDx, hBy - heroH + perspDy)

                // Hero Floor Shadow & Stealth/Shield Ring
                drawOval(
                    color = Color.Black.copy(alpha = 0.65f * alphaFactor),
                    topLeft = Offset(hBx - hIsoW * 0.4f, hBy - hIsoH * 0.25f),
                    size = androidx.compose.ui.geometry.Size(hIsoW * 0.8f, hIsoH * 0.5f)
                )
                drawCircle(
                    color = CyberPrimary.copy(alpha = (0.3f + 0.3f * pulseAnim) * alphaFactor),
                    radius = hIsoW * 0.5f,
                    center = Offset(hBx, hBy),
                    style = Stroke(width = 2f)
                )

                // Hero Cyber-Ninja Body
                val hTop = Offset(heroCenter.x, heroCenter.y - hIsoH * 0.45f)
                val hRight = Offset(heroCenter.x + hIsoW * 0.28f, heroCenter.y)
                val hBottom = Offset(heroCenter.x, heroCenter.y + hIsoH * 0.45f)
                val hLeft = Offset(heroCenter.x - hIsoW * 0.28f, heroCenter.y)

                drawDiamond(hTop, hRight, hBottom, hLeft, CyberPrimary.copy(alpha = alphaFactor), CyberSecondary.copy(alpha = alphaFactor), 2f)

                // Cyber Visor Head Unit
                drawCircle(CyberSecondary.copy(alpha = alphaFactor), hIsoW * 0.16f, heroCenter)
                drawCircle(Color.White.copy(alpha = alphaFactor), hIsoW * 0.07f, heroCenter)

                // Aiming Laser / Direction Line
                val dx = worldState.playerDirection.first
                val dy = worldState.playerDirection.second
                if (dx != 0 || dy != 0) {
                    val aimIsoX = (dx - dy) * (hIsoW * 0.4f)
                    val aimIsoY = (dx + dy) * (hIsoH * 0.4f)
                    drawLine(
                        color = Color.Yellow.copy(alpha = alphaFactor),
                        start = heroCenter,
                        end = Offset(heroCenter.x + aimIsoX, heroCenter.y + aimIsoY),
                        strokeWidth = 3f
                    )
                    drawCircle(Color.Yellow.copy(alpha = alphaFactor), 3.dp.toPx(), Offset(heroCenter.x + aimIsoX, heroCenter.y + aimIsoY))
                }
            }

            val heroBx = camOffsetX + (animatedPlayerX - animatedPlayerY) * (baseIsoTileW / 2f)
            val heroBy = camOffsetY + (animatedPlayerX + animatedPlayerY) * (baseIsoTileH / 2f)
            val heroDepthSum = (animatedPlayerX + animatedPlayerY).toInt()

            // 3. PAINTER'S ALGORITHM ORDER with Foreshortening Perspective Scaling
            for (sum in 0 until (gridW + gridH - 1)) {
                
                // Draw Hero at its exact depth order in the painter pass
                if (sum == heroDepthSum) {
                    val heroForeshortening = (1.0f + ((heroBy - centerY) * 0.0015f)).coerceIn(0.85f, 1.25f)
                    drawHeroUnit(heroBx, heroBy, baseIsoTileW * heroForeshortening, baseIsoTileH * heroForeshortening, heroAlpha)
                }

                for (x in 0..sum) {
                    val y = sum - x
                    if (x < 0 || x >= gridW || y < 0 || y >= gridH) continue

                    val pos = Pair(x, y)
                    val isExplored = worldState.explored.contains(pos)

                    // Base tile origin in isometric space
                    val bx = camOffsetX + (x - y) * (baseIsoTileW / 2f)
                    val by = camOffsetY + (x + y) * (baseIsoTileH / 2f)

                    // 3. Foreshortening Camera Scale: objects further down screen are slightly larger
                    val foreshorteningScale = (1.0f + ((by - centerY) * 0.0015f)).coerceIn(0.85f, 1.25f)
                    val isoTileW = baseIsoTileW * foreshorteningScale
                    val isoTileH = baseIsoTileH * foreshorteningScale
                    val wallHeight = baseWallHeight * foreshorteningScale

                    // Diamond floor vertices
                    val pTop = Offset(bx, by - isoTileH / 2f)
                    val pRight = Offset(bx + isoTileW / 2f, by)
                    val pBottom = Offset(bx, by + isoTileH / 2f)
                    val pLeft = Offset(bx - isoTileW / 2f, by)

                    // Culling: check if tile is near screen bounds
                    if (bx < -isoTileW * 2 || bx > size.width + isoTileW * 2 ||
                        by < -isoTileH * 3 || by > size.height + isoTileH * 3) {
                        continue
                    }

                    if (!isExplored) {
                        drawDiamond(pTop, pRight, pBottom, pLeft, Color(0xFF030308), Color(0x1000FFCC), 0.5f)
                        continue
                    }

                    val isWall = worldState.walls.contains(pos)

                    // GTA2 / MGS Perspective Shift Vector relative to camera center
                    val perspDx = (bx - centerX) * 0.06f
                    val perspDy = (by - centerY) * 0.04f

                    if (isWall) {
                        // --- MGS-LIKE PSEUDO-3D EXTRUDED WALL BLOCK ---
                        val rTop = Offset(pTop.x + perspDx, pTop.y - wallHeight + perspDy)
                        val rRight = Offset(pRight.x + perspDx, pRight.y - wallHeight + perspDy)
                        val rBottom = Offset(pBottom.x + perspDx, pBottom.y - wallHeight + perspDy)
                        val rLeft = Offset(pLeft.x + perspDx, pLeft.y - wallHeight + perspDy)

                        // 1. Left-Front Wall Face (Shadowed)
                        drawQuad(pLeft, pBottom, rBottom, rLeft, Color(0xFF0F172A), zoneThemeColor.copy(alpha = 0.3f), 1f)
                        
                        // 2. Right-Front Wall Face (Lit)
                        drawQuad(pBottom, pRight, rRight, rBottom, Color(0xFF1E293B), zoneThemeColor.copy(alpha = 0.3f), 1f)

                        // 3. Top Roof Face
                        drawDiamond(rTop, rRight, rBottom, rLeft, Color(0xFF0D1B2A), zoneThemeColor.copy(alpha = 0.6f), 1.5f)
                        
                        // Roof Inner Inset Pattern
                        val inTop = Offset(rTop.x, rTop.y + isoTileH * 0.15f)
                        val inRight = Offset(rRight.x - isoTileW * 0.15f, rRight.y)
                        val inBottom = Offset(rBottom.x, rBottom.y - isoTileH * 0.15f)
                        val inLeft = Offset(rLeft.x + isoTileW * 0.15f, rLeft.y)
                        drawDiamond(inTop, inRight, inBottom, inLeft, Color(0xFF1B263B), zoneThemeColor.copy(alpha = 0.4f), 1f)

                        // Vertical Edge Bevel Highlights
                        drawLine(Color.White.copy(alpha = 0.35f), pBottom, rBottom, strokeWidth = 2f)
                        drawLine(zoneThemeColor, rLeft, rBottom, strokeWidth = 1.5f)
                        drawLine(zoneThemeColor, rBottom, rRight, strokeWidth = 1.5f)

                    } else {
                        // --- PSEUDOISOMETRIC FLOOR TILE ---
                        drawDiamond(pTop, pRight, pBottom, pLeft, Color(0xFF080C16), Color(0xFF1E293B), 1f)
                        
                        drawCircle(
                            color = zoneThemeColor.copy(alpha = 0.2f),
                            radius = isoTileW * 0.08f,
                            center = Offset(bx, by)
                        )

                        // A) Elevator Portal
                        if (pos == worldState.exitPosition) {
                            val beamHeight = isoTileH * 2.5f
                            val topCenter = Offset(bx + perspDx, by - beamHeight + perspDy)
                            
                            drawCircle(CyberSecondary.copy(alpha = 0.25f * pulseAnim), isoTileW * 0.45f, Offset(bx, by))
                            drawCircle(CyberSecondary, isoTileW * 0.35f, Offset(bx, by), style = Stroke(width = 2f))

                            drawLine(CyberSecondary.copy(alpha = 0.8f), Offset(bx - isoTileW * 0.25f, by), Offset(topCenter.x - isoTileW * 0.25f, topCenter.y), strokeWidth = 2f)
                            drawLine(CyberSecondary.copy(alpha = 0.8f), Offset(bx + isoTileW * 0.25f, by), Offset(topCenter.x + isoTileW * 0.25f, topCenter.y), strokeWidth = 2f)

                            drawCircle(CyberSecondary, isoTileW * 0.35f, topCenter, style = Stroke(width = 2f))
                            drawCircle(CyberSecondary.copy(alpha = 0.3f), isoTileW * 0.45f, topCenter)
                        }

                        // B) Doors
                        worldState.doors[pos]?.let { door ->
                            val doorHeight = isoTileH * 1.8f
                            val doorTopLeft = Offset(pLeft.x + perspDx, pLeft.y - doorHeight + perspDy)
                            val doorTopRight = Offset(pRight.x + perspDx, pRight.y - doorHeight + perspDy)

                            if (door.isLocked) {
                                drawQuad(pLeft, pRight, doorTopRight, doorTopLeft, CyberTertiary.copy(alpha = 0.4f * pulseAnim), CyberTertiary, 2f)
                                drawLine(CyberTertiary, pLeft, doorTopLeft, strokeWidth = 3f)
                                drawLine(CyberTertiary, pRight, doorTopRight, strokeWidth = 3f)
                            } else {
                                drawLine(CyberSecondary, pLeft, doorTopLeft, strokeWidth = 2.5f)
                                drawLine(CyberSecondary, pRight, doorTopRight, strokeWidth = 2.5f)
                                drawLine(CyberSecondary, doorTopLeft, doorTopRight, strokeWidth = 1.5f)
                            }
                        }

                        // C) Loot Chests & Items
                        worldState.loot[pos]?.let { lootType ->
                            val lootColor = when (lootType) {
                                LootType.CREDITS -> Color(0xFFFFD700)
                                LootType.MEDKIT -> CyberTertiary
                                LootType.SHIELD_CELL -> CyberPrimary
                                LootType.WEAPON_MOD -> CyberWarning
                                LootType.KEYCARD -> Color(0xFFA855F7)
                            }

                            val hoverY = sin((rotateAnim * 0.05f).toDouble()).toFloat() * 4f
                            val chestH = isoTileH * 0.8f
                            val lootCenter = Offset(bx + perspDx, by - chestH - hoverY + perspDy)

                            drawOval(
                                color = Color.Black.copy(alpha = 0.5f),
                                topLeft = Offset(bx - isoTileW * 0.25f, by - isoTileH * 0.15f),
                                size = androidx.compose.ui.geometry.Size(isoTileW * 0.5f, isoTileH * 0.3f)
                            )

                            val cTop = Offset(lootCenter.x, lootCenter.y - isoTileH * 0.3f)
                            val cRight = Offset(lootCenter.x + isoTileW * 0.2f, lootCenter.y)
                            val cBottom = Offset(lootCenter.x, lootCenter.y + isoTileH * 0.3f)
                            val cLeft = Offset(lootCenter.x - isoTileW * 0.2f, lootCenter.y)

                            drawDiamond(cTop, cRight, cBottom, cLeft, lootColor, Color.White, 1.5f)
                            drawCircle(lootColor.copy(alpha = 0.3f * pulseAnim), isoTileW * 0.35f, lootCenter)
                        }

                        // D) Enemy Mechs
                        worldState.enemies[pos]?.let { enemy ->
                            val mechH = isoTileH * 1.6f
                            val enemyCenter = Offset(bx + perspDx, by - mechH + perspDy)

                            drawOval(
                                color = Color.Black.copy(alpha = 0.6f),
                                topLeft = Offset(bx - isoTileW * 0.35f, by - isoTileH * 0.2f),
                                size = androidx.compose.ui.geometry.Size(isoTileW * 0.7f, isoTileH * 0.4f)
                            )

                            val eTop = Offset(enemyCenter.x, enemyCenter.y - isoTileH * 0.4f)
                            val eRight = Offset(enemyCenter.x + isoTileW * 0.25f, enemyCenter.y)
                            val eBottom = Offset(enemyCenter.x, enemyCenter.y + isoTileH * 0.4f)
                            val eLeft = Offset(enemyCenter.x - isoTileW * 0.25f, enemyCenter.y)

                            drawDiamond(eTop, eRight, eBottom, eLeft, CyberTertiary, Color.White, 1.5f)
                            
                            drawCircle(Color.Red, isoTileW * 0.12f, enemyCenter)
                            drawCircle(Color.Yellow, isoTileW * 0.05f, enemyCenter)

                            val hpRatio = (enemy.hp.toFloat() / enemy.maxHp.toFloat()).coerceIn(0f, 1f)
                            val barW = isoTileW * 0.8f
                            val barH = 4.dp.toPx()
                            val barLeft = enemyCenter.x - barW / 2f
                            val barTop = enemyCenter.y - isoTileH * 0.6f

                            drawRect(Color.Black, Offset(barLeft, barTop), androidx.compose.ui.geometry.Size(barW, barH))
                            drawRect(
                                color = if (hpRatio > 0.5f) CyberSecondary else CyberTertiary,
                                topLeft = Offset(barLeft, barTop),
                                size = androidx.compose.ui.geometry.Size(barW * hpRatio, barH)
                            )
                        }
                    }
                }
            }

            // Project Zomboid style X-Ray Translucency Ghost Overlay:
            // If player is behind a wall, re-draw a translucent 0.6f alpha hero on top of walls
            if (isPlayerBehindWall) {
                val heroForeshortening = (1.0f + ((heroBy - centerY) * 0.0015f)).coerceIn(0.85f, 1.25f)
                drawHeroUnit(heroBx, heroBy, baseIsoTileW * heroForeshortening, baseIsoTileH * heroForeshortening, 0.6f)
            }

            // Dynamic Weather Rain Streaks
            if (weatherState.condition == WeatherCondition.GLITCH_RAIN || weatherState.condition == WeatherCondition.DATA_STORM) {
                val rainColor = Color(0x40CEF7FF)
                for (i in 0..14) {
                    val rx = ((i * 79 + (rotateAnim * 4).toInt()) % size.width.toInt()).toFloat()
                    val ry = ((i * 47 + (rotateAnim * 8).toInt()) % size.height.toInt()).toFloat()
                    drawLine(
                        color = rainColor,
                        start = Offset(rx, ry),
                        end = Offset(rx - 12f, ry + 26f),
                        strokeWidth = 1.8f
                    )
                }
            }
        }

        // Floating Micro 2D Tactical Minimap Overlay (Top-Right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                .border(BorderStroke(1.dp, CyberTertiary.copy(alpha = 0.6f)), RoundedCornerShape(8.dp))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "3D_ISOMAP",
                    color = CyberTertiary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp),
                    letterSpacing = 0.5.sp
                )
                Canvas(
                    modifier = Modifier.size(75.dp)
                ) {
                    val mSize = minOf(size.width, size.height)
                    val mTile = mSize / worldState.width
                    for (mx in 0 until worldState.width) {
                        for (my in 0 until worldState.height) {
                            val pos = Pair(mx, my)
                            val isExplored = worldState.explored.contains(pos)
                            val isWall = worldState.walls.contains(pos)
                            val color = when {
                                !isExplored -> Color(0xFF080812)
                                mx == worldState.playerX && my == worldState.playerY -> CyberPrimary
                                worldState.enemies.containsKey(pos) -> CyberTertiary
                                worldState.loot.containsKey(pos) -> Color(0xFFFFD700)
                                pos == worldState.exitPosition -> CyberSecondary
                                isWall -> Color(0xFF20223A)
                                else -> Color(0xFF0A151D)
                            }
                            drawRect(
                                color = color,
                                topLeft = Offset(mx * mTile, my * mTile),
                                size = androidx.compose.ui.geometry.Size(mTile - 0.5f, mTile - 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}






