package com.kanak.app.performance

import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewTreeObserver
import android.view.Window
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kanak.app.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONArray
import org.json.JSONObject

/** Test-only observation of real input dispatch; no production hooks or expense writes. */
class InputLatencyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private data class Sample(val queuedMs: Long, val dispatchMs: Long, val nextDrawMs: Long)
    private class Pending(val eventAt: Long, val arrivedAt: Long, val latch: CountDownLatch) {
        @Volatile var returnedAt = arrivedAt
        @Volatile var drawnAt = arrivedAt
    }

    @Test fun measureNavigationTapDeliveryAndNextDraw() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = compose.activity
        val window = activity.window
        val original = window.callback
        val pending = AtomicReference<Pending?>()
        val samples = mutableListOf<Pair<String, Sample>>()
        val callback = object : Window.Callback by original {
            override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked != MotionEvent.ACTION_UP) return original.dispatchTouchEvent(event)
                val record = Pending(event.eventTime, SystemClock.uptimeMillis(), CountDownLatch(1))
                pending.set(record)
                val observer = window.decorView.viewTreeObserver
                val listener = object : ViewTreeObserver.OnDrawListener {
                    private var completed = false
                    override fun onDraw() {
                        if (completed) return
                        completed = true
                        record.drawnAt = SystemClock.uptimeMillis()
                        record.latch.countDown()
                        window.decorView.post { if (observer.isAlive) observer.removeOnDrawListener(this) }
                    }
                }
                observer.addOnDrawListener(listener)
                return try { original.dispatchTouchEvent(event) } finally { record.returnedAt = SystemClock.uptimeMillis() }
            }
        }
        compose.runOnUiThread { window.callback = callback }
        try {
            // First cycle captures cold navigation; later cycles include warmed navigation.
            repeat(6) { round ->
                for (tab in listOf("Transactions", "Analytics", "Settings", "Home")) {
                    compose.waitForIdle()
                    val node = compose.onNode(hasText(tab) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
                        .fetchSemanticsNode()
                    val center = node.boundsInWindow.center
                    val offset = IntArray(2)
                    compose.runOnUiThread { window.decorView.getLocationOnScreen(offset) }
                    pending.set(null)
                    val downAt = SystemClock.uptimeMillis()
                    fun inject(action: Int, at: Long) {
                        val event = MotionEvent.obtain(downAt, at, action, center.x + offset[0], center.y + offset[1], 0)
                        event.source = InputDevice.SOURCE_TOUCHSCREEN
                        try { assertTrue("Input injection failed", instrumentation.uiAutomation.injectInputEvent(event, true)) }
                        finally { event.recycle() }
                    }
                    inject(MotionEvent.ACTION_DOWN, downAt)
                    SystemClock.sleep(30)
                    inject(MotionEvent.ACTION_UP, SystemClock.uptimeMillis())
                    val record = requireNotNull(pending.get()) { "Activity did not receive injected tap" }
                    assertTrue("No draw after tap", record.latch.await(5, TimeUnit.SECONDS))
                    compose.waitForIdle()
                    samples += "$round:$tab" to Sample(
                        record.arrivedAt - record.eventAt,
                        record.returnedAt - record.arrivedAt,
                        record.drawnAt - record.eventAt
                    )
                }
            }
        } finally { compose.runOnUiThread { window.callback = original } }
        val output = JSONObject()
            .put("run_id", InstrumentationRegistry.getArguments().getString("kanakLatencyRunId", "ci"))
            .put("device", android.os.Build.MODEL)
            .put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("build_type", "debug")
            .put("injected_taps", true)
            .put("note", "Input event time to Activity delivery, Window dispatch duration, and next View draw. Next draw is a lower bound, not confirmed changed-content presentation or physical touch-to-photon latency. Debug/emulator overhead included. No user expenses are written.")
        val rows = JSONArray()
        for ((name, sample) in samples) {
            rows.put(JSONObject().put("sample", name).put("delivery_ms", sample.queuedMs)
                .put("dispatch_ms", sample.dispatchMs).put("next_draw_ms", sample.nextDrawMs))
        }
        output.put("samples", rows)
        Log.i("KANAK_INPUT_TIMING", output.toString())
        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "\nKANAK_INPUT_TIMING " + output + "\n") })
        assertEquals(24, samples.size)
    }
}
