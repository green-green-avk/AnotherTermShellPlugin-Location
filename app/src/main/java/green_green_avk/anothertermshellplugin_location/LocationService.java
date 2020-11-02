package green_green_avk.anothertermshellplugin_location;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import java.util.HashSet;
import java.util.Set;

public final class LocationService extends Service {

    private static final int ID_FG = 1;
    private static final String NOTIFICATION_CHANNEL_ID = LocationService.class.getName();

    private static volatile LocationService instance = null;

    public interface OnStateChanged {
        void onStateChanged(boolean isRunning);
    }

    public interface OnStateChangedClient {
        void onEnabled();

        void onDisabled();

        void onAdd();

        void onRemove();
    }

    public interface OnLocation {
        void onLocationChanged(@Nullable Location location);

        void onEnabled();

        void onDisabled();

        void onException(@NonNull Exception e);
    }

    public static OnStateChanged onStateChanged = null;

    private static final Set<OnStateChangedClient> onStateChangedClients = new HashSet<>();

    private static final Object lock = new Object();

    private void tryFg() {
        final TaskStackBuilder tsb = TaskStackBuilder.create(this)
                .addNextIntentWithParentStack(
                        new Intent(this, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final NotificationChannel nc = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(nc);
        }
        final Notification n = new NotificationCompat.Builder(this,
                NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getString(R.string.stat_loc))
                .setSmallIcon(R.drawable.ic_stat_loc)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(tsb.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT))
                .build();
        startForeground(ID_FG, n);
    }

    public static boolean isRunning() {
        return instance != null;
    }

    public static void tryStart(@NonNull final Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ctx.startForegroundService(
                    new Intent(ctx, LocationService.class));
        else
            ctx.startService(new Intent(ctx, LocationService.class));
    }

    public static void tryStop() {
        if (instance != null) instance.stopSelf();
    }

    @Nullable
    public static Location getLocation(@NonNull final Context ctx) {
        synchronized (lock) {
            if (instance == null)
                throw new SecurityException(ctx.getString(R.string.msg_disabled));
            final LocationManager lm =
                    (LocationManager) instance.getSystemService(Context.LOCATION_SERVICE);
            try {
                return lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } catch (final SecurityException e) {
                throw new SecurityException(ctx.getString(R.string.msg_location_perm_not_granted), e);
            }
        }
    }

    @CheckResult
    @NonNull
    public static OnStateChangedClient notifyLocation(@NonNull final Context ctx,
                                                      final long minInterval,
                                                      final float minDistance,
                                                      @NonNull final OnLocation listener,
                                                      @NonNull final Looper looper) {
        final Context appCtx = ctx.getApplicationContext();
        final LocationListener ll = new LocationListener() {
            @Override
            public void onLocationChanged(final Location location) {
                listener.onLocationChanged(location);
            }

            @Override
            public void onStatusChanged(final String provider, final int status, final Bundle extras) {
            }

            @Override
            public void onProviderEnabled(final String provider) {
            }

            @Override
            public void onProviderDisabled(final String provider) {
            }
        };
        final OnStateChangedClient h = new OnStateChangedClient() {
            final Handler handler = new Handler(looper);

            @Override
            public void onEnabled() {
                handler.post(listener::onEnabled);
            }

            @Override
            public void onDisabled() {
                handler.post(listener::onDisabled);
            }

            @Override
            public void onAdd() {
                try {
                    final LocationManager lm =
                            (LocationManager) instance.getSystemService(Context.LOCATION_SERVICE);
                    try {
                        lm.requestLocationUpdates(
                                LocationManager.GPS_PROVIDER, minInterval, minDistance,
                                ll, looper);
                    } catch (final SecurityException e) {
                        throw new SecurityException(appCtx.getString(R.string.msg_location_perm_not_granted), e);
                    }
                } catch (final Exception e) {
                    handler.post(() -> listener.onException(e));
                }
            }

            @Override
            public void onRemove() {
                try {
                    final LocationManager lm =
                            (LocationManager) instance.getSystemService(Context.LOCATION_SERVICE);
                    lm.removeUpdates(ll);
                } catch (final Exception e) {
                    handler.post(() -> listener.onException(e));
                }
            }
        };
        addStateNotification(h);
        return h;
    }

    public static void addStateNotification(@NonNull final OnStateChangedClient listener) {
        synchronized (lock) {
            if (instance != null)
                listener.onAdd();
            onStateChangedClients.add(listener);
        }
    }

    public static void removeStateNotification(@NonNull final OnStateChangedClient listener) {
        synchronized (lock) {
            onStateChangedClients.remove(listener);
            if (instance != null)
                listener.onRemove();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        tryFg();
        synchronized (lock) {
            instance = this;
            for (OnStateChangedClient h : onStateChangedClients) {
                h.onEnabled();
                h.onAdd();
            }
        }
        if (onStateChanged != null) onStateChanged.onStateChanged(true);
    }

    @Override
    public void onDestroy() {
        if (onStateChanged != null) onStateChanged.onStateChanged(false);
        synchronized (lock) {
            for (OnStateChangedClient h : onStateChangedClients) {
                h.onDisabled();
                h.onRemove();
            }
            instance = null;
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (intent == null) stopSelf(); // Nothing to do after restart
        return START_STICKY; // Just in case
    }

    @Override
    @Nullable
    public IBinder onBind(final Intent intent) {
        return null;
    }
}
