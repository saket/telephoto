package me.saket.telephoto.subsamplingimage.decoder;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import androidx.annotation.NonNull;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Default implementation of {@link me.saket.telephoto.subsamplingimage.decoder.ImageRegionDecoder}
 * using Android's {@link android.graphics.BitmapRegionDecoder}, based on the Skia library. This
 * works well in most circumstances and has reasonable performance due to the cached decoder instance,
 * however it has some problems with grayscale, indexed and CMYK images.
 *
 * A {@link ReadWriteLock} is used to delegate responsibility for multi threading behaviour to the
 * {@link BitmapRegionDecoder} instance on SDK &gt;= 21, whilst allowing this class to block until no
 * tiles are being loaded before recycling the decoder. In practice, {@link BitmapRegionDecoder} is
 * synchronized internally so this has no real impact on performance.
 */
public class SkiaImageRegionDecoderOld implements ImageRegionDecoder {

  private BitmapRegionDecoder decoder;
  private final ReadWriteLock decoderLock = new ReentrantReadWriteLock(true);

  private static final String ASSET_PREFIX = "file://" + "/android_asset/";

  @Override
  @NonNull
  public Point init(Context context, @NonNull Uri uri) throws Exception {
    String uriString = uri.toString();

    String assetName = uriString.substring(ASSET_PREFIX.length());
    decoder = BitmapRegionDecoder.newInstance(context.getAssets().open(assetName, AssetManager.ACCESS_RANDOM), false);

    return new Point(decoder.getWidth(), decoder.getHeight());
  }

  @Override
  @NonNull
  public Bitmap decodeRegion(@NonNull Rect sRect, int sampleSize) {
    getDecodeLock().lock();
    try {
      if (decoder != null && !decoder.isRecycled()) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bitmap = decoder.decodeRegion(sRect, options);
        if (bitmap == null) {
          throw new RuntimeException("Skia image decoder returned null bitmap - image format may not be supported");
        }
        return bitmap;
      } else {
        throw new IllegalStateException("Cannot decode region after decoder has been recycled");
      }
    } finally {
      getDecodeLock().unlock();
    }
  }

  @Override
  public synchronized boolean isReady() {
    return decoder != null && !decoder.isRecycled();
  }

  @Override
  public synchronized void recycle() {
    decoderLock.writeLock().lock();
    try {
      decoder.recycle();
      decoder = null;
    } finally {
      decoderLock.writeLock().unlock();
    }
  }

  private Lock getDecodeLock() {
    return decoderLock.readLock();
  }
}
