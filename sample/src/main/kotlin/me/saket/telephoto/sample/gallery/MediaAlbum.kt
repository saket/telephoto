package me.saket.telephoto.sample.gallery

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MediaAlbum(
  val items: List<MediaItem>
) : Parcelable

sealed interface MediaItem : Parcelable {
  val caption: String

  @Parcelize
  data class NormalSizedLocalImage(
    override val caption: String
  ) : MediaItem

  @Parcelize
  data class NormalSizedRemoteImage(
    override val caption: String
  ) : MediaItem

  @Parcelize
  data class SubSampledImage(
    override val caption: String
  ) : MediaItem
}
