package com.tgnanativeapps.jwplayer

import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentActivity
import android.content.Context
import android.net.Uri
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.Toast
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReadableArray
import com.longtailvideo.jwplayer.JWPlayerView
import com.longtailvideo.jwplayer.configuration.PlayerConfig
import com.longtailvideo.jwplayer.configuration.SkinConfig
import com.longtailvideo.jwplayer.media.ads.AdBreak
import com.longtailvideo.jwplayer.media.ads.AdSource
import com.longtailvideo.jwplayer.media.captions.Caption
import com.longtailvideo.jwplayer.media.playlists.PlaylistItem
import com.tgnanativeapps.R
import com.tgnanativeapps.adlogger.AdLogger
import kotlinx.android.synthetic.main.view_jwplayer_logger_button.view.*
import kotlinx.coroutines.*


private const val TAG = "@JWPlayer"

class JWPlayerFrameLayout(context: Context) : FrameLayout(context) {

    // Fields
    private val adLogger = AdLogger("Preroll")

    private val reactContext: ReactContext = context as ReactContext
    private val playerConfig: PlayerConfig
    private val playerFragment: TGNPlayerFragment

    private var playlistJob: Job? = null
    internal var isOnAdBreak: Boolean = false
    private var isSetup: Boolean  = false

    // Properties coming from the react native store
    val rnProps: JWPlayerRNProps = JWPlayerRNProps()

    // Convenience Accessors
    @Suppress("DEPRECATION")
    private inline val mFragmentManager: FragmentManager?
        get() = (reactContext.currentActivity as? FragmentActivity)?.supportFragmentManager
    private inline val mPlayerView: JWPlayerView?
        get() = playerFragment.player


