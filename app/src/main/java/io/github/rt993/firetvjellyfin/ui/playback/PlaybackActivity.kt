package io.github.rt993.firetvjellyfin.ui.playback

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import io.github.rt993.firetvjellyfin.R

class PlaybackActivity : FragmentActivity(R.layout.activity_playback) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.playback_fragment, PlaybackVideoFragment())
                .commit()
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_ITEM_NAME = "extra_item_name"
        /** Where to start playback from, in Jellyfin's 100-ns ticks - 0 (the default) starts from the beginning. */
        const val EXTRA_START_POSITION_TICKS = "extra_start_position_ticks"
    }
}
