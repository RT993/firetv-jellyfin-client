package io.github.rt993.firetvjellyfin.ui.home

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType

private const val CARD_WIDTH_DP = 150
private const val CARD_HEIGHT_DP = 225
private const val CARD_CORNER_RADIUS_DP = 14
private const val FOCUS_SCALE = 1.12f
private const val FOCUS_ELEVATION_DP = 18
private const val FOCUS_IN_ANIM_MS = 220L
private const val FOCUS_OUT_ANIM_MS = 150L
private const val FOCUS_OVERSHOOT_TENSION = 2.5f
private const val PREVIEW_DELAY_MS = 550L
private const val PREVIEW_CROSSFADE_MS = 300

/**
 * Renders a single [BaseItemDto] as a poster card inside a leanback row. When [previewOnFocus] is
 * on (used for the Recently Added row), holding focus on a card for a moment crossfades its
 * poster to that title's backdrop - a lightweight stand-in for a hover preview, since D-pad focus
 * has no equivalent of a mouse hover and this app has no video-preview infrastructure.
 */
class CardPresenter(
    private val repository: JellyfinRepository,
    private val previewOnFocus: Boolean = false,
) : Presenter() {

    private class CardViewHolder(view: View) : Presenter.ViewHolder(view) {
        var item: BaseItemDto? = null
        var pendingPreview: Runnable? = null
        var isShowingPreview = false
    }

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(parent.context, CARD_WIDTH_DP, CARD_HEIGHT_DP)
            infoAreaBackground = ColorDrawable(Color.TRANSPARENT)
            clipRoundedCorners(context)
        }
        val holder = CardViewHolder(cardView)

        val focusElevationPx = dpToPx(cardView.context, FOCUS_ELEVATION_DP)
        cardView.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            view.animate()
                .scaleX(if (hasFocus) FOCUS_SCALE else 1f)
                .scaleY(if (hasFocus) FOCUS_SCALE else 1f)
                .translationZ(if (hasFocus) focusElevationPx else 0f)
                .setDuration(if (hasFocus) FOCUS_IN_ANIM_MS else FOCUS_OUT_ANIM_MS)
                // A slight overshoot on the way in gives the card a springy "pop"; settling
                // back down plays smooth instead, so it doesn't visibly overshoot below 1x.
                .setInterpolator(if (hasFocus) OvershootInterpolator(FOCUS_OVERSHOOT_TENSION) else DecelerateInterpolator())
                .start()

            if (previewOnFocus) updatePreview(cardView, holder, hasFocus)
        }
        return holder
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val holder = viewHolder as CardViewHolder
        val cardView = holder.view as ImageCardView
        val baseItem = item as? BaseItemDto ?: return
        val imageView = cardView.mainImageView ?: return

        cancelPendingPreview(cardView, holder)
        holder.item = baseItem
        cardView.titleText = baseItem.name
        cardView.contentText = baseItem.productionYear?.toString().orEmpty()

        val imageUrl = repository.buildImageUrl(baseItem.id)
        Glide.with(cardView.context)
            .load(imageUrl)
            .placeholder(R.drawable.card_placeholder)
            .error(R.drawable.card_placeholder)
            .into(imageView)
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        val holder = viewHolder as CardViewHolder
        val cardView = holder.view as ImageCardView
        cancelPendingPreview(cardView, holder)
        holder.item = null
        // RecyclerView recycles/unbinds every view as part of the host Activity's own teardown
        // (removeAndRecycleAllViews), which can run after the Activity is already marked
        // destroyed - Glide.with() asserts against that and throws IllegalArgumentException,
        // crashing the whole app on the way out. Clearing an in-flight load for a view that's
        // being torn down anyway is a no-op at that point regardless.
        runCatching {
            cardView.mainImageView?.let { Glide.with(cardView.context).clear(it) }
        }
        cardView.mainImage = null
    }

    /** Schedules (or cancels) the delayed poster-to-backdrop crossfade as focus changes. */
    private fun updatePreview(cardView: ImageCardView, holder: CardViewHolder, hasFocus: Boolean) {
        cancelPendingPreview(cardView, holder)
        val item = holder.item ?: return
        if (hasFocus) {
            if (item.backdropImageTags.isNullOrEmpty()) return
            val runnable = Runnable {
                holder.isShowingPreview = true
                loadInto(cardView, repository.buildImageUrl(item.id, imageType = ImageType.BACKDROP, maxWidth = 500))
            }
            holder.pendingPreview = runnable
            cardView.postDelayed(runnable, PREVIEW_DELAY_MS)
        } else if (holder.isShowingPreview) {
            holder.isShowingPreview = false
            loadInto(cardView, repository.buildImageUrl(item.id))
        }
    }

    private fun cancelPendingPreview(cardView: ImageCardView, holder: CardViewHolder) {
        holder.pendingPreview?.let { cardView.removeCallbacks(it) }
        holder.pendingPreview = null
    }

    private fun loadInto(cardView: ImageCardView, url: String) {
        val imageView = cardView.mainImageView ?: return
        Glide.with(cardView.context)
            .load(url)
            .transition(DrawableTransitionOptions.withCrossFade(PREVIEW_CROSSFADE_MS))
            .into(imageView)
    }

    private fun ImageCardView.setMainImageDimensions(context: Context, widthDp: Int, heightDp: Int) {
        setMainImageDimensions(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, widthDp.toFloat(), context.resources.displayMetrics).toInt(),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, heightDp.toFloat(), context.resources.displayMetrics).toInt(),
        )
    }

    /** Rounded corners, clipped to match, with a native elevation shadow that grows on focus. */
    private fun ImageCardView.clipRoundedCorners(context: Context) {
        val radiusPx = dpToPx(context, CARD_CORNER_RADIUS_DP)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        clipToOutline = true
    }

    private fun dpToPx(context: Context, dp: Int): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics)
}
