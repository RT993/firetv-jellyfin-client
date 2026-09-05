package io.github.rt993.firetvjellyfin.ui.library

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import io.github.rt993.firetvjellyfin.R

/** Hosts [LibraryGridFragment] - a full-screen poster grid for one library (Movies, TV Shows, ...). */
class LibraryGridActivity : FragmentActivity(R.layout.activity_library_grid) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.library_grid_fragment, LibraryGridFragment())
                .commit()
        }
    }

    companion object {
        const val EXTRA_LIBRARY_ID = "extra_library_id"
        const val EXTRA_TITLE = "extra_title"
    }
}
