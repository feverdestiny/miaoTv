package com.feverdestiny.miaotv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.feverdestiny.miaotv.activities.LeanbackActivity
import com.feverdestiny.miaotv.data.work.EpgRefreshWorkScheduler
import com.feverdestiny.miaotv.ui.utils.SP

/**
 * 开机自启动监听
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            SP.init(context.applicationContext)
            EpgRefreshWorkScheduler.schedule(context.applicationContext)

            val sp: SharedPreferences = SP.getInstance(context)
            val bootLaunch = sp.getBoolean(SP.KEY.APP_BOOT_LAUNCH.name, false)

            if (bootLaunch) {
                context.startActivity(Intent(context, LeanbackActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }
}
