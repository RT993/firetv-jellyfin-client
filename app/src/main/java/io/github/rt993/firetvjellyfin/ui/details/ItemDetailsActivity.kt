package io.github.rt993.firetvjellyfin.ui.details

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import io.github.rt993.firetvjellyfin.R

class ItemDetailsActivity : FragmentActivity(R.layout.activity_item_details) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.item_details_fragment, ItemDetailsFragment())
                .commit()
        }
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
    }
}
