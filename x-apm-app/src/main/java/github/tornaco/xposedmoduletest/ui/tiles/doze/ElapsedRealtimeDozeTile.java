package github.tornaco.xposedmoduletest.ui.tiles.doze;

import android.content.Context;
import android.widget.RelativeLayout;

import dev.nick.tiles.tile.QuickTile;
import dev.nick.tiles.tile.SwitchTileView;
import github.tornaco.xposedmoduletest.R;
import github.tornaco.xposedmoduletest.xposed.app.XAPMManager;

public class ElapsedRealtimeDozeTile extends QuickTile {

    public ElapsedRealtimeDozeTile(final Context context) {
        super(context);
        this.titleRes = R.string.title_doze_elapsed_realtime;
        this.summaryRes = R.string.summary_doze_elapsed_realtime;
        this.iconRes = R.drawable.ic_timer_black_24dp;

        this.tileView = new SwitchTileView(context) {
            @Override
            protected void onBindActionView(RelativeLayout container) {
                super.onBindActionView(container);
                setChecked(XAPMManager.get().isDozeElapsedRealtimeEnabled());
            }

            @Override
            protected void onCheckChanged(boolean checked) {
                super.onCheckChanged(checked);
                XAPMManager.get().setDozeElapsedRealtimeEnabled(checked);
            }
        };
    }
}
