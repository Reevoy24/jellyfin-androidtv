package org.jellyfin.androidtv.ui.playback

/**
 * Stereoscopic layout of a video frame, used by [StereoInterleaveEffect] to convert the frame into
 * a row-interleaved image for passive (polarized) 3D TVs.
 *
 * "Half" formats squeeze both eyes into a normal-sized frame (the common case for SBS/TAB rips),
 * "full" formats store each eye at full resolution (double-width or double-height frames). The
 * distinction only affects the intended display aspect ratio.
 */
enum class StereoFormat(@JvmField val tab: Boolean, @JvmField val full: Boolean) {
	NONE(false, false),
	SBS_HALF(false, false),
	SBS_FULL(false, true),
	TAB_HALF(true, false),
	TAB_FULL(true, true),
}
