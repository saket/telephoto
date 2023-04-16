package me.saket.telephoto.zoomable.glide

import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.EncodeStrategy
import com.bumptech.glide.load.engine.DiskCacheStrategy

open class ForwardingDiskCacheStrategy(
  private val delegate: DiskCacheStrategy
) : DiskCacheStrategy() {

  override fun isDataCacheable(dataSource: DataSource) = delegate.isDataCacheable(dataSource)

  override fun isResourceCacheable(
    isFromAlternateCacheKey: Boolean,
    dataSource: DataSource,
    encodeStrategy: EncodeStrategy
  ) = delegate.isResourceCacheable(isFromAlternateCacheKey, dataSource, encodeStrategy)

  override fun decodeCachedResource() = delegate.decodeCachedResource()

  override fun decodeCachedData() = delegate.decodeCachedData()
}
