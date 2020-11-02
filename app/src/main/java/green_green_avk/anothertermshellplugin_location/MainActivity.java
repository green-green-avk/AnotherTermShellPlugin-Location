package green_green_avk.anothertermshellplugin_location;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.Nullable;

public final class MainActivity extends Activity {

    private static final int[] UNCHECKED = new int[]{};
    private static final int[] CHECKED = new int[]{android.R.attr.state_checked};

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main_menu, menu);
        LocationService.onStateChanged =
                isRunning -> ((ImageButton) menu.findItem(R.id.start_stop).getActionView())
                        .setImageState(isRunning ? CHECKED : UNCHECKED, true);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        ((ImageButton) menu.findItem(R.id.start_stop).getActionView())
                .setImageState(LocationService.isRunning() ? CHECKED : UNCHECKED, true);
        return true;
    }

    public void onStartStop(final View view) {
        if (LocationService.isRunning()) {
            LocationService.tryStop();
        } else {
            LocationService.tryStart(this);
        }
    }
}
