package app.cuisson.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Where a timer reaching zero arrives.
 *
 * Android starts this even when Cuisson is not running, which is the whole reason the
 * countdown is an alarm rather than a loop in the cooking screen.
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        KitchenTimer.finish(context.applicationContext, id)
    }

    companion object {
        const val ACTION = "app.cuisson.TIMER"
        const val EXTRA_ID = "timer"
    }
}
