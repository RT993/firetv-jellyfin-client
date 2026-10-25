package io.github.rt993.firetvjellyfin.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import io.github.rt993.firetvjellyfin.R
import io.github.rt993.firetvjellyfin.data.JellyfinRepository
import org.jellyfin.sdk.model.api.BaseItemDto

private const val CARD_WIDTH_DP = 150
private const val CARD_HEIGHT_DP = 225
private const val CARD_CORNER_RADIUS_DP = 14
private const val FOCUS_SCALE = 1.12f
private const val FOCUS_ELEVATION_DP = 18
private const val FOCUS_IN_ANIM_MS = 220L
private const val FOCUS_OUT_ANIM_MS = 150L
private const val FOCUS_OVERSHOOT_TENSION = 2.5f

// Same 3D tilt as the Compose poster cards elsewhere in the app (see FocusableCard) - rotationY is
// a plain View property too, so the same tasteful tilt-on-focus treatment doesn't need Compose to
// work here. Left at the platform's default camera distance (not a custom, exaggerated one like the
// Compose version's) since that default already reads as a natural tilt for angles this small.
// Y-axis only, not X: tipping the top edge back made the elevation shadow (cast from the flat,
// untilted outline, never reprojected in 3D) stop lining up with the foreshortened top edge, leaving
// a sliver of square shadow corner visible above the rounded card - see TvCard.kt's FocusableCard
// for the same fix.
private const val TILT_ROTATION_Y = 10f
private const val FOCUS_BORDER_WIDTH_DP = 2

/** Renders a single [BaseItemDto] as a poster card inside a leanback row. */
class CardPresenter(private val repository: JellyfinRepository) : Presenter() {

    // Built once and reused for every card (same size/color every time, no reason to redraw it on
    // every single bind/recycle) - see placeholderDrawable() for why this exists as a real Bitmap
    // rather than an XML shape drawable.
    private var cachedPlaceholder: Drawable? = null

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(parent.context, CARD_WIDTH_DP, CARD_HEIGHT_DP)
            infoAreaBackground = ColorDrawable(Color.TRANSPARENT)
            // infoAreaBackground only clears the info strip's own background - ImageCardView's
            // root still paints Leanback's default card-background color underneath it, which can
            // show through at the edges even with the strip itself transparent.
            background = ColorDrawable(Color.TRANSPARENT)
            clipRoundedCorners(context)
            foreground = focusBorderDrawable(context).apply { alpha = 0 }

            val focusElevationPx = dpToPx(context, FOCUS_ELEVATION_DP)
            onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                val animationSpec = if (hasFocus) FOCUS_IN_ANIM_MS else FOCUS_OUT_ANIM_MS
                // A slight overshoot on the way in gives the card a springy "pop"; settling back
                // down plays smooth instead, so it doesn't visibly overshoot below 1x/0deg.
                val interpolator = if (hasFocus) OvershootInterpolator(FOCUS_OVERSHOOT_TENSION) else DecelerateInterpolator()
                view.animate()
                    .scaleX(if (hasFocus) FOCUS_SCALE else 1f)
                    .scaleY(if (hasFocus) FOCUS_SCALE else 1f)
                    .translationZ(if (hasFocus) focusElevationPx else 0f)
                    .rotationY(if (hasFocus) TILT_ROTATION_Y else 0f)
                    .setDuration(animationSpec)
                    .setInterpolator(interpolator)
                    .start()
                // No separate parallax scale on mainImageView itself (unlike the Compose cards'
                // Modifier.focusParallax) - an independent ViewPropertyAnimator scale on the child,
                // stacked on top of the card's own scale, doesn't have the same clip-correctness
                // guarantee Compose's nested clip gets for free, and was the one thing structurally
                // different about this card vs every other place cards round correctly. It was the
                // actual cause of the square-corner artifact surviving every other fix tried here
                // (rotationX removal, disabling Leanback's auto-shadow, baking rounding into both
                // the real artwork's and the placeholder's bitmaps) - all real, legitimate fixes for
                // the mechanisms they addressed, but this compounding extra scale was still enough
                // to push the baked-in rounding out of alignment with the card's own border. The
                // card-level FOCUS_SCALE pop is enough focus feedback on its own without it.
                view.foreground?.alpha = if (hasFocus) 255 else 0
            }
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val cardView = viewHolder.view as ImageCardView
        val baseItem = item as? BaseItemDto ?: return
        val imageView = cardView.mainImageView ?: return

        cardView.titleText = baseItem.name
        cardView.contentText = baseItem.productionYear?.toString().orEmpty()

        // Rounded via a Glide bitmap transform, not just the View-level clipToOutline below - the
        // parallax scale animation on this ImageView (see onCreateViewHolder) promotes it to its
        // own hardware layer while animating, which can render outside its parent's clip region
        // regardless of clipToOutline, leaving the image's actual square corners visible past the
        // rounded white border. Baking the rounding into the decoded bitmap itself sidesteps that
        // entirely - the pixels outside the rounded shape are simply gone before they ever reach
        // the ImageView.
        val radiusPx = dpToPx(cardView.context, CARD_CORNER_RADIUS_DP).toInt()
        val placeholder = placeholderDrawable(cardView.context)
        val imageUrl = repository.buildImageUrl(baseItem.id)
        Glide.with(cardView.context)
            .load(imageUrl)
            .transform(CenterCrop(), RoundedCorners(radiusPx))
            .placeholder(placeholder)
            .error(placeholder)
            .into(imageView)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
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

    /**
     * A solid-color rounded-rect, shown while an image is loading and on load failure - a real
     * Bitmap, not an XML shape drawable. A plain shape drawable reports -1 for its intrinsic
     * width/height, and ImageView's CENTER_CROP scaling math (used to position mainImageView's
     * content) relies on that intrinsic size to compute how to scale/position whatever it's
     * given; with no real size to work from, that produced a bogus scale factor that pushed the
     * rounded corners outside the visible frame entirely - tried fixing that by giving the shape
     * drawable an explicit <size>, which didn't hold up either. A genuine Bitmap sidesteps the
     * question entirely: it has a real, unambiguous intrinsic size (its own pixel dimensions), the
     * exact same way the actually-loaded artwork does once RoundedCorners bakes its rounding in.
     */
    private fun placeholderDrawable(context: Context): Drawable {
        cachedPlaceholder?.let { return it }
        val widthPx = dpToPx(context, CARD_WIDTH_DP).toInt()
        val heightPx = dpToPx(context, CARD_HEIGHT_DP).toInt()
        val radiusPx = dpToPx(context, CARD_CORNER_RADIUS_DP)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.card_default) }
        Canvas(bitmap).drawRoundRect(RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat()), radiusPx, radiusPx, paint)
        return BitmapDrawable(context.resources, bitmap).also { cachedPlaceholder = it }
    }

    /** A white rounded-rect outline, same width/corner radius as the Compose cards' focus border
     * (see FocusableCard) - toggled visible/invisible on focus rather than drawn always-transparent,
     * since GradientDrawable's own alpha animates less smoothly than a plain View property would. */
    private fun focusBorderDrawable(context: Context): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(context, CARD_CORNER_RADIUS_DP)
        setStroke(dpToPx(context, FOCUS_BORDER_WIDTH_DP).toInt(), Color.WHITE)
        setColor(Color.TRANSPARENT)
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
