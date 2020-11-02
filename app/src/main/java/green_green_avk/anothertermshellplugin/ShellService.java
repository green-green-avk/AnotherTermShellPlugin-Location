package green_green_avk.anothertermshellplugin;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import green_green_avk.anothertermshellplugin_location.LocationService;
import green_green_avk.anothertermshellplugin_location.LocationUtils;
import green_green_avk.anothertermshellplugin_location.LocationUtils.OutputFmt;
import green_green_avk.anothertermshellplugin_location.R;
import green_green_avk.anothertermshellpluginutils.BaseShellService;
import green_green_avk.anothertermshellpluginutils.ExecutionContext;
import green_green_avk.anothertermshellpluginutils.OnSignal;
import green_green_avk.anothertermshellpluginutils.Protocol;
import green_green_avk.anothertermshellpluginutils.Signal;
import green_green_avk.anothertermshellpluginutils.Utils;
import green_green_avk.anothertermshellpluginutils.Waiter;
import green_green_avk.anothertermshellpluginutils_perms.Permissions;

public final class ShellService extends BaseShellService {

    private static final long defMinInterval = 1000; // ms
    private static final float defMinDistance = 1F; // m

    private static final class ShellLocationListener implements LocationService.OnLocation, OnSignal {
        @NonNull
        private final Context appCtx;
        @NonNull
        private final OutputStream stderr;
        @NonNull
        private final OutputStream stdout;
        @NonNull
        private final OutputFmt outputFmt;

        public final Waiter<Integer> result = new Waiter<>();

        private ShellLocationListener(@NonNull final Context ctx,
                                      @NonNull final OutputStream stderr,
                                      @NonNull final OutputStream stdout,
                                      @NonNull final OutputFmt outputFmt) {
            this.appCtx = ctx.getApplicationContext();
            this.stderr = stderr;
            this.stdout = stdout;
            this.outputFmt = outputFmt;
        }

        @Override
        public void onLocationChanged(@Nullable final Location location) {
            try {
                LocationUtils.printLocation(stdout, location, outputFmt);
                stdout.flush();
            } catch (final IOException ignored) {
                result.set(3);
            }
        }

        @Override
        public void onEnabled() {
            try {
                Utils.write(stdout, appCtx.getString(R.string.msg_enabled) + "\n");
                stdout.flush();
            } catch (final IOException ignored) {
                result.set(3);
            }
        }

        @Override
        public void onDisabled() {
            try {
                Utils.write(stdout, appCtx.getString(R.string.msg_disabled) + "\n");
                stdout.flush();
            } catch (final IOException ignored) {
                result.set(3);
            }
        }

        @Override
        public void onException(@NonNull final Exception e) {
            int r = 3;
            try {
                if (e instanceof IllegalArgumentException) {
                    r = 1;
                    Utils.write(stderr, appCtx.getString(R.string.msg_usage) + "\n" +
                            appCtx.getString(R.string.msg_bad_argument_s, e.getMessage()) + "\n");
                } else if (e instanceof SecurityException) {
                    r = 2;
                    Utils.write(stderr, e.getMessage() + "\n");
                } else {
                    Utils.write(stderr, "Oops: " + e.getMessage() + "\n");
                }
                stdout.flush();
            } catch (final IOException ignored) {
            }
            result.set(r);
        }

        @Override
        public void onSignal(@NonNull final Signal signal) {
            result.set(0);
        }
    }

    @Override
    protected int onExec(@NonNull final ExecutionContext execCtx,
                         @NonNull final byte[][] args, @NonNull final ParcelFileDescriptor[] fds) {
        final OutputStream stderr = new FileOutputStream(fds[2].getFileDescriptor());
        try {
            if (!Permissions.verifyByBinder(this)) {
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
                                minInterval =
                                        (long) (Float.parseFloat(Utils.fromUTF8(args[argsPtr])) * 1000);
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
            try {
                if (track) {
                    final ShellLocationListener ll =
                            new ShellLocationListener(this, stderr, stdout, outputFmt);
                    final HandlerThread th = new HandlerThread("");
                    th.setDaemon(true);
                    th.start();
                    if (!LocationService.isRunning()) {
                        ll.onDisabled();
                    }
                    final LocationService.OnStateChangedClient sl =
                            LocationService.notifyLocation(this, minInterval, minDistance, ll, th.getLooper());
                    try {
                        Utils.bindSignal(execCtx, ll, th.getLooper());
                        return ll.result.get();
                    } catch (final InterruptedException ignored) {
                    } finally {
                        LocationService.removeStateNotification(sl);
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
                        location = LocationService.getLocation(this);
                    } catch (final SecurityException e) {
                        Utils.write(stderr, e.getMessage() + "\n");
                        return 2;
                    }
                    try {
                        LocationUtils.printLocation(stdout, location, outputFmt);
                    } catch (final IOException ignored) {
                    }
                }
                return 0;
            } finally {
                try {
                    stdout.close();
                } catch (final IOException ignored) {
                }
            }
        } finally {
            try {
                stderr.close();
            } catch (final IOException ignored) {
            }
        }
    }

    @Override
    protected Bundle onMeta() {
        final Bundle b = new Bundle();
        b.putInt(Protocol.META_KEY_INFO_RES_ID, R.string.desc_plugin);
        b.putInt(Protocol.META_KEY_INFO_RES_TYPE, Protocol.STRING_CONTENT_TYPE_XML_AT);
        return b;
    }
}
