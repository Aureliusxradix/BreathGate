package dev.breathgate

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Test run — the gate, on demand, without waiting to catch yourself opening something.
 * Same view and same settings as the real thing, so what you tune is what you get.
 */
class TestRunActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = Prefs(this)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        val container = FrameLayout(this)
        val breath = BreathView(this, p) { }
        container.addView(breath)
        container.setBackgroundColor(Palette.current(p).bg)
        setContentView(container)

        // The real gate ends in a decision; a test run ends by handing the screen back —
        // same opening animation, so the timing being tuned is the timing that ships.
        breath.postDelayed({
            breath.openAndThen {
                container.animate().alpha(0f).setDuration(220)
                    .withEndAction { finish(); overridePendingTransition(0, 0) }.start()
            }
        }, (p.totalSeconds * 1000L) + 1400L)
    }
}
