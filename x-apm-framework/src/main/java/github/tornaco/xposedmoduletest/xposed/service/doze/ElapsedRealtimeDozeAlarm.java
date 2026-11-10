package github.tornaco.xposedmoduletest.xposed.service.doze;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;

import java.util.UUID;

@TargetApi(Build.VERSION_CODES.M)
public class ElapsedRealtimeDozeAlarm {

    private static final String EXTRA_GENERATION = "generation";

    private final Context context;
    private final Handler handler;
    private final Runnable callback;
    private final String action = "github.tornaco.xposedmoduletest.DOZE_ELAPSED_REALTIME."
            + UUID.randomUUID();

    private PendingIntent pendingIntent;
    private long generation;
    private boolean receiverRegistered;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (ElapsedRealtimeDozeAlarm.this) {
                if (!action.equals(intent.getAction()) || pendingIntent == null
                        || intent.getLongExtra(EXTRA_GENERATION, -1) != generation) {
                    return;
                }
                pendingIntent.cancel();
                pendingIntent = null;
            }
            callback.run();
        }
    };

    public ElapsedRealtimeDozeAlarm(Context context, Handler handler, Runnable callback) {
        this.context = context;
        this.handler = handler;
        this.callback = callback;
    }

    public synchronized void schedule(long delayMillis) {
        cancel();
        if (!receiverRegistered) {
            context.registerReceiver(receiver, new IntentFilter(action),
                    Manifest.permission.DEVICE_POWER, handler);
            receiverRegistered = true;
        }

        Intent intent = new Intent(action)
                .setPackage(context.getPackageName())
                .putExtra(EXTRA_GENERATION, generation);
        pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + Math.max(0L, delayMillis), pendingIntent);
        } catch (RuntimeException exception) {
            pendingIntent.cancel();
            pendingIntent = null;
            throw exception;
        }
    }

    public synchronized void cancel() {
        generation++;
        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            pendingIntent = null;
        }
    }
}
