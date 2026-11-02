package com.umbra.client

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import java.net.InetSocketAddress
import java.net.Socket

/** Draws the floating shortcut, quick-toggle panel and HUD above any app, including Minecraft. */
class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var hp: WindowManager.LayoutParams
    private val h = Handler(Looper.getMainLooper())
    private var bubble: ImageView? = null
    private var hud: TextView? = null
    private var cross: View? = null
    private var panel: View? = null
    private var ping = "…"
    private var n = 0

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("umbra", "Umbra", NotificationManager.IMPORTANCE_LOW))
        val nt = Notification.Builder(this, "umbra").setContentTitle("Umbra shortcut is on")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, nt, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, nt)
        if (bubble == null) { build(); h.post(tick) }
        return START_STICKY
    }

    private fun lp(w: Int, hh: Int, touch: Boolean) = WindowManager.LayoutParams(
        w, hh, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            (if (touch) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun build() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val d = resources.displayMetrics.density
        val s = (Prefs.size(this) * d).toInt()
        val bp = lp(s, s, true).apply { x = (24 * d).toInt(); y = (120 * d).toInt() }
        val b = ImageView(this).apply {
            setImageResource(R.drawable.logo); scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = .92f; clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) { o.setOval(0, 0, v.width, v.height) }
            }
        }
        var ox = 0f; var oy = 0f; var sx = 0; var sy = 0; var mv = false
        b.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = bp.x; sy = bp.y; ox = e.rawX; oy = e.rawY; mv = false }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX - ox) > 12 || Math.abs(e.rawY - oy) > 12) mv = true
                    if (mv) { bp.x = sx + (e.rawX - ox).toInt(); bp.y = sy + (e.rawY - oy).toInt(); wm.updateViewLayout(b, bp) }
                }
                MotionEvent.ACTION_UP -> if (!mv) togglePanel(bp)
            }
            true
        }
        hp = lp(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, false)
            .apply { x = (16 * d).toInt(); y = (12 * d).toInt() }
        val t = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 12f
            setPadding((10 * d).toInt(), (6 * d).toInt(), (10 * d).toInt(), (6 * d).toInt())
            background = GradientDrawable().apply { setColor(0x99000000.toInt()); cornerRadius = 14 * d; setStroke(2, 0x55FFFFFF) }
            visibility = View.GONE
        }
        wm.addView(t, hp); hud = t
        wm.addView(b, bp); bubble = b
    }

    private fun togglePanel(bp: WindowManager.LayoutParams) {
        if (panel != null) { panel?.let { wm.removeView(it) }; panel = null; return }
        val d = resources.displayMetrics.density
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * d).toInt(), (10 * d).toInt(), (14 * d).toInt(), (10 * d).toInt())
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xEE4A4A4A.toInt(), 0xEE000000.toInt())).apply { cornerRadius = 24 * d; setStroke(2, 0x66FFFFFF) }
        }
        MODS.forEach { m ->
            p.addView(Switch(this).apply {
                text = m.name; setTextColor(Color.WHITE)
                isChecked = Prefs.on(this@OverlayService, m.id)
                setOnCheckedChangeListener { _, v -> Prefs.set(this@OverlayService, m.id, v) }
            })
        }
        p.addView(TextView(this).apply {
            text = "Open Umbra"; setTextColor(Color.WHITE); setPadding(0, (10 * d).toInt(), 0, 0)
            setOnClickListener { startActivity(Intent(this@OverlayService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        })
        val pp = lp(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, true)
            .apply { x = bp.x + (bubble?.width ?: 0) + (16 * d).toInt(); y = bp.y }
        wm.addView(p, pp); panel = p
    }

    private val tick = object : Runnable {
        override fun run() {
            val c = this@OverlayService
            val l = ArrayList<String>()
            val bi = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (Prefs.on(c, "clock")) l += java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            if (Prefs.on(c, "battery") && bi != null) l += "Battery " + bi.getIntExtra("level", 0) * 100 / bi.getIntExtra("scale", 100) + "%"
            if (Prefs.on(c, "temp") && bi != null) l += "Temp " + bi.getIntExtra("temperature", 0) / 10f + " °C"
            if (Prefs.on(c, "ram")) {
                val mi = ActivityManager.MemoryInfo()
                getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
                l += "RAM " + mi.availMem / 1048576 + " MB free"
            }
            if (Prefs.on(c, "ping")) {
                if (n++ % 3 == 0) Thread {
                    ping = try {
                        val t0 = System.nanoTime()
                        Socket().use { it.connect(InetSocketAddress("1.1.1.1", 53), 1500) }
                        ((System.nanoTime() - t0) / 1000000).toString() + " ms"
                    } catch (e: Exception) { "—" }
                }.start()
                l += "Ping $ping"
            }
            hud?.apply { text = l.joinToString("\n"); visibility = if (l.isEmpty()) View.GONE else View.VISIBLE }
            val keep = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            val aw = Prefs.on(c, "awake"); val has = (hp.flags and keep) != 0
            if (aw != has) {
                hp.flags = if (aw) hp.flags or keep else hp.flags and keep.inv()
                hud?.let { wm.updateViewLayout(it, hp) }
            }
            val cr = Prefs.on(c, "crosshair")
            if (cr && cross == null) {
                val d = resources.displayMetrics.density
                val v = View(c).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE); setStroke(2, Color.BLACK) } }
                wm.addView(v, lp((7 * d).toInt(), (7 * d).toInt(), false).apply { gravity = Gravity.CENTER })
                cross = v
            }
            if (!cr && cross != null) { cross?.let { wm.removeView(it) }; cross = null }
            h.postDelayed(this, 1000)
        }
    }

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        listOf(bubble, hud, cross, panel).forEach { v -> v?.let { try { wm.removeView(it) } catch (e: Exception) {} } }
        super.onDestroy()
    }
}
