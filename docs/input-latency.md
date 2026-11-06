# Input timing

InputLatencyTest injects 24 taps in the real MainActivity: Transactions → Analytics → Settings → Home, six cycles. A test-only Window callback observes input. Production app code stays unchanged; no expenses are created, edited or deleted.

Metrics use Android uptime in milliseconds:
- delivery_ms: ACTION_UP timestamp to Activity Window callback entry. Includes synthetic injection and input dispatch overhead, excludes physical digitizer/firmware latency.
- dispatch_ms: duration of delegated Window.dispatchTouchEvent. Compose can work asynchronously afterward; this does not guarantee click-handler completion.
- next_draw_ms: event timestamp to next View draw callback. A ripple or unrelated invalidation can draw first. This is a lower bound, NOT confirmed changed-content presentation or touch-to-photon latency.

Debug, instrumentation, emulator and host overhead affect results. CI describes its emulator, not the M34. Raw values and first/warm cycle summaries are retained. No invented target or threshold is imposed.

To measure the connected/authorized M34 from Android Studio's Terminal:

    bash scripts/measure-input-latency.sh

This builds/installs the debug and test APKs, then runs only the navigation test. It does not clear app data or uninstall first. Existing migrations may run if updating an older build. Only the dedicated timing log is read; no screenshots, expense details or general device logs are collected.

ADB must actually reach the phone from the machine running this command. Repository permissions do not connect ChatGPT to a phone on the user's Mac. Full visual-response measurement still requires a system trace connecting input to the changed-content frame. Never present next_draw_ms as that measurement.
