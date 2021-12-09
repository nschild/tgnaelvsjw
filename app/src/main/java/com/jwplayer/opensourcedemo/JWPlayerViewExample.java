package com.jwplayer.opensourcedemo;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;

import com.google.android.gms.cast.framework.CastContext;
import com.jwplayer.pub.api.JWPlayer;
import com.jwplayer.pub.api.configuration.PlayerConfig;
import com.jwplayer.pub.api.events.EventType;
import com.jwplayer.pub.api.events.FullscreenEvent;
import com.jwplayer.pub.api.events.listeners.VideoPlayerEvents;
import com.jwplayer.pub.api.license.LicenseUtil;
import com.jwplayer.pub.api.media.playlists.PlaylistItem;
import com.jwplayer.pub.view.JWPlayerView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;


public class JWPlayerViewExample extends AppCompatActivity
		implements VideoPlayerEvents.OnFullscreenListener {

	private JWPlayerView mPlayerView;

	private CastContext mCastContext;

	private CallbackScreen mCallbackScreen;
	private JWPlayer mPlayer;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_jwplayerview);

		// TODO: Add your license key
		LicenseUtil.setLicenseKey(this, REMOVED);
		mPlayerView = findViewById(R.id.jwplayer);
		mPlayer = mPlayerView.getPlayer();


		// Handle hiding/showing of ActionBar
		mPlayer.addListener(EventType.FULLSCREEN, this);

		// Keep the screen on during playback
		new KeepScreenOnHandler(mPlayer, getWindow());

		// Event Logging
		mCallbackScreen = findViewById(R.id.callback_screen);
		mCallbackScreen.registerListeners(mPlayer);

		// Load a media source
		PlaylistItem playlistItem = new PlaylistItem.Builder()
				.file("https://livevideodev.tegnadigital.com/hls/live/2014538/stage/elvs/live2.m3u8")
				.image("https://www.mydomain.com/poster.jpg")
				.title("Playlist-Item Title")
				.description("Some really great content")
				.build();

		List<PlaylistItem> playlist = new ArrayList<>();
		playlist.add(playlistItem);

		PlayerConfig config = new PlayerConfig.Builder()
				.playlist(playlist)
				.build();

		mPlayer.setup(config);

		// Get a reference to the CastContext
		mCastContext = CastContext.getSharedInstance(this);


	}


	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		if (!mPlayer.isInPictureInPictureMode()) {
			final boolean isFullscreen = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE;
			mPlayer.setFullscreen(isFullscreen, true);
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// Exit fullscreen when the user pressed the Back button
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (mPlayer.getFullscreen()) {
				mPlayer.setFullscreen(false, true);
				return false;
			}
		}
		return super.onKeyDown(keyCode, event);
	}


	@Override
	public void onFullscreen(FullscreenEvent fullscreenEvent) {
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			if (fullscreenEvent.getFullscreen()) {
				actionBar.hide();
			} else {
				actionBar.show();
			}
		}
	}
}
