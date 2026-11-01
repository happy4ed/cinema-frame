package com.happy.cinemaframe;
import android.app.*;import android.content.*;import android.os.IBinder;
public class FrameService extends Service {
 public void onCreate(){super.onCreate();NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);n.createNotificationChannel(new NotificationChannel("cinema", "프레임 연결",NotificationManager.IMPORTANCE_LOW));PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);startForeground(1,new Notification.Builder(this,"cinema").setContentTitle("Cinema Frame").setContentText("휴대폰 연결을 기다리는 중").setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(open).build());FrameCore.get(this);}
 public int onStartCommand(Intent i,int flags,int id){return START_STICKY;}public IBinder onBind(Intent i){return null;}
}
