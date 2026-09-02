package io.github.rt993.firetvjellyfin.ui.details

import android.os.Bundle
import android.view.KeyEvent
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

    // Intercepting here, before the key reaches whichever view is focused, is what lets
    // ItemDetailsFragment.consumeDownFromOverviewRow() pre-empt Leanback's own (buggy) row
    // transition instead of racing it - see that method for why.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val fragment = supportFragmentManager.findFragmentById(R.id.item_details_fragment) as? ItemDetailsFragment
            if (fragment?.consumeDownFromOverviewRow() == true) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_ITEM_ID = "extra_item_id"
    }
}
