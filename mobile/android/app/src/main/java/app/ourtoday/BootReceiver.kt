package app.ourtoday

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 재부팅 후 위치 공유가 켜져 있으면 loose 수집 복구 (빠르게 반환) */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        LocationEngine.requestArm(context)
    }
}
