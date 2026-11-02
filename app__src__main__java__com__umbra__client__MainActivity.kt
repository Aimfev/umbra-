package com.umbra.client

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

const val MC = "com.mojang.minecraftpe"
val Soft = Color(0xB3FFFFFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }
}

fun Modifier.glass(r: Dp = 24.dp) = this
    .clip(RoundedCornerShape(r))
    .background(Brush.linearGradient(listOf(Color(0x47FFFFFF), Color(0x14FFFFFF))))
    .border(1.dp, Color(0x59FFFFFF), RoundedCornerShape(r))

fun startOverlay(c: Context): Boolean {
    if (!Settings.canDrawOverlays(c)) {
        c.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName)))
        Toast.makeText(c, "Allow \"Display over other apps\", then tap again", Toast.LENGTH_LONG).show()
        return false
    }
    c.startForegroundService(Intent(c, OverlayService::class.java))
    return true
}

fun launchMinecraft(c: Context) {
    if (!startOverlay(c)) return
    val i = c.packageManager.getLaunchIntentForPackage(MC)
    if (i == null) Toast.makeText(c, "Minecraft isn't installed", Toast.LENGTH_LONG).show() else c.startActivity(i)
}

@Composable
fun App() {
    val c = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    val on = remember { mutableStateMapOf<String, Boolean>().apply { MODS.forEach { put(it.id, Prefs.on(c, it.id)) } } }
    val lo = LocalLifecycleOwner.current
    DisposableEffect(lo) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) MODS.forEach { on[it.id] = Prefs.on(c, it.id) } }
        lo.lifecycle.addObserver(ob); onDispose { lo.lifecycle.removeObserver(ob) }
    }
    Box(Modifier.fillMaxSize()
        .background(Brush.linearGradient(listOf(Color(0xFFF2F2F2), Color(0xFF6B6B6B), Color(0xFF050505)), Offset(0f, 0f), Offset(2200f, 1500f)))
        .windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxHeight().glass(32.dp).padding(8.dp), verticalArrangement = Arrangement.Center) {
                listOf("Home", "Modules", "Boost", "Settings").forEachIndexed { i, t ->
                    val sel = tab == i
                    val bg by animateColorAsState(if (sel) Color.White else Color.Transparent, tween(300), label = "bg")
                    Text(t, color = if (sel) Color.Black else Soft, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(bg).clickable { tab = i }
                            .padding(horizontal = 14.dp, vertical = 12.dp))
                }
            }
            AnimatedContent(tab, modifier = Modifier.weight(1f), label = "tab",
                transitionSpec = { (fadeIn(tween(380)) + slideInHorizontally(tween(380)) { it / 10 }) togetherWith fadeOut(tween(180)) }) { t ->
                when (t) {
                    0 -> Home(on)
                    1 -> Modules(on)
                    2 -> Boost()
                    else -> SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun PressButton(t: String, ghost: Boolean = false, go: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pr by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pr) 0.95f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "s")
    val fill = if (ghost) Brush.linearGradient(listOf(Color(0x47FFFFFF), Color(0x1FFFFFFF)))
    else Brush.verticalGradient(listOf(Color.White, Color(0xFFCFCFCF)))
    Box(Modifier.fillMaxWidth().scale(sc).clip(RoundedCornerShape(20.dp)).background(fill)
        .clickable(interactionSource = src, indication = null, onClick = go).padding(16.dp), contentAlignment = Alignment.Center) {
        Text(t, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = if (ghost) Color.White else Color.Black)
    }
}

@Composable
fun Home(on: Map<String, Boolean>) {
    val c = LocalContext.current
    val inf = rememberInfiniteTransition(label = "f")
    val dy by inf.animateFloat(0f, -8f, infiniteRepeatable(tween(2500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight().glass().padding(20.dp), verticalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.logo), null, Modifier.size(92.dp).offset(y = dy.dp).clip(RoundedCornerShape(26.dp)))
            Spacer(Modifier.height(14.dp))
            Text("Umbra", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Client for Minecraft Bedrock", color = Soft)
            Spacer(Modifier.height(6.dp))
            Text("${on.values.count { it }} modules on", color = Soft)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            PressButton("Launch Minecraft") { launchMinecraft(c) }
            Spacer(Modifier.height(12.dp))
            PressButton("Start floating shortcut", true) { startOverlay(c) }
            Spacer(Modifier.height(12.dp))
            PressButton("Stop floating shortcut", true) { c.stopService(Intent(c, OverlayService::class.java)) }
        }
    }
}

@Composable
fun Modules(on: MutableMap<String, Boolean>) {
    val c = LocalContext.current
    LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        itemsIndexed(MODS) { i, m ->
            val v = on[m.id] == true
            var show by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(i * 55L); show = true }
            val a by animateFloatAsState(if (show) 1f else 0f, tween(420), label = "a")
            val bg by animateColorAsState(if (v) Color(0xE6FFFFFF) else Color(0x22FFFFFF), tween(250), label = "b")
            val fg = if (v) Color.Black else Color.White
            Column(Modifier.graphicsLayer { alpha = a; scaleX = .9f + .1f * a; scaleY = .9f + .1f * a; translationY = (1 - a) * 40f }
                .clip(RoundedCornerShape(22.dp)).background(bg).border(1.dp, Color(0x59FFFFFF), RoundedCornerShape(22.dp))
                .clickable { on[m.id] = !v; Prefs.set(c, m.id, !v) }.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Switch(v, null)
                }
                Spacer(Modifier.height(6.dp))
                Text(m.desc, color = fg.copy(alpha = .65f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun Boost() {
    val c = LocalContext.current
    fun open(a: String, pkg: String? = null) {
        try { c.startActivity(Intent(a, pkg?.let { Uri.parse("package:$it") })) }
        catch (e: Exception) { Toast.makeText(c, "Can't open that screen on this phone", Toast.LENGTH_SHORT).show() }
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1.3f).fillMaxHeight().glass().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("For more FPS in Minecraft", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            listOf("Render distance: 6 to 8 chunks", "Turn off Fancy Graphics and Beautiful Skies",
                "Lower particles and turn off smooth lighting", "Match the game's FPS to your screen's refresh rate",
                "Close other apps and turn off battery saver").forEach { Text("•  $it", color = Soft, fontSize = 14.sp) }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            PressButton("Minecraft app settings", true) { open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, MC) }
            Spacer(Modifier.height(12.dp))
            PressButton("Screen refresh rate", true) { open(Settings.ACTION_DISPLAY_SETTINGS) }
            Spacer(Modifier.height(12.dp))
            PressButton("Battery optimization", true) { open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }
        }
    }
}

@Composable
fun SettingsScreen() {
    val c = LocalContext.current
    var sz by remember { mutableStateOf(Prefs.size(c).toFloat()) }
    Column(Modifier.fillMaxSize().glass().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Shortcut size: ${sz.toInt()}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Slider(sz, { sz = it; Prefs.setSize(c, it.toInt()) }, valueRange = 40f..80f)
        Text("Stop and start the shortcut again to apply the new size.", color = Soft, fontSize = 13.sp)
    }
}
