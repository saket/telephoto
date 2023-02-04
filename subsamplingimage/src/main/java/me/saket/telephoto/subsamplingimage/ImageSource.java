package me.saket.telephoto.subsamplingimage;

import android.net.Uri;
import androidx.annotation.NonNull;

/**
 * Helper class used to set the source and additional attributes from a variety of sources. Supports
 * use of a bitmap, asset, resource, external file or any other URI.
 */
public final class ImageSource {
  final Uri uri;

  private ImageSource(@NonNull Uri uri) {
    this.uri = uri;
  }

  /**
   * Create an instance from an asset name.
   *
   * @param assetName asset name.
   * @return an {@link ImageSource} instance.
   */
  @NonNull
  public static ImageSource asset(@NonNull String assetName) {
    return new ImageSource(Uri.parse("file:///android_asset/" + assetName));
  }
}
