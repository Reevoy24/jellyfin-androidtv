package org.jellyfin.androidtv.ui.playback.overlay;

import static java.lang.Math.round;

import android.content.Context;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.leanback.media.PlaybackTransportControlGlue;
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter;
import androidx.leanback.widget.Action;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.PlaybackControlsRow;
import androidx.leanback.widget.PlaybackRowPresenter;
import androidx.leanback.widget.PlaybackTransportRowPresenter;
import androidx.leanback.widget.PlaybackTransportRowView;
import androidx.leanback.widget.RowPresenter;

import org.jellyfin.androidtv.R;
import org.jellyfin.androidtv.preference.UserPreferences;
import org.jellyfin.androidtv.preference.constant.ClockBehavior;
import org.jellyfin.androidtv.ui.playback.PlaybackController;
import org.jellyfin.androidtv.ui.playback.overlay.action.AndroidAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.ChannelBarChannelAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.ChapterAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.ClosedCaptionsAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.CustomAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.FastForwardAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.GuideAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.PlayPauseAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.PlaybackSpeedAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.PreviousLiveTvChannelAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.RecordAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.RewindAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.SelectAudioAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.SelectQualityAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.SkipNextAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.SkipPreviousAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.Stereo3dAction;
import org.jellyfin.androidtv.ui.playback.overlay.action.ZoomAction;
import org.jellyfin.androidtv.util.DateTimeExtensionsKt;
import org.koin.java.KoinJavaComponent;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class CustomPlaybackTransportControlGlue extends PlaybackTransportControlGlue<VideoPlayerAdapter> {

    // Normal playback actions
    private PlayPauseAction playPauseAction;
    private RewindAction rewindAction;
    private FastForwardAction fastForwardAction;
    private SkipPreviousAction skipPreviousAction;
    private SkipNextAction skipNextAction;
    private SelectAudioAction selectAudioAction;
    private ClosedCaptionsAction closedCaptionsAction;
    private SelectQualityAction selectQualityAction;
    private PlaybackSpeedAction playbackSpeedAction;
    private ZoomAction zoomAction;
    private Stereo3dAction stereo3dAction;
    private ChapterAction chapterAction;

    // TV actions
    private PreviousLiveTvChannelAction previousLiveTvChannelAction;
    private ChannelBarChannelAction channelBarChannelAction;
    private GuideAction guideAction;
    private RecordAction recordAction;

    private final PlaybackController playbackController;
    private ArrayObjectAdapter primaryActionsAdapter;
    private ArrayObjectAdapter secondaryActionsAdapter;

    // Injected views
    private TextView mEndsText = null;

    private final Handler mHandler = new Handler();
    private Runnable mRefreshEndTime;
    private Runnable mRefreshViewVisibility;

    private LinearLayout mButtonRef;

    /** Root view of the transport row (lb_playback_transport_controls_row), for focus management. */
    private View mRowView;

    CustomPlaybackTransportControlGlue(Context context, VideoPlayerAdapter playerAdapter, PlaybackController playbackController) {
        super(context, playerAdapter);
        this.playbackController = playbackController;

        mRefreshEndTime = () -> {
            setEndTime();
            if (!isPlaying()) {
                mHandler.postDelayed(mRefreshEndTime, 30000);
            }
        };

        mRefreshViewVisibility = () -> {
            if (mButtonRef != null && mButtonRef.getVisibility() != mEndsText.getVisibility())
                mEndsText.setVisibility(mButtonRef.getVisibility());
            else
                mHandler.postDelayed(mRefreshViewVisibility, 100);
        };

        initActions(context);
    }

    @Override
    protected void onDetachedFromHost() {
        mHandler.removeCallbacks(mRefreshEndTime);
        mHandler.removeCallbacks(mRefreshViewVisibility);

        closedCaptionsAction.removePopup();
        playbackSpeedAction.dismissPopup();
        selectAudioAction.dismissPopup();
        selectQualityAction.dismissPopup();
        zoomAction.dismissPopup();
        stereo3dAction.dismissPopup();

        super.onDetachedFromHost();
    }

    @Override
    protected PlaybackRowPresenter onCreateRowPresenter() {
        final AbstractDetailsDescriptionPresenter detailsPresenter = new AbstractDetailsDescriptionPresenter() {
            @Override
            protected void onBindDescription(ViewHolder vh, Object item) {

            }
        };
        PlaybackTransportRowPresenter rowPresenter = new PlaybackTransportRowPresenter() {
            @Override
            protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
                RowPresenter.ViewHolder vh = super.createRowViewHolder(parent);

                mRowView = vh.view;

                // The seek bar is a focus trap: leanback prefers it on every automatic focus grant
                // and its key listener consumes DPAD LEFT/RIGHT unconditionally (scrubs instead of
                // navigating). Make it non-focusable so NO code path -- known or unknown -- can ever
                // land focus there automatically; leanback's own focus routing checks isFocusable()
                // and cleanly skips it, so the buttons become the natural landing spot everywhere.
                // Scrubbing stays available through the one deliberate gesture: DPAD_DOWN from the
                // primary buttons calls tryEnterSeekBar(), which re-enables focusability for the
                // duration of the visit (disabled again when focus leaves, see the focus guard).
                View seekBar = vh.view.findViewById(androidx.leanback.R.id.playback_progress);
                if (seekBar != null) seekBar.setFocusable(false);

                attachSeekBarFocusGuard(vh.view);

                ClockBehavior showClock = KoinJavaComponent.<UserPreferences>get(UserPreferences.class).get(UserPreferences.Companion.getClockBehavior());

                if (showClock == ClockBehavior.ALWAYS || showClock == ClockBehavior.IN_VIDEO) {
                    Context context = parent.getContext();
                    mEndsText = new TextView(context);
                    mEndsText.setTextAppearance(context, androidx.leanback.R.style.Widget_Leanback_PlaybackControlsTimeStyle);
                    setEndTime();

                    LinearLayout view = (LinearLayout) vh.view;

                    PlaybackTransportRowView bar = (PlaybackTransportRowView) view.getChildAt(1);
                    FrameLayout v = (FrameLayout) bar.getChildAt(0);
                    mButtonRef = (LinearLayout) v.getChildAt(0);

                    bar.removeViewAt(0);
                    RelativeLayout rl = new RelativeLayout(context);
                    RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT);
                    rl.addView(v);

                    RelativeLayout.LayoutParams rlp2 = new RelativeLayout.LayoutParams(
                            RelativeLayout.LayoutParams.WRAP_CONTENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT);
                    rlp2.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                    rlp2.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                    rl.addView(mEndsText, rlp2);
                    bar.addView(rl, 0, rlp);
                }

                return vh;
            }

            @Override
            protected void onProgressBarClicked(PlaybackTransportRowPresenter.ViewHolder vh) {
                CustomPlaybackTransportControlGlue controlglue = CustomPlaybackTransportControlGlue.this;
                controlglue.onActionClicked(controlglue.playPauseAction);
            }

            @Override
            protected void onBindRowViewHolder(RowPresenter.ViewHolder vh, Object item) {
                super.onBindRowViewHolder(vh, item);
                vh.setOnKeyListener(CustomPlaybackTransportControlGlue.this);
            }

            @Override
            protected void onUnbindRowViewHolder(RowPresenter.ViewHolder vh) {
                super.onUnbindRowViewHolder(vh);
                vh.setOnKeyListener(null);
            }

            @Override
            public void onReappear(RowPresenter.ViewHolder vh) {
                // leanback's default focuses the seek bar. Despite the name this does NOT run when the
                // OSD appears -- it only runs when a hide-fade completes (and via the never-called
                // resetFocus()) -- but wherever it runs, park focus on the control buttons instead.
                // See attachSeekBarFocusGuard for why the seek bar must never gain focus automatically.
                if (vh.view != null && vh.view.hasFocus()) focusPrimaryControls();
            }
        };
        rowPresenter.setDescriptionPresenter(detailsPresenter);
        return rowPresenter;
    }

    /**
     * leanback's PlaybackTransportRowView.onRequestFocusInDescendants prefers the seek bar whenever
     * focus enters the row without an already-focused descendant (playback start, grid focus
     * recovery after relayouts, the focused button being removed during an action-adapter rebuild,
     * a popup closing, ...). On the seek bar, DPAD LEFT/RIGHT is consumed unconditionally for
     * scrubbing -- and once scrubbing, UP/DOWN are swallowed too and the buttons are set GONE -- so
     * every such automatic grant strands the user where left/right seeks instead of navigating.
     * Watch for those grants and send focus to the control buttons instead. A deliberate DPAD_DOWN
     * from the buttons (old focus is an attached descendant of the row) is left alone so the seek
     * bar stays reachable for scrubbing.
     * <p>
     * The referenced ids (controls_dock, playback_progress) are internal leanback ids tied to
     * lb_playback_transport_controls_row.xml -- re-verify they exist on any androidx.leanback bump.
     */
    private void attachSeekBarFocusGuard(final View rowView) {
        final ViewTreeObserver.OnGlobalFocusChangeListener guard = (oldFocus, newFocus) -> {
            // End of a deliberate seek-bar visit (see tryEnterSeekBar): focus moved off the seek
            // bar, lock it again so it can't be a target for automatic focus grants.
            if (oldFocus != null && oldFocus.getId() == androidx.leanback.R.id.playback_progress
                    && isDescendantOf(oldFocus, rowView) && oldFocus != newFocus) {
                oldFocus.setFocusable(false);
            }

            if (newFocus == null || newFocus.getId() != androidx.leanback.R.id.playback_progress) return;
            if (!isDescendantOf(newFocus, rowView)) return;
            // A move from within the row (the deliberate tryEnterSeekBar entry) is allowed.
            if (oldFocus != null && oldFocus.isAttachedToWindow() && isDescendantOf(oldFocus, rowView)) return;
            focusPrimaryControls();
        };
        rowView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                v.getViewTreeObserver().addOnGlobalFocusChangeListener(guard);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                v.getViewTreeObserver().removeOnGlobalFocusChangeListener(guard);
            }
        });
    }

    /**
     * Focus the primary control buttons (play/pause is the first child, so it becomes the landing
     * spot). If the dock momentarily has no focusable children -- during an action-adapter rebuild
     * or playback startup before addMediaActions() -- retry after the current frame instead of
     * letting focus strand on the seek bar.
     */
    void focusPrimaryControls() {
        final View rowView = mRowView;
        if (rowView == null) return;
        View dock = rowView.findViewById(androidx.leanback.R.id.controls_dock);
        if (dock != null && dock.requestFocus()) return;
        rowView.post(() -> {
            View focused = rowView.findFocus();
            if (focused == null || focused.getId() != androidx.leanback.R.id.playback_progress) return;
            View retryDock = rowView.findViewById(androidx.leanback.R.id.controls_dock);
            if (retryDock != null) retryDock.requestFocus();
        });
    }

    /**
     * Redirect focus to the control buttons when it is stranded somewhere unhelpful: on the seek
     * bar (retained from an earlier hide/scrub -- onReappear does NOT run at show time), or on a
     * bare ancestor container (the leanback grid keeps focus itself when the dock had no focusable
     * children yet during startup, since the seek bar is non-focusable). Called when the OSD is
     * shown and after every action-adapter rebuild. Never steals focus from sibling UI (popups,
     * the guide, the top panel): those are not ancestors of the transport row.
     */
    public void focusPrimaryControlsIfOnSeekBar() {
        View rowView = mRowView;
        if (rowView == null) return;
        View focused = rowView.getRootView().findFocus();
        if (focused == null) return;
        boolean onSeekBar = focused.getId() == androidx.leanback.R.id.playback_progress
                && isDescendantOf(focused, rowView);
        boolean onBareContainer = isDescendantOf(rowView, focused);
        if (onSeekBar || onBareContainer) focusPrimaryControls();
    }

    /**
     * The one deliberate way onto the (otherwise non-focusable, see createRowViewHolder) seek bar:
     * called for DPAD_DOWN while the OSD is visible. Only acts when focus is currently on one of
     * the primary control buttons and the player can seek; re-enables the seek bar's focusability
     * and moves focus there so leanback's scrub mode (LEFT/RIGHT step, OK commit, BACK cancel)
     * works as designed. The focus guard locks the bar again as soon as focus leaves it.
     *
     * @return true when the seek bar took focus (the key event is consumed by the caller)
     */
    public boolean tryEnterSeekBar() {
        View rowView = mRowView;
        if (rowView == null) return false;
        View seekBar = rowView.findViewById(androidx.leanback.R.id.playback_progress);
        View dock = rowView.findViewById(androidx.leanback.R.id.controls_dock);
        if (seekBar == null || dock == null) return false;
        View focused = rowView.findFocus();
        if (focused == null || !isDescendantOf(focused, dock)) return false;
        if (!getPlayerAdapter().canSeek()) return false;
        seekBar.setFocusable(true);
        if (seekBar.requestFocus()) return true;
        seekBar.setFocusable(false);
        return false;
    }

    private static boolean isDescendantOf(View view, View ancestor) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent == ancestor) return true;
            parent = parent.getParent();
        }
        return false;
    }

    private void initActions(Context context) {
        playPauseAction = new PlayPauseAction(context);
        rewindAction = new RewindAction(context);
        fastForwardAction = new FastForwardAction(context);
        skipPreviousAction = new SkipPreviousAction(context);
        skipNextAction = new SkipNextAction(context);
        selectAudioAction = new SelectAudioAction(context, this);
        selectAudioAction.setLabels(new String[]{context.getString(R.string.lbl_audio_track)});
        closedCaptionsAction = new ClosedCaptionsAction(context, this);
        closedCaptionsAction.setLabels(new String[]{context.getString(R.string.lbl_subtitle_track)});
        selectQualityAction = new SelectQualityAction(context, this, KoinJavaComponent.get(UserPreferences.class));
        selectQualityAction.setLabels(new String[]{context.getString(R.string.lbl_quality_profile)});
        playbackSpeedAction = new PlaybackSpeedAction(context, this, playbackController);
        playbackSpeedAction.setLabels(new String[]{context.getString(R.string.lbl_playback_speed)});
        zoomAction = new ZoomAction(context, this);
        zoomAction.setLabels(new String[]{context.getString(R.string.lbl_zoom)});
        stereo3dAction = new Stereo3dAction(context, this);
        stereo3dAction.setLabels(new String[]{context.getString(R.string.lbl_stereo_3d)});
        chapterAction = new ChapterAction(context, this);
        chapterAction.setLabels(new String[]{context.getString(R.string.lbl_chapters)});

        previousLiveTvChannelAction = new PreviousLiveTvChannelAction(context, this);
        previousLiveTvChannelAction.setLabels(new String[]{context.getString(R.string.lbl_prev_item)});
        channelBarChannelAction = new ChannelBarChannelAction(context, this);
        channelBarChannelAction.setLabels(new String[]{context.getString(R.string.lbl_other_channels)});
        guideAction = new GuideAction(context, this);
        guideAction.setLabels(new String[]{context.getString(R.string.lbl_live_tv_guide)});
        recordAction = new RecordAction(context, this);
        recordAction.setLabels(new String[]{
                context.getString(R.string.lbl_record),
                context.getString(R.string.lbl_cancel_recording)
        });
    }

    @Override
    protected void onCreatePrimaryActions(ArrayObjectAdapter primaryActionsAdapter) {
        this.primaryActionsAdapter = primaryActionsAdapter;
    }

    @Override
    protected void onCreateSecondaryActions(ArrayObjectAdapter secondaryActionsAdapter) {
        this.secondaryActionsAdapter = secondaryActionsAdapter;
    }

    void addMediaActions() {
        if (primaryActionsAdapter.size() > 0)
            primaryActionsAdapter.clear();
        if (secondaryActionsAdapter.size() > 0)
            secondaryActionsAdapter.clear();

        // Primary Items
        primaryActionsAdapter.add(playPauseAction);
        VideoPlayerAdapter playerAdapter = getPlayerAdapter();

        if (playerAdapter.canSeek()) {
            primaryActionsAdapter.add(rewindAction);
            primaryActionsAdapter.add(fastForwardAction);
        }

        if (playerAdapter.hasSubs()) {
            primaryActionsAdapter.add(closedCaptionsAction);
        }

        if (playerAdapter.hasMultiAudio()) {
            primaryActionsAdapter.add(selectAudioAction);
        }

        if (playerAdapter.isLiveTv()) {
            primaryActionsAdapter.add(channelBarChannelAction);
            primaryActionsAdapter.add(guideAction);
        }

        // Secondary Items
        if (playerAdapter.isLiveTv()) {
            secondaryActionsAdapter.add(previousLiveTvChannelAction);
            if (playerAdapter.canRecordLiveTv()) {
                secondaryActionsAdapter.add(recordAction);
                recordingStateChanged();
            }
        }

        if (playerAdapter.hasPreviousItem()) {
            secondaryActionsAdapter.add(skipPreviousAction);
        }

        if (playerAdapter.hasNextItem()) {
            secondaryActionsAdapter.add(skipNextAction);
        }

        if (playerAdapter.hasChapters()) {
            secondaryActionsAdapter.add(chapterAction);
        }

        if (!playerAdapter.isLiveTv()) {
            secondaryActionsAdapter.add(playbackSpeedAction);
            secondaryActionsAdapter.add(selectQualityAction);
        }

        secondaryActionsAdapter.add(zoomAction);

        if (!playerAdapter.isLiveTv()) {
            secondaryActionsAdapter.add(stereo3dAction);
        }

        // Clearing the adapters above removes the focused button, which makes leanback descend to
        // the seek bar (see attachSeekBarFocusGuard). Repair synchronously now that buttons exist
        // again -- this runs after every subtitle/audio/quality change and at playback startup.
        focusPrimaryControlsIfOnSeekBar();
    }

    @Override
    public void onActionClicked(Action action) {
        if (action instanceof AndroidAction) {
            ((AndroidAction) action).onActionClicked(getPlayerAdapter());
        }
        notifyActionChanged(action);
    }

    public void onCustomActionClicked(Action action, View view) {
        // Handle custom action clicks which require a popup menu
        if (action instanceof CustomAction) {
            ((CustomAction) action).handleClickAction(playbackController, getPlayerAdapter(), getContext(), view);
        }

        if (action == playbackSpeedAction) {
            // Post a callback to calculate the new time, since Exoplayer updates this in an async fashion.
            // This is a hack, we should instead have onPlaybackParametersChanged call out to this
            // class to notify rather than poll. But communication is unidirectional at the moment:
            mHandler.postDelayed(mRefreshEndTime, 5000);  // 5 seconds
        }
    }

    private void setEndTime() {
        if (mEndsText == null || getPlayerAdapter().getDuration() < 1)
            return;
        long msLeft = getPlayerAdapter().getDuration() - getPlayerAdapter().getCurrentPosition();
        long realTimeLeft = round(msLeft / playbackController.getPlaybackSpeed());

        LocalDateTime endTime = LocalDateTime.now().plus(realTimeLeft, ChronoUnit.MILLIS);
        mEndsText.setText(getContext().getString(R.string.lbl_playback_control_ends, DateTimeExtensionsKt.getTimeFormatter(getContext()).format(endTime)));
    }

    private void notifyActionChanged(Action action) {
        ArrayObjectAdapter adapter = primaryActionsAdapter;
        if (adapter.indexOf(action) >= 0) {
            adapter.notifyArrayItemRangeChanged(adapter.indexOf(action), 1);
            return;
        }
        adapter = secondaryActionsAdapter;
        if (adapter.indexOf(action) >= 0) {
            adapter.notifyArrayItemRangeChanged(adapter.indexOf(action), 1);
        }
    }

    void setInitialPlaybackDrawable() {
        playPauseAction.setIndex(PlaybackControlsRow.PlayPauseAction.INDEX_PAUSE);
        notifyActionChanged(playPauseAction);
    }

    void invalidatePlaybackControls() {
        if (primaryActionsAdapter.size() > 0)
            primaryActionsAdapter.clear();
        if (secondaryActionsAdapter.size() > 0)
            secondaryActionsAdapter.clear();
        addMediaActions();
    }

    void recordingStateChanged() {
        if (getPlayerAdapter().isRecording()) {
            recordAction.setIndex(RecordAction.INDEX_RECORDING);
        } else {
            recordAction.setIndex(RecordAction.INDEX_INACTIVE);
        }
        notifyActionChanged(recordAction);
    }

    void updatePlayState() {
        playPauseAction.setIndex(isPlaying() ? PlaybackControlsRow.PlayPauseAction.INDEX_PAUSE : PlaybackControlsRow.PlayPauseAction.INDEX_PLAY);
        notifyActionChanged(playPauseAction);
        setEndTime();
        if (!isPlaying()) {
            mHandler.removeCallbacks(mRefreshEndTime);
            mHandler.postDelayed(mRefreshEndTime, 30000);
        } else {
            mHandler.removeCallbacks(mRefreshEndTime);
        }

    }

    public void setInjectedViewsVisibility() {
        if (mButtonRef != null && mButtonRef.getVisibility() != mEndsText.getVisibility())
            mEndsText.setVisibility(mButtonRef.getVisibility());
        mHandler.removeCallbacks(mRefreshViewVisibility);
        mHandler.postDelayed(mRefreshViewVisibility, 100);
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            // The below actions are only handled on key up
            return super.onKey(v, keyCode, event);
        }

        VideoPlayerAdapter playerAdapter = getPlayerAdapter();

        if (playerAdapter.hasSubs() && keyCode == KeyEvent.KEYCODE_CAPTIONS) {
            closedCaptionsAction.handleClickAction(playbackController, getPlayerAdapter(), getContext(), v);
        }
        if (playerAdapter.hasMultiAudio() && keyCode == KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK) {
            selectAudioAction.handleClickAction(playbackController, getPlayerAdapter(), getContext(), v);
        }
        return super.onKey(v, keyCode, event);
    }
}