    init {
        Log.d(TAG, "initializing")

        val skinConfig = SkinConfig.Builder()
            .name("test")
            .url("file:///android_asset/test.css")
            .build()


        // Build a player configuration
        playerConfig = PlayerConfig.Builder()
            .allowCrossProtocolRedirects(true)
            .skinConfig(skinConfig)
            .mute(true)
            .build()

        // Instantiate a new JWPlayerSupportFragment using the playerConfig
        playerFragment = TGNPlayerFragment.newInstance(playerConfig)

        // Add the fragment into the FrameLayout
        val fragmentManager = mFragmentManager

        if (fragmentManager != null) {
            fragmentManager.beginTransaction()
                .add(playerFragment, "JWPlayerSupportFragment")
                .commitAllowingStateLoss()
            fragmentManager.executePendingTransactions()
        }

        // Instantiate the JW Player event handler class
        val playerView = mPlayerView
        if (playerView != null) {
            JWPlayerEventHandler(playerView, reactContext, this, adLogger)
        }

        // This step is needed to in order for ReactNative to render your view
        if (playerFragment.view != null) {
            addView(playerFragment.view, MATCH_PARENT, MATCH_PARENT)
        }

        // Setup the debug button
        inflate(context, R.layout.view_jwplayer_logger_button, this)

        debugButton.visibility = View.GONE

        debugButton.setOnClickListener {
            val c = it.context
            if (c != null) {
                adLogger.copyToClipboard(c)
                Toast.makeText(c, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun cleanUp() {
        // Cancel any remaining set-playlist jobs
        playlistJob?.cancel()
        playlistJob = null

        // Manually removing the player view is needed since we manually added it to this layout
        removeAllViews()

        mFragmentManager?.beginTransaction()
            ?.remove(playerFragment)
            ?.commitAllowingStateLoss()
    }

    fun setDangerouslyPlay(isPlaying: Boolean) {
        // Make sure to re-assign for any other methods that may be reading RNProps
        rnProps.isPlaying = isPlaying
        Log.d(TAG, "setDangerouslyPlay rnProps = $rnProps")
        val playerView = mPlayerView ?: return
        if (rnProps.isPlaying) {
            playerView.play()
        } else {
            playerView.pause()
        }
    }

    fun setDangerouslySeekTime(dangerouslySeekTime: Double) {
        // Make sure the JWPlayer state is in a state that matches the rnProps
        val playerView = mPlayerView ?: return
        if (rnProps.dangerouslySeekTime != dangerouslySeekTime && dangerouslySeekTime > 0 && rnProps.isPlaying) {
            // This is called when transitioning from takeover back to intentional inline player
            // To preserve correct time, we have to manually set the start time on the playlist item and re-setup the player
            // Instead of calling 'seek'method since it is broken in JWPlayer SDK 3.12.1+
            playerConfig.playlist?.get(0)?.setStartTime(dangerouslySeekTime)
            playerView.setup(playerConfig)
        }
        rnProps.dangerouslySeekTime = dangerouslySeekTime
        Log.d(TAG, "setDangerouslySeekTime rnProps = $rnProps")
    }

    fun setIsControlsVisible(isControlsVisible: Boolean) {
        // Make sure to re-assign for any other methods that may be reading RNProps
        rnProps.isControlsVisible = isControlsVisible
        Log.d(TAG, "setIsControlsVisible rnProps = $rnProps")
        val playerView = mPlayerView ?: return
        playerView.controls = rnProps.shouldShowControls
    }

    fun setIsMuted(isMuted: Boolean) {
        // Make sure to re-assign for any other methods that may be reading RNProps
        rnProps.isAudioOn = !isMuted
        Log.d(TAG, "setIsMuted rnProps = $rnProps")
        val playerView = mPlayerView ?: return
        playerView.mute = !rnProps.isAudioOn
    }

    fun setDebuggerValue(debuggerValue: String) {
        // Make sure to re-assign for any other methods that may be reading RNProps
        rnProps.debuggerValue = debuggerValue
        Log.d(TAG, "setDebuggerValue rnProps = $rnProps")

        if (debuggerValue == "Enabled") {
            // Show the debug button and enable the logger
            debugButton.visibility = View.VISIBLE
            adLogger.startAndReset()
        } else {
            // Hide the debug button and clear all the logs to prevent memleaks
            debugButton.visibility = View.GONE
            adLogger.stopAndCleanup()
        }
    }

    fun setIsCaptionOn(isCaptionOn: Boolean) {
        // Make sure to re-assign for any other methods that may be reading RNProps
        rnProps.isCCOn = isCaptionOn
        Log.d(TAG, "setIsCaptionOn rnProps = $rnProps")
        val playerView = mPlayerView ?: return
        playerView.currentCaptions = if (rnProps.isCCOn) 1 else 0
    }

    fun setIsInline(isInline: Boolean) {
        // Make sure to re-assign for any other methods that may be reading RNProps
        rnProps.isInline = isInline
    }


    fun setPlaylist(videos: ReadableArray) {
        // Cancel the current set-playlist job, if there's any
        this.playlistJob?.cancel()

        // Start another one
        this.playlistJob = GlobalScope.launch {
            // Make sure setPlaylistAsync() is run on the Main thread
            withContext(Dispatchers.Main) {
                setPlaylistAsync(videos)
            }
        }
    }

    private suspend fun CoroutineScope.setPlaylistAsync(videos: ReadableArray) {

        val context = context ?: return
        val playerView = mPlayerView ?: return

        // Create a playlist, you'll need this to build your player config
        // https://developer.jwplayer.com/sdk/android/docs/developer-guide/interaction/playlists/
        val playlist = mutableListOf<PlaylistItem>()

        // Don't continue if this job is cancelled
        if (!isActive) return

        // Fetch the Limited Ad Tracking parameters for the preroll
        val latParams = loadLatParams(context)

        // Go through each videoItem passed as prop to fill the playlist
        for (i in 0 until videos.size()) {
            // Don't continue if this job is cancelled
            if (!isActive) return

            val videoItem = videos.getMap(i)
            val videoUrl = videoItem?.getString("videoUrl")

            if (isSetup) {
                return
            }

            // Video URL is the only required prop so we need to make sure is defined before adding it to the playlist
            if (videoUrl != null) {

                // Instantiate the playlist item
                val playlistItem = PlaylistItem(videoUrl)

                // Ad poster image
                val imageUrl = videoItem.getString("imageUrl")
                if (imageUrl != null) {
                    playlistItem.image = imageUrl
                }

                // Add pre-roll ad
                // https://developer.jwplayer.com/sdk/android/docs/developer-guide/advertising/overview/
                val adPreUrl = videoItem.getString("adPreUrl")
                if (adPreUrl != null && adPreUrl.isNotBlank() && (rnProps.dangerouslySeekTime == 0.0 || rnProps.dangerouslySeekTime == 1.0 || i > 0)) {

                    val adBreakBuilder = AdBreak.Builder()
                        .offset("pre")
                        .source(AdSource.IMA)

                    // Apply LAT to the pre roll URL
                    val adPreUrlWithLat = applyParamsToUrl(adPreUrl, latParams)

                    // Apply IAB string to the pre roll URL
                    val modifiedPreUrl = applyIabParamToUrl(adPreUrlWithLat, context)

                    // Pass pre roll URL to the builder
                    adBreakBuilder.tag(modifiedPreUrl)

                    // Load the amazon custom params and pass it on the builder
                    val amazonParams = loadAmazonPrerollParams(context, adLogger)
                    if (amazonParams.isNotEmpty()) {
                        adBreakBuilder.customParams(amazonParams)
                    }

                    playlistItem.adSchedule = listOf(adBreakBuilder.build())

                    adLogger.with("setPlaylist") {
                        log("adPreUrl", adPreUrl)
                        log("latParams", latParams)
                        log("amazonParams", amazonParams)
                        log("modifiedPreUrl", modifiedPreUrl)

                        // Parse out the `ttid` for logging
                        val parsedUrl = Uri.parse(adPreUrl)
                        val ttid = parsedUrl.getQueryParameter("ttid")
                        log("ttid", ttid)
                        val isUgc = parsedUrl.getQueryParameter("isUgc")
                        log("isUgc", isUgc)
                    }
                }

                // Add caption track
                // https://developer.jwplayer.com/sdk/android/docs/developer-guide/interaction/captions/
                val captionUrl = videoItem.getString("captionUrl")
                if (captionUrl != null) {
                    Log.d(TAG, captionUrl)
                    val captionCC = Caption.Builder().file(captionUrl).label("Closed Captioning").build()
                    playlistItem.setCaptions(listOf(captionCC))
                }
                // If we have a seek time greater than 0, start the player at that position
                // Must use setStartTime instead of calling 'seek'method since it is broken in JWPlayer SDK 3.12.1+
                if (rnProps.dangerouslySeekTime > 0) {
                    playlistItem.setStartTime(rnProps.dangerouslySeekTime)
                }

                // Add the playlist item to our playlist
                playlist.add(playlistItem)

            }
        }

        // Create a player config for the playlist
        playerConfig.playlist = playlist

        // Set props that come from react native in case they are set before the playlist is called
        playerConfig.autostart = rnProps.isPlaying

        // Don't continue if this job is cancelled
        if (!isActive) return

        // Setup the player config on the current video player instance
        playerView.setup(playerConfig)
        isSetup = true

        // Set props that come from react native in case they are set before the playlist is called
        Log.d(TAG, "setPlaylist rnProps = $rnProps")
        playerView.currentCaptions = if (rnProps.isCCOn) 1 else 0
        playerView.mute = !rnProps.isAudioOn
        playerView.controls = rnProps.shouldShowControls

        if (!rnProps.isPlaying) {
            playerView.pause()
        }
    }

    override fun requestLayout() {
        super.requestLayout()

        // The video relies on a measure + layout pass happening after it calls requestLayout().
        // Without this, the widget never actually changes the selection and doesn't call the
        // appropriate listeners. Since we override onLayout in our ViewGroups, a layout pass never
        // happens after a call to requestLayout, so we simulate one here.
        // sources:
        // - https://stackoverflow.com/questions/45915857/jwplayerreactnativeandroid-black-screen-with-audio
        // - https://stackoverflow.com/questions/39836356/react-native-resize-custom-ui-component/39838774#39838774
        post {
            if (mPlayerView != null) {
                measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
                )
                layout(left, top, right, bottom)
            }
        }
    }

    private val JWPlayerRNProps.shouldShowControls: Boolean
        get() = isOnAdBreak || isControlsVisible
}
