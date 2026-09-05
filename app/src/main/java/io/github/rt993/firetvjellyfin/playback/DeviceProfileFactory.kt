package io.github.rt993.firetvjellyfin.playback

import org.jellyfin.sdk.model.api.CodecProfile
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileCondition
import org.jellyfin.sdk.model.api.ProfileConditionType
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile

/**
 * Describes what this app/device can play, so the Jellyfin server can tell us in one round trip
 * (see [io.github.rt993.firetvjellyfin.data.JellyfinRepository.getPlaybackInfo]) whether an item
 * can be direct-played or must be transcoded. This is the single place that encodes the device's
 * playback capabilities - see [PlaybackDecisionMaker] for how the server's answer is then acted on.
 *
 * The codec lists below are a reasonable baseline for Fire TV Stick hardware decoders (H.264 and
 * HEVC main/main10 up to 1080p-4K depending on the specific stick generation, AAC/AC3/EAC3 audio
 * passthrough or decode). Tune these against the actual target device's supported formats -
 * MediaCodecList/CodecCapabilities can be queried at runtime for a more precise profile than this
 * static one.
 */
fun buildDeviceProfile(): DeviceProfile = DeviceProfile(
    name = "TreeHouse",
    maxStreamingBitrate = 120_000_000,
    maxStaticBitrate = 100_000_000,
    musicStreamingTranscodingBitrate = 384_000,
    directPlayProfiles = listOf(
        DirectPlayProfile(
            container = "mp4,m4v",
            videoCodec = "h264,hevc",
            audioCodec = "aac,ac3,eac3,mp3,flac",
            type = DlnaProfileType.VIDEO,
        ),
        DirectPlayProfile(
            container = "mkv",
            videoCodec = "h264,hevc,vp9",
            audioCodec = "aac,ac3,eac3,mp3,flac,opus",
            type = DlnaProfileType.VIDEO,
        ),
        DirectPlayProfile(
            container = "webm",
            videoCodec = "vp8,vp9",
            audioCodec = "vorbis,opus",
            type = DlnaProfileType.VIDEO,
        ),
    ),
    transcodingProfiles = listOf(
        TranscodingProfile(
            container = "ts",
            type = DlnaProfileType.VIDEO,
            videoCodec = "h264",
            audioCodec = "aac",
            protocol = MediaStreamProtocol.HLS,
            context = EncodingContext.STREAMING,
            minSegments = 1,
            breakOnNonKeyFrames = true,
            conditions = emptyList(),
        ),
    ),
    containerProfiles = emptyList(),
    codecProfiles = listOf(
        // Cap H.264 to a level most Fire TV Stick decoders handle comfortably at high
        // resolutions; the server will transcode instead of direct playing above this.
        CodecProfile(
            type = CodecType.VIDEO,
            codec = "h264",
            conditions = listOf(
                ProfileCondition(
                    condition = ProfileConditionType.LESS_THAN_EQUAL,
                    property = ProfileConditionValue.VIDEO_LEVEL,
                    value = "51",
                    isRequired = false,
                ),
            ),
            applyConditions = emptyList(),
        ),
    ),
    // Declaring these is what makes Jellyfin hand back a ready-to-fetch external VTT URL
    // (MediaStream.deliveryUrl, deliveryMethod == EXTERNAL) for text-based subtitle formats
    // instead of defaulting to burning them into the video or leaving them embedded and
    // unusable - without any subtitleProfiles at all, the server has no idea this client can
    // sideload subtitles on its own. Image-based formats (PGS/VobSub) aren't listed - ExoPlayer
    // can't render bitmap subtitles, and burning them in would mean a transcode restart.
    subtitleProfiles = listOf("srt", "subrip", "ass", "ssa", "vtt").map { format ->
        SubtitleProfile(format = format, method = SubtitleDeliveryMethod.EXTERNAL)
    },
)

/** Maximum bitrate (bits/sec) this app will ever request for direct play or transcode streams. */
const val MAX_STREAMING_BITRATE = 120_000_000
