// SPDX-License-Identifier: GPL-3.0-only
package dev.puitheme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restore access to enabled plugins using device protected storage before the settings UI opens. */
public final class CustomPluginBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String action=intent==null?null:intent.getAction();
        if(ModuleVariant.DABING||(!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                &&!Intent.ACTION_BOOT_COMPLETED.equals(action)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)))return;
        PendingResult pending=goAsync();
        CustomPluginVisibility.start(context,pending::finish);
    }
}
