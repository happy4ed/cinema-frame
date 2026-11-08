package com.happy.cinemaframe;
import android.content.*;
/** Restore background indexing only; never steal foreground from the photo app. */
public class BootReceiver extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){if(Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()))c.startForegroundService(new Intent(c,FrameService.class));}
}
