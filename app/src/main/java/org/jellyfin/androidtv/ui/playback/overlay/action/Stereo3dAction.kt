package org.jellyfin.androidtv.ui.playback.overlay.action

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.playback.PlaybackController
import org.jellyfin.androidtv.ui.playback.StereoFormat
import org.jellyfin.androidtv.ui.playback.overlay.CustomPlaybackTransportControlGlue
import org.jellyfin.androidtv.ui.playback.overlay.VideoPlayerAdapter
import org.jellyfin.androidtv.util.popupMenu

/**
 * 3D output for passive (polarized) TVs: converts side-by-side / top-bottom frames into a
 * row-interleaved image so the polarized glasses work without the TV being in any 3D mode.
 * Changing the mode restarts the stream at the current position (video effects can only be
 * installed while the player is prepared).
 */
class Stereo3dAction(
	context: Context,
	customPlaybackTransportControlGlue: CustomPlaybackTransportControlGlue,
) : CustomAction(context, customPlaybackTransportControlGlue) {
	private var popup: PopupMenu? = null

	init {
		initializeWithIcon(R.drawable.ic_3d_glasses)
	}

	override fun handleClickAction(
		playbackController: PlaybackController,
		videoPlayerAdapter: VideoPlayerAdapter,
		context: Context,
		view: View,
	) {
		videoPlayerAdapter.leanbackOverlayFragment.setFading(false)
		dismissPopup()
		val format = playbackController.stereoFormat
		val swap = playbackController.stereoSwapEyes
		popup = popupMenu(context, view, Gravity.END) {
			item(context.getString(R.string.stereo_3d_off)) {
				playbackController.setStereoFormat(StereoFormat.NONE, false)
			}.apply {
				isChecked = format == StereoFormat.NONE
			}

			item(context.getString(R.string.stereo_3d_sbs)) {
				playbackController.setStereoFormat(StereoFormat.SBS_HALF, false)
			}.apply {
				isChecked = format == StereoFormat.SBS_HALF && !swap
			}

			item(context.getString(R.string.stereo_3d_sbs_swapped)) {
				playbackController.setStereoFormat(StereoFormat.SBS_HALF, true)
			}.apply {
				isChecked = format == StereoFormat.SBS_HALF && swap
			}

			item(context.getString(R.string.stereo_3d_tab)) {
				playbackController.setStereoFormat(StereoFormat.TAB_HALF, false)
			}.apply {
				isChecked = format == StereoFormat.TAB_HALF && !swap
			}

			item(context.getString(R.string.stereo_3d_tab_swapped)) {
				playbackController.setStereoFormat(StereoFormat.TAB_HALF, true)
			}.apply {
				isChecked = format == StereoFormat.TAB_HALF && swap
			}
		}
		popup?.menu?.setGroupCheckable(0, true, true)
		popup?.setOnDismissListener {
			videoPlayerAdapter.leanbackOverlayFragment.setFading(true)
			popup = null
		}
		popup?.show()
	}

	fun dismissPopup() {
		popup?.dismiss()
	}
}
