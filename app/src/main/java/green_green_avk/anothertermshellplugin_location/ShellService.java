package green_green_avk.anothertermshellplugin_location;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

import green_green_avk.anothertermshellpluginutils.BaseShellService;
import green_green_avk.anothertermshellpluginutils.ExecutionContext;
import green_green_avk.anothertermshellpluginutils.Protocol;
import green_green_avk.anothertermshellpluginutils.Utils;

public class ShellService extends BaseShellService {

    private static final long defMinInterval = 1000; // ms
    private static final float defMinDistance = 1F; // m

    private enum OutputFmt {DEFAULT, FANCY, BINARY}

    private static final boolean isO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

    private static final String locFmtH =
            "%TF,%<TT,%<TZ %.6f,%.6f ±%.1fm %.1fm ±%.1fm %.1f° ±%.1f° %.1fm/s ±%.1fm/s %s";
    private static final String locFmt =
            "%Ts %.6f,%.6f %.1f %.1f %.1f %.1f %.1f %.1f %.1f %s";

    private static String getLocationStr(@Nullable final Location l, @NonNull final String fmt) {
        if (l == null) return "No data";
        final Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(l.getTime());
        return String.format(Locale.ROOT, fmt,
                cal,
                l.getLatitude(), l.getLongitude(), l.getAccuracy(),
                l.getAltitude(), isO ? l.getVerticalAccuracyMeters() : 0F,
                l.getBearing(), isO ? l.getBearingAccuracyDegrees() : 0F,
                l.getSpeed(), isO ? l.getSpeedAccuracyMetersPerSecond() : 0F,
                l.getProvider());
    }

    private static final int locationBinRecLen = (
            Long.SIZE +
                    Double.SIZE * 2 + Float.SIZE +
                    Double.SIZE + Float.SIZE +
                    Float.SIZE * 2 +
                    Float.SIZE * 2 +
                    32
    ) / 8;

    private static void printLocation(@NonNull final OutputStream output,
                                      @Nullable final Location l,
                                      @NonNull final OutputFmt outputFmt) {
        if (outputFmt == OutputFmt.BINARY) {
            final DataOutputStream dos = new DataOutputStream(output);
            try {
                if (l == null) {
                    dos.writeInt(0);
                    return;
                }
                dos.writeInt(locationBinRecLen);
                dos.writeLong(l.getTime());
                dos.writeDouble(l.getLatitude());
                dos.writeDouble(l.getLongitude());
                dos.writeFloat(l.getAccuracy());
                dos.writeDouble(l.getAltitude());
                dos.writeFloat(isO ? l.getVerticalAccuracyMeters() : 0F);
                dos.writeFloat(l.getBearing());
                dos.writeFloat(isO ? l.getBearingAccuracyDegrees() : 0F);
                dos.writeFloat(l.getSpeed());
                dos.writeFloat(isO ? l.getSpeedAccuracyMetersPerSecond() : 0F);
                String pStr = l.getProvider();
                if (pStr == null) pStr = "null";
                dos.write(Arrays.copyOf(Utils.toUTF8(pStr), 4));
            } catch (final IOException ignored) {
            }
        } else
            Utils.write(output, getLocationStr(l,
                    outputFmt == OutputFmt.FANCY ? locFmtH : locFmt) + "\n");
    }

    private static final class ShellLocationListener implements LocationListener {
        @NonNull
        private final OutputStream stdout;
        @NonNull
        private final OutputFmt outputFmt;

        private ShellLocationListener(@NonNull final OutputStream stdout,
                                      @NonNull final OutputFmt outputFmt) {
            this.stdout = stdout;
            this.outputFmt = outputFmt;
        }

        @Override
        public void onLocationChanged(final Location location) {
            printLocation(stdout, location, outputFmt);
            try {
                stdout.flush();
            } catch (final IOException ignored) {
            }
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
    }

    @Override
    protected int onExec(@NonNull final ExecutionContext execCtx,
                         @NonNull final byte[][] args, @NonNull final ParcelFileDescriptor[] fds) {
        final OutputStream stderr = new FileOutputStream(fds[2].getFileDescriptor());
        if (!execCtx.verify(BuildConfig.DEBUG ?
                BaseShellService.trustedClientsDebug : BaseShellService.trustedClients)) {
            Utils.write(stderr, "Access denied: untrusted client\n");
            return 1;
        }
        @NonNull OutputFmt outputFmt = OutputFmt.DEFAULT;
        boolean track = false;
        long minInterval = defMinInterval;
        float minDistance = defMinDistance;
        int argsPtr = 0;
        while (args.length > argsPtr) {
            final String arg = Utils.fromUTF8(args[argsPtr]);
            switch (arg) {
                case "-b":
                    outputFmt = OutputFmt.BINARY;
                    argsPtr++;
                    break;
                case "-r":
                    outputFmt = OutputFmt.FANCY;
                    argsPtr++;
                    break;
                case "-t":
                    track = true;
                    argsPtr++;
                    try {
                        if (args.length > argsPtr) {
                            minInterval = Long.parseLong(Utils.fromUTF8(args[argsPtr])) * 1000;
                            argsPtr++;
                        }
                        if (args.length > argsPtr) {
                            minDistance = Float.parseFloat(Utils.fromUTF8(args[argsPtr]));
                            argsPtr++;
                        }
                    } catch (final NumberFormatException ignored) {
                    }
                    break;
                default:
                    Utils.write(stderr, getString(R.string.msg_usage) + "\n");
                    Utils.write(stderr, getString(R.string.msg_bad_argument_s, arg) + "\n");
                    return 1;
            }
        }
        final OutputStream stdout = new FileOutputStream(fds[1].getFileDescriptor());
        final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (track) {
            final LocationListener ll = new ShellLocationListener(stdout, outputFmt);
            final HandlerThread th = new HandlerThread("");
            th.setDaemon(true);
            th.start();
            try {
                try {
                    lm.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, minInterval, minDistance,
                            ll, th.getLooper());
                } catch (final IllegalArgumentException e) {
                    Utils.write(stderr, getString(R.string.msg_usage) + "\n");
                    Utils.write(stderr, getString(R.string.msg_bad_argument_s, e.getMessage()) + "\n");
                    return 1;
                } catch (final SecurityException e) {
                    Utils.write(stderr, getString(R.string.msg_location_perm_not_granted) + "\n");
                    return 2;
                }
                execCtx.readSignal();
            } catch (final IOException ignored) {
            } finally {
                lm.removeUpdates(ll);
                th.quit();
                try {
                    th.join();
                } catch (final InterruptedException ignored) {
                }
                Utils.write(stderr, "\n");
            }
        } else {
            final Location location;
            try {
                location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } catch (final SecurityException e) {
                Utils.write(stderr, getString(R.string.msg_location_perm_not_granted) + "\n");
                return 1;
            }
            printLocation(stdout, location, outputFmt);
        }
        return 0;
    }

    @Override
    protected Bundle onMeta() {
        final Bundle b = new Bundle();
        b.putInt(Protocol.META_KEY_INFO_RES_ID, R.string.desc_plugin);
        b.putInt(Protocol.META_KEY_INFO_RES_TYPE, Protocol.STRING_CONTENT_TYPE_XML_AT);
        return b;
    }
}
