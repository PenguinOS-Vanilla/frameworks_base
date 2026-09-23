package com.android.systemui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

/**
 * Lets the wallpaper picker's lock screen gallery restart SystemUI after switching between the
 * default clock and a custom clock style, which parts of the keyguard only read at startup.
 */
public class LockScreenClockRestartReceiver extends BroadcastReceiver {

    public static final String ACTION = "com.android.systemui.action.RESTART_FOR_CLOCK_STYLE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION.equals(intent.getAction())) {
            Process.killProcess(Process.myPid());
        }
    }
}
