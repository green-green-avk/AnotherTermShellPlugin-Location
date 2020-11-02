package green_green_avk.anothertermshellplugin_location;

import android.location.Location;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

public final class LocationUtils {
    private LocationUtils() {
    }

    public enum OutputFmt {DEFAULT, FANCY, BINARY}

    public static final String locFmtH =
            "%TF,%<TT,%<TZ %.6f,%.6f ±%.1fm %.1fm ±%.1fm %.1f° ±%.1f° %.1fm/s ±%.1fm/s %s";
    public static final String locFmt =
            "%Ts %.6f,%.6f %.1f %.1f %.1f %.1f %.1f %.1f %.1f %s";
    public static final int locationBinRecLen = (
            Long.SIZE +
                    Double.SIZE * 2 + Float.SIZE +
                    Double.SIZE + Float.SIZE +
                    Float.SIZE * 2 +
                    Float.SIZE * 2 +
                    32
    ) / 8;

    public static final boolean isO = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

    public static String getLocationStr(@Nullable final Location l, @NonNull final String fmt) {
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

    public static void printLocation(@NonNull final OutputStream output,
                                     @Nullable final Location l,
                                     @NonNull final OutputFmt outputFmt) throws IOException {
        if (outputFmt == OutputFmt.BINARY) {
            final ByteBuffer ob = ByteBuffer.allocate(locationBinRecLen + Integer.SIZE / 8)
                    .order(ByteOrder.nativeOrder());
            if (l == null) {
                ob.putInt(0);
                output.write(ob.array(), ob.arrayOffset(), ob.arrayOffset() + ob.position());
                return;
            }
            ob.putInt(locationBinRecLen);
            String pStr = l.getProvider();
            if (pStr == null) pStr = "null";
            ob.put(Arrays.copyOf(green_green_avk.anothertermshellpluginutils.Utils.toUTF8(pStr), 4));
            ob.putLong(l.getTime());
            ob.putDouble(l.getLatitude());
            ob.putDouble(l.getLongitude());
            ob.putDouble(l.getAltitude());
            ob.putFloat(l.getAccuracy());
            ob.putFloat(isO ? l.getVerticalAccuracyMeters() : 0F);
            ob.putFloat(l.getBearing());
            ob.putFloat(isO ? l.getBearingAccuracyDegrees() : 0F);
            ob.putFloat(l.getSpeed());
            ob.putFloat(isO ? l.getSpeedAccuracyMetersPerSecond() : 0F);
            output.write(ob.array(), ob.arrayOffset(), ob.arrayOffset() + ob.position());
        } else
            output.write(green_green_avk.anothertermshellpluginutils.Utils.toUTF8(getLocationStr(l,
                    outputFmt == OutputFmt.FANCY ? locFmtH : locFmt) + "\n"));
    }
}
