package me.saket.telephoto.subsamplingimage;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import androidx.annotation.Nullable;
import android.net.Uri;
import android.os.AsyncTask;
import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import me.saket.telephoto.subsamplingimage.decoder.ImageRegionDecoder;
import me.saket.telephoto.subsamplingimage.decoder.SkiaImageRegionDecoder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * <p>
 * Displays an image subsampled as necessary to avoid loading too much image data into memory. After zooming in,
 * a set of image tiles subsampled at higher resolution are loaded and displayed over the base layer. During pan and
 * zoom, tiles off screen or higher/lower resolution than required are discarded from memory.
 * </p><p>
 * Tiles are no larger than the max supported bitmap size, so with large images tiling may be used even when zoomed out.
 * </p><p>
 * v prefixes - coordinates, translations and distances measured in screen (view) pixels
 * <br>
 * s prefixes - coordinates, translations and distances measured in rotated and cropped source image pixels (scaled)
 * <br>
 * f prefixes - coordinates, translations and distances measured in original unrotated, uncropped source file pixels
 * </p><p>
 * <a href="https://github.com/davemorrissey/subsampling-scale-image-view">View project on GitHub</a>
 * </p>
 */
public class SubsamplingScaleImageView extends View {

    private static final String TAG = SubsamplingScaleImageView.class.getSimpleName();

    /** Display the image file in its native orientation. */
    public static final int ORIENTATION_0 = 0;

    // Uri of full size image
    private Uri uri;

    // Sample size used to display the whole image when fully zoomed out
    private int fullImageSampleSize;

    // Map of zoom level to tile grid
    private Map<Integer, List<Tile>> tileMap;

    // Overlay tile boundaries and other info
    private final boolean debug = true;

    // Image orientation setting
    private static final int orientation = ORIENTATION_0;

    // Max scale allowed (prevent infinite zoom)
    private float maxScale = 2F;

    // Density to reach before loading higher resolution tiles
    private int minimumTileDpi = -1;

    // An executor service for loading of images
    private final Executor executor = AsyncTask.THREAD_POOL_EXECUTOR;

    /**
     * Enable or disable eager loading of tiles that appear on screen during gestures or animations,
     * while the gesture or animation is still in progress. By default this is enabled to improve
     * responsiveness, but it can result in tiles being loaded and discarded more rapidly than
     * necessary and reduce the animation frame rate on old/cheap devices. Disable this on older
     * devices if you see poor performance. Tiles will then be loaded only when gestures and animations
     * are completed.
     * @param eagerLoadingEnabled true to enable loading during gestures, false to delay loading until gestures end
     */
    // Whether tiles should be loaded while gestures and animations are still in progress
    private final boolean eagerLoadingEnabled = true;

    // Current scale and scale at start of zoom
    private float scale;
    private float scaleStart;

    // Screen coordinate of top-left corner of source image
    private PointF vTranslate;
    private PointF vTranslateStart;
    private PointF vTranslateBefore;

    // Source coordinate to center on, used when new position is set externally before view is ready
    private Float pendingScale;
    private PointF sPendingCenter;

    // Source image dimensions and orientation - dimensions relate to the unrotated image
    private int sWidth;
    private int sHeight;

    // Is two-finger zooming in progress
    private boolean isZooming;
    // Is one-finger panning in progress
    private boolean isPanning;
    // Max touches used in current gesture
    private int maxTouchCount;

    // Tile and image decoding
    private ImageRegionDecoder decoder;

    // Debug values
    private PointF vCenterStart;
    private float vDistStart;

    // Whether a ready notification has been sent to subclasses
    private boolean readySent;
    // Whether a base layer loaded notification has been sent to subclasses
    private boolean imageLoadedSent;

    // Paint objects created once and reused for efficiency
    private Paint bitmapPaint;
    private Paint debugTextPaint;
    private Paint debugLinePaint;

    // Volatile fields used to reduce object creation
    private ScaleAndTranslate satTemp;
    private Matrix matrix;
    private final float[] srcArray = new float[8];
    private final float[] dstArray = new float[8];

    //The logical density of the display
    private final float density;

    public SubsamplingScaleImageView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setMinimumDpi(160);
        setMinimumTileDpi(320);
    }

    /**
     * Set the image source from a bitmap, resource, asset, file or other URI, providing a preview image to be
     * displayed until the full size image is loaded, starting with a given orientation setting, scale and center.
     * This is the best method to use when you want scale and center to be restored after screen orientation change;
     * it avoids any redundant loading of tiles in the wrong orientation.
     * You must declare the dimensions of the full size image by calling ImageSource#dimensions(int, int)
     * on the imageSource object. The preview source will be ignored if you don't provide dimensions,
     * and if you provide a bitmap for the full size image.
     * @param imageSource Image source. Dimensions must be declared.
     */
    public final void setImage(@NonNull ImageSource imageSource) {
        //noinspection ConstantConditions
        if (imageSource == null) {
            throw new NullPointerException("imageSource must not be null");
        }

        reset(true);

        uri = imageSource.uri;

        // Load the bitmap using tile decoding.
        TilesInitTask task = new TilesInitTask(this, uri);
        execute(task);
    }

    /**
     * Reset all state before setting/changing image or setting new rotation.
     */
    private void reset(boolean newImage) {
        debug("reset newImage=" + newImage);
        scale = 0f;
        scaleStart = 0f;
        vTranslate = null;
        vTranslateStart = null;
        vTranslateBefore = null;
        pendingScale = 0f;
        sPendingCenter = null;
        isZooming = false;
        isPanning = false;
        maxTouchCount = 0;
        fullImageSampleSize = 0;
        vCenterStart = null;
        vDistStart = 0;
        satTemp = null;
        matrix = null;
        if (newImage) {
            uri = null;
            if (decoder != null) {
                decoder.recycle();
                decoder = null;
            }
            sWidth = 0;
            sHeight = 0;
            readySent = false;
            imageLoadedSent = false;
        }
        if (tileMap != null) {
            for (Map.Entry<Integer, List<Tile>> tileMapEntry : tileMap.entrySet()) {
                for (Tile tile : tileMapEntry.getValue()) {
                    tile.visible = false;
                    if (tile.bitmap != null) {
                        tile.bitmap.recycle();
                        tile.bitmap = null;
                    }
                }
            }
            tileMap = null;
        }
    }

    /**
     * On resize, preserve center and scale. Various behaviours are possible, override this method to use another.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        debug("onSizeChanged %dx%d -> %dx%d", oldw, oldh, w, h);
        PointF sCenter = getCenter();
        if (readySent && sCenter != null) {
            this.pendingScale = scale;
            this.sPendingCenter = sCenter;
        }
    }

    /**
     * Measures the width and height of the view, preserving the aspect ratio of the image displayed if wrap_content is
     * used. The image will scale within this box, not resizing the view as it is zoomed.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        boolean resizeWidth = widthSpecMode != MeasureSpec.EXACTLY;
        boolean resizeHeight = heightSpecMode != MeasureSpec.EXACTLY;
        int width = parentWidth;
        int height = parentHeight;
        if (sWidth > 0 && sHeight > 0) {
            if (resizeWidth && resizeHeight) {
                width = sWidth;
                height = sHeight;
            } else if (resizeHeight) {
                height = (int)((((double) sHeight /(double) sWidth) * width));
            } else if (resizeWidth) {
                width = (int)((((double) sWidth /(double) sHeight) * height));
            }
        }
        width = Math.max(width, getSuggestedMinimumWidth());
        height = Math.max(height, getSuggestedMinimumHeight());
        setMeasuredDimension(width, height);
    }

    /**
     * Handle touch events. One finger pans, and two finger pinch and zoom plus panning.
     */
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (vTranslateStart == null) { vTranslateStart = new PointF(0, 0); }
        if (vTranslateBefore == null) { vTranslateBefore = new PointF(0, 0); }
        if (vCenterStart == null) { vCenterStart = new PointF(0, 0); }

        vTranslateBefore.set(vTranslate);

        boolean handled = onTouchEventInternal(event);
        return handled || super.onTouchEvent(event);
    }

    @SuppressWarnings("deprecation")
    private boolean onTouchEventInternal(@NonNull MotionEvent event) {
        int touchCount = event.getPointerCount();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_1_DOWN:
            case MotionEvent.ACTION_POINTER_2_DOWN:
                maxTouchCount = Math.max(maxTouchCount, touchCount);
                if (touchCount >= 2) {
                    // Start pinch to zoom. Calculate distance between touch points and center point of the pinch.
                    float distance = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1));
                    scaleStart = scale;
                    vDistStart = distance;
                    vTranslateStart.set(vTranslate.x, vTranslate.y);
                    vCenterStart.set((event.getX(0) + event.getX(1))/2, (event.getY(0) + event.getY(1))/2);
                } else {
                    // Start one-finger pan
                    vTranslateStart.set(vTranslate.x, vTranslate.y);
                    vCenterStart.set(event.getX(), event.getY());
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                boolean consumed = false;
                if (maxTouchCount > 0) {
                    if (touchCount >= 2) {
                        // Calculate new distance between touch points, to scale and pan relative to start values.
                        float vDistEnd = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1));
                        float vCenterEndX = (event.getX(0) + event.getX(1))/2;
                        float vCenterEndY = (event.getY(0) + event.getY(1))/2;

                        if ((distance(vCenterStart.x, vCenterEndX, vCenterStart.y, vCenterEndY) > 5
                            || Math.abs(vDistEnd - vDistStart) > 5
                            || isPanning)) {
                            isZooming = true;
                            isPanning = true;
                            consumed = true;

                            double previousScale = scale;
                            scale = Math.min(maxScale, (vDistEnd / vDistStart) * scaleStart);

                            if (scale <= minScale()) {
                                // Minimum scale reached so don't pan. Adjust start settings so any expand will zoom in.
                                vDistStart = vDistEnd;
                                scaleStart = minScale();
                                vCenterStart.set(vCenterEndX, vCenterEndY);
                                vTranslateStart.set(vTranslate);
                            } else {
                                // Translate to place the source image coordinate that was at the center of the pinch at the start
                                // at the center of the pinch now, to give simultaneous pan + zoom.
                                float vLeftStart = vCenterStart.x - vTranslateStart.x;
                                float vTopStart = vCenterStart.y - vTranslateStart.y;
                                float vLeftNow = vLeftStart * (scale/scaleStart);
                                float vTopNow = vTopStart * (scale/scaleStart);
                                vTranslate.x = vCenterEndX - vLeftNow;
                                vTranslate.y = vCenterEndY - vTopNow;
                                if ((previousScale * sHeight < getHeight() && scale * sHeight >= getHeight()) || (previousScale * sWidth
                                    < getWidth() && scale * sWidth >= getWidth())) {
                                    fitToBounds(true);
                                    vCenterStart.set(vCenterEndX, vCenterEndY);
                                    vTranslateStart.set(vTranslate);
                                    scaleStart = scale;
                                    vDistStart = vDistEnd;
                                }
                            }

                            fitToBounds(true);
                            refreshRequiredTiles(eagerLoadingEnabled);
                        }
                    } else if (!isZooming) {
                        // One finger pan - translate the image. We do this calculation even with pan disabled so click
                        // and long click behaviour is preserved.
                        float dx = Math.abs(event.getX() - vCenterStart.x);
                        float dy = Math.abs(event.getY() - vCenterStart.y);

                        //On the Samsung S6 long click event does not work, because the dx > 5 usually true
                        float offset = density * 5;
                        if (dx > offset || dy > offset || isPanning) {
                            consumed = true;
                            vTranslate.x = vTranslateStart.x + (event.getX() - vCenterStart.x);
                            vTranslate.y = vTranslateStart.y + (event.getY() - vCenterStart.y);

                            float lastX = vTranslate.x;
                            float lastY = vTranslate.y;
                            fitToBounds(true);
                            boolean atXEdge = lastX != vTranslate.x;
                            boolean atYEdge = lastY != vTranslate.y;
                            boolean edgeXSwipe = atXEdge && dx > dy && !isPanning;
                            boolean edgeYSwipe = atYEdge && dy > dx && !isPanning;
                            if (!edgeXSwipe && !edgeYSwipe && (!atXEdge || !atYEdge || isPanning)) {
                                isPanning = true;
                            } else if (dx > offset || dy > offset) {
                                // Haven't panned the image, and we're at the left or right edge. Switch to page swipe.
                                maxTouchCount = 0;
                            }

                            refreshRequiredTiles(eagerLoadingEnabled);
                        }
                    }
                }
                if (consumed) {
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_POINTER_2_UP:
                if (maxTouchCount > 0 && (isZooming || isPanning)) {
                    if (isZooming && touchCount == 2) {
                        // Convert from zoom to pan with remaining touch
                        isPanning = true;
                        vTranslateStart.set(vTranslate.x, vTranslate.y);
                        if (event.getActionIndex() == 1) {
                            vCenterStart.set(event.getX(0), event.getY(0));
                        } else {
                            vCenterStart.set(event.getX(1), event.getY(1));
                        }
                    }
                    if (touchCount < 3) {
                        // End zooming when only one touch point
                        isZooming = false;
                    }
                    if (touchCount < 2) {
                        // End panning when no touch points
                        isPanning = false;
                        maxTouchCount = 0;
                    }
                    // Trigger load of tiles now required
                    refreshRequiredTiles(true);
                    return true;
                }
                if (touchCount == 1) {
                    isZooming = false;
                    isPanning = false;
                    maxTouchCount = 0;
                }
                return true;
        }
        return false;
    }

    /**
     * Draw method should not be called until the view has dimensions so the first calls are used as triggers to calculate
     * the scaling and tiling required. Once the view is setup, tiles are displayed as they are loaded.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        createPaints();

        // If image or view dimensions are not known yet, abort.
        if (sWidth == 0 || sHeight == 0 || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        // When using tiles, on first render with no tile map ready, initialise it and kick off async base image loading.
        if (tileMap == null && decoder != null) {
            initialiseBaseLayer();
        }

        // If image has been loaded or supplied as a bitmap, onDraw may be the first time the view has
        // dimensions and therefore the first opportunity to set scale and translate. If this call returns
        // false there is nothing to be drawn so return immediately.
        if (!checkReady()) {
            return;
        }

        // Set scale and translate before draw.
        preDraw();

        if (tileMap != null && isBaseLayerReady()) {

            // Optimum sample size for current scale
            int sampleSize = Math.min(fullImageSampleSize, calculateInSampleSize(scale));

            // First check for missing tiles - if there are any we need the base layer underneath to avoid gaps
            boolean hasMissingTiles = false;
            for (Map.Entry<Integer, List<Tile>> tileMapEntry : tileMap.entrySet()) {
                if (tileMapEntry.getKey() == sampleSize) {
                    for (Tile tile : tileMapEntry.getValue()) {
                        if (tile.visible && (tile.loading || tile.bitmap == null)) {
                            hasMissingTiles = true;
                        }
                    }
                }
            }

            // Render all loaded tiles. LinkedHashMap used for bottom up rendering - lower res tiles underneath.
            for (Map.Entry<Integer, List<Tile>> tileMapEntry : tileMap.entrySet()) {
                if (tileMapEntry.getKey() == sampleSize || hasMissingTiles) {
                    for (Tile tile : tileMapEntry.getValue()) {
                        sourceToViewRect(tile.sRect, tile.vRect);
                        if (!tile.loading && tile.bitmap != null) {
                            if (matrix == null) { matrix = new Matrix(); }
                            matrix.reset();
                            setMatrixArray(srcArray, 0, 0, tile.bitmap.getWidth(), 0, tile.bitmap.getWidth(), tile.bitmap.getHeight(), 0, tile.bitmap.getHeight());
                            setMatrixArray(dstArray, tile.vRect.left, tile.vRect.top, tile.vRect.right, tile.vRect.top, tile.vRect.right, tile.vRect.bottom, tile.vRect.left, tile.vRect.bottom);
                            matrix.setPolyToPoly(srcArray, 0, dstArray, 0, 4);
                            canvas.drawBitmap(tile.bitmap, matrix, bitmapPaint);
                            if (debug) {
                                canvas.drawRect(tile.vRect, debugLinePaint);
                            }
                        } else if (tile.loading && debug) {
                            canvas.drawText("LOADING", tile.vRect.left + px(5), tile.vRect.top + px(35), debugTextPaint);
                        }
                        if (tile.visible && debug) {
                            canvas.drawText("ISS " + tile.sampleSize + " RECT " + tile.sRect.top + "," + tile.sRect.left + "," + tile.sRect.bottom + "," + tile.sRect.right, tile.vRect.left + px(5), tile.vRect.top + px(15), debugTextPaint);
                        }
                    }
                }
            }

        }

        if (debug) {
            canvas.drawText("Scale: " + String.format(Locale.ENGLISH, "%.2f", scale) + " (" + String.format(Locale.ENGLISH, "%.2f", minScale()) + " - " + String.format(Locale.ENGLISH, "%.2f", maxScale) + ")", px(5), px(15), debugTextPaint);
            canvas.drawText("Translate: " + String.format(Locale.ENGLISH, "%.2f", vTranslate.x) + ":" + String.format(Locale.ENGLISH, "%.2f", vTranslate.y), px(5), px(30), debugTextPaint);
            PointF center = getCenter();
            //noinspection ConstantConditions
            canvas.drawText("Source center: " + String.format(Locale.ENGLISH, "%.2f", center.x) + ":" + String.format(Locale.ENGLISH, "%.2f", center.y), px(5), px(45), debugTextPaint);

            if (vCenterStart != null) {
                debugLinePaint.setColor(Color.MAGENTA);
                canvas.drawCircle(vCenterStart.x, vCenterStart.y, px(20), debugLinePaint);
            }
            debugLinePaint.setColor(Color.RED);
        }
    }

    /**
     * Helper method for setting the values of a tile matrix array.
     */
    private void setMatrixArray(float[] array, float f0, float f1, float f2, float f3, float f4, float f5, float f6, float f7) {
        array[0] = f0;
        array[1] = f1;
        array[2] = f2;
        array[3] = f3;
        array[4] = f4;
        array[5] = f5;
        array[6] = f6;
        array[7] = f7;
    }

    /**
     * Checks whether the base layer of tiles or full size bitmap is ready.
     */
    private boolean isBaseLayerReady() {
        if (tileMap != null) {
            boolean baseLayerReady = true;
            for (Map.Entry<Integer, List<Tile>> tileMapEntry : tileMap.entrySet()) {
                if (tileMapEntry.getKey() == fullImageSampleSize) {
                    for (Tile tile : tileMapEntry.getValue()) {
                        if (tile.loading || tile.bitmap == null) {
                            baseLayerReady = false;
                        }
                    }
                }
            }
            return baseLayerReady;
        }
        return false;
    }

    /**
     * Check whether view and image dimensions are known and either a preview, full size image or
     * base layer tiles are loaded. First time, send ready event to listener. The next draw will
     * display an image.
     */
    private boolean checkReady() {
        boolean ready = getWidth() > 0 && getHeight() > 0 && sWidth > 0 && sHeight > 0 && isBaseLayerReady();
        if (!readySent && ready) {
            preDraw();
            readySent = true;
        }
        return ready;
    }

    /**
     * Check whether either the full size bitmap or base layer tiles are loaded. First time, send image
     * loaded event to listener.
     */
    private boolean checkImageLoaded() {
        boolean imageLoaded = isBaseLayerReady();
        if (!imageLoadedSent && imageLoaded) {
            preDraw();
            imageLoadedSent = true;
        }
        return imageLoaded;
    }

    /**
     * Creates Paint objects once when first needed.
     */
    private void createPaints() {
        if (bitmapPaint == null) {
            bitmapPaint = new Paint();
            bitmapPaint.setAntiAlias(true);
            bitmapPaint.setFilterBitmap(true);
            bitmapPaint.setDither(true);
        }
        if ((debugTextPaint == null || debugLinePaint == null) && debug) {
            debugTextPaint = new Paint();
            debugTextPaint.setTextSize(px(12));
            debugTextPaint.setColor(Color.MAGENTA);
            debugTextPaint.setStyle(Style.FILL);
            debugLinePaint = new Paint();
            debugLinePaint.setColor(Color.MAGENTA);
            debugLinePaint.setStyle(Style.STROKE);
            debugLinePaint.setStrokeWidth(px(2));
        }
    }

    /**
     * Called on first draw when the view has dimensions. Calculates the initial sample size and starts async loading of
     * the base layer image - the whole source subsampled as necessary.
     */
    private synchronized void initialiseBaseLayer() {
        debug("initialiseBaseLayer");

        satTemp = new ScaleAndTranslate(0f, new PointF(0, 0));
        fitToBounds(true, satTemp);

        // Load double resolution - next level will be split into four tiles and at the center all four are required,
        // so don't bother with tiling until the next level 16 tiles are needed.
        fullImageSampleSize = calculateInSampleSize(satTemp.scale);
        if (fullImageSampleSize > 1) {
            fullImageSampleSize /= 2;
        }

        initialiseTileMap();

        List<Tile> baseGrid = tileMap.get(fullImageSampleSize);
        for (Tile baseTile : baseGrid) {
            TileLoadTask task = new TileLoadTask(this, decoder, baseTile);
            execute(task);
        }
        refreshRequiredTiles(true);
    }

    /**
     * Loads the optimum tiles for display at the current scale and translate, so the screen can be filled with tiles
     * that are at least as high resolution as the screen. Frees up bitmaps that are now off the screen.
     * @param load Whether to load the new tiles needed. Use false while scrolling/panning for performance.
     */
    private void refreshRequiredTiles(boolean load) {
        if (decoder == null || tileMap == null) { return; }

        int sampleSize = Math.min(fullImageSampleSize, calculateInSampleSize(scale));

        // Load tiles of the correct sample size that are on screen. Discard tiles off screen, and those that are higher
        // resolution than required, or lower res than required but not the base layer, so the base layer is always present.
        for (Map.Entry<Integer, List<Tile>> tileMapEntry : tileMap.entrySet()) {
            for (Tile tile : tileMapEntry.getValue()) {
                if (tile.sampleSize < sampleSize || (tile.sampleSize > sampleSize && tile.sampleSize != fullImageSampleSize)) {
                    tile.visible = false;
                    if (tile.bitmap != null) {
                        tile.bitmap.recycle();
                        tile.bitmap = null;
                    }
                }
                if (tile.sampleSize == sampleSize) {
                    if (tileVisible(tile)) {
                        tile.visible = true;
                        if (!tile.loading && tile.bitmap == null && load) {
                            TileLoadTask task = new TileLoadTask(this, decoder, tile);
                            execute(task);
                        }
                    } else if (tile.sampleSize != fullImageSampleSize) {
                        tile.visible = false;
                        if (tile.bitmap != null) {
                            tile.bitmap.recycle();
                            tile.bitmap = null;
                        }
                    }
                } else if (tile.sampleSize == fullImageSampleSize) {
                    tile.visible = true;
                }
            }
        }

    }

    /**
     * Determine whether tile is visible.
     */
    private boolean tileVisible(Tile tile) {
        float sVisLeft = viewToSourceX(0),
            sVisRight = viewToSourceX(getWidth()),
            sVisTop = viewToSourceY(0),
            sVisBottom = viewToSourceY(getHeight());
        return !(sVisLeft > tile.sRect.right || tile.sRect.left > sVisRight || sVisTop > tile.sRect.bottom || tile.sRect.top > sVisBottom);
    }

    /**
     * Sets scale and translate ready for the next draw.
     */
    private void preDraw() {
        if (getWidth() == 0 || getHeight() == 0 || sWidth <= 0 || sHeight <= 0) {
            return;
        }

        // If waiting to translate to new center position, set translate now
        if (sPendingCenter != null && pendingScale != null) {
            scale = pendingScale;
            if (vTranslate == null) {
                vTranslate = new PointF();
            }
            vTranslate.x = (getWidth()/2) - (scale * sPendingCenter.x);
            vTranslate.y = (getHeight()/2) - (scale * sPendingCenter.y);
            sPendingCenter = null;
            pendingScale = null;
            fitToBounds(true);
            refreshRequiredTiles(true);
        }

        // On first display of base image set up position, and in other cases make sure scale is correct.
        fitToBounds(false);
    }

    /**
     * Calculates sample size to fit the source image in given bounds.
     */
    private int calculateInSampleSize(float scale) {
        if (minimumTileDpi > 0) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            float averageDpi = (metrics.xdpi + metrics.ydpi)/2;
            scale = (minimumTileDpi/averageDpi) * scale;
        }

        int reqWidth = (int)(sWidth * scale);
        int reqHeight = (int)(sHeight * scale);

        // Raw height and width of image
        int inSampleSize = 1;
        if (reqWidth == 0 || reqHeight == 0) {
            return 32;
        }

        if (sHeight > reqHeight || sWidth > reqWidth) {

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) sHeight / (float) reqHeight);
            final int widthRatio = Math.round((float) sWidth / (float) reqWidth);

            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = Math.min(heightRatio, widthRatio);
        }

        // We want the actual sample size that will be used, so round down to nearest power of 2.
        int power = 1;
        while (power * 2 < inSampleSize) {
            power = power * 2;
        }

        return power;
    }

    /**
     * Adjusts hypothetical future scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set so one dimension fills the view and the image is centered on the other dimension. Used to calculate what the target of an
     * animation should be.
     * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
     * @param sat The scale we want and the translation we're aiming for. The values are adjusted to be valid.
     */
    private void fitToBounds(boolean center, ScaleAndTranslate sat) {
        PointF vTranslate = sat.vTranslate;
        float scale = limitedScale(sat.scale);
        float scaleWidth = scale * sWidth;
        float scaleHeight = scale * sHeight;

        if (center) {
            vTranslate.x = Math.max(vTranslate.x, getWidth() - scaleWidth);
            vTranslate.y = Math.max(vTranslate.y, getHeight() - scaleHeight);
        } else {
            vTranslate.x = Math.max(vTranslate.x, -scaleWidth);
            vTranslate.y = Math.max(vTranslate.y, -scaleHeight);
        }

        // Asymmetric padding adjustments
        float xPaddingRatio = getPaddingLeft() > 0 || getPaddingRight() > 0 ? getPaddingLeft()/(float)(getPaddingLeft() + getPaddingRight()) : 0.5f;
        float yPaddingRatio = getPaddingTop() > 0 || getPaddingBottom() > 0 ? getPaddingTop()/(float)(getPaddingTop() + getPaddingBottom()) : 0.5f;

        float maxTx;
        float maxTy;
        if (center) {
            maxTx = Math.max(0, (getWidth() - scaleWidth) * xPaddingRatio);
            maxTy = Math.max(0, (getHeight() - scaleHeight) * yPaddingRatio);
        } else {
            maxTx = Math.max(0, getWidth());
            maxTy = Math.max(0, getHeight());
        }

        vTranslate.x = Math.min(vTranslate.x, maxTx);
        vTranslate.y = Math.min(vTranslate.y, maxTy);

        sat.scale = scale;
    }

    /**
     * Adjusts current scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set so one dimension fills the view and the image is centered on the other dimension.
     * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
     */
    private void fitToBounds(boolean center) {
        boolean init = false;
        if (vTranslate == null) {
            init = true;
            vTranslate = new PointF(0, 0);
        }
        if (satTemp == null) {
            satTemp = new ScaleAndTranslate(0, new PointF(0, 0));
        }
        satTemp.scale = scale;
        satTemp.vTranslate.set(vTranslate);
        fitToBounds(center, satTemp);
        scale = satTemp.scale;
        vTranslate.set(satTemp.vTranslate);
        if (init) {
            vTranslate.set(vTranslateForSCenter(sWidth /2, sHeight /2, scale));
        }
    }

    /**
     * Once source image and view dimensions are known, creates a map of sample size to tile grid.
     */
    private void initialiseTileMap() {
        debug("initialiseTileMap");
        this.tileMap = new LinkedHashMap<>();
        int sampleSize = fullImageSampleSize;
        int xTiles = 1;
        int yTiles = 1;
        while (true) {
            int sTileWidth = sWidth /xTiles;
            int sTileHeight = sHeight /yTiles;
            int subTileWidth = sTileWidth/sampleSize;
            int subTileHeight = sTileHeight/sampleSize;
            while (subTileWidth > getWidth() * 1.25 && sampleSize < fullImageSampleSize) {
                xTiles += 1;
                sTileWidth = sWidth /xTiles;
                subTileWidth = sTileWidth/sampleSize;
            }
            while (subTileHeight > getHeight() * 1.25 && sampleSize < fullImageSampleSize) {
                yTiles += 1;
                sTileHeight = sHeight /yTiles;
                subTileHeight = sTileHeight/sampleSize;
            }
            List<Tile> tileGrid = new ArrayList<>(xTiles * yTiles);
            for (int x = 0; x < xTiles; x++) {
                for (int y = 0; y < yTiles; y++) {
                    Tile tile = new Tile();
                    tile.sampleSize = sampleSize;
                    tile.visible = sampleSize == fullImageSampleSize;
                    tile.sRect = new Rect(
                        x * sTileWidth,
                        y * sTileHeight,
                        x == xTiles - 1 ? sWidth : (x + 1) * sTileWidth,
                        y == yTiles - 1 ? sHeight : (y + 1) * sTileHeight
                    );
                    tile.vRect = new Rect(0, 0, 0, 0);
                    tile.fileSRect = new Rect(tile.sRect);
                    tileGrid.add(tile);
                }
            }
            tileMap.put(sampleSize, tileGrid);
            if (sampleSize == 1) {
                break;
            } else {
                sampleSize /= 2;
            }
        }
    }

    /**
     * Async task used to get image details without blocking the UI thread.
     */
    @SuppressWarnings("deprecation") private static class TilesInitTask extends AsyncTask<Void, Void, int[]> {
        @SuppressLint("StaticFieldLeak") private final SubsamplingScaleImageView view;
        private final Uri source;
        private ImageRegionDecoder decoder;
        private Exception exception;

        TilesInitTask(SubsamplingScaleImageView view, Uri source) {
            this.view = view;
            this.source = source;
        }

        @Override
        protected int[] doInBackground(Void... params) {
            try {
                Context context = view.getContext();
                view.debug("TilesInitTask.doInBackground");
                decoder = new SkiaImageRegionDecoder();
                Point dimensions = decoder.init(context, source);
                int sWidth = dimensions.x;
                int sHeight = dimensions.y;
                return new int[] { sWidth, sHeight };

            } catch (Exception e) {
                Log.e(TAG, "Failed to initialise bitmap decoder", e);
                this.exception = e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(int[] xyo) {
            if (decoder != null && xyo != null && xyo.length == 2) {
                view.onTilesInited(decoder, xyo[0], xyo[1]);
            } else if (exception != null) {
                exception.printStackTrace();
            }
        }
    }

    /**
     * Called by worker task when decoder is ready and image size and EXIF orientation is known.
     */
    private synchronized void onTilesInited(ImageRegionDecoder decoder, int sWidth, int sHeight) {
        debug("onTilesInited sWidth=%d, sHeight=%d, sOrientation=%d", sWidth, sHeight, orientation);
        // If actual dimensions don't match the declared size, reset everything.
        if (this.sWidth > 0 && this.sHeight > 0 && (this.sWidth != sWidth || this.sHeight != sHeight)) {
            reset(false);
        }
        this.decoder = decoder;
        this.sWidth = sWidth;
        this.sHeight = sHeight;
        checkReady();
        if (!checkImageLoaded()) {
            initialiseBaseLayer();
        }
        invalidate();
        requestLayout();
    }

    /**
     * Async task used to load images without blocking the UI thread.
     */
    @SuppressWarnings("deprecation")
    private static class TileLoadTask extends AsyncTask<Void, Void, Bitmap> {
        @SuppressLint("StaticFieldLeak") private final SubsamplingScaleImageView view;
        private final ImageRegionDecoder decoder;
        private final Tile tile;
        private Exception exception;

        TileLoadTask(SubsamplingScaleImageView view, ImageRegionDecoder decoder, Tile tile) {
            this.view = view;
            this.decoder = decoder;
            this.tile = tile;
            tile.loading = true;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            try {
                if (decoder.isReady() && tile.visible) {
                    view.debug("TileLoadTask.doInBackground, tile.sRect=%s, tile.sampleSize=%d", tile.sRect, tile.sampleSize);
                    if (decoder.isReady()) {
                        // Update tile's file sRect according to rotation
                        view.fileSRect(tile.sRect, tile.fileSRect);
                        return decoder.decodeRegion(tile.fileSRect, tile.sampleSize);
                    } else {
                        tile.loading = false;
                    }
                } else if (tile != null) {
                    tile.loading = false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to decode tile", e);
                this.exception = e;
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "Failed to decode tile - OutOfMemoryError", e);
                this.exception = new RuntimeException(e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (view != null && tile != null) {
                if (bitmap != null) {
                    tile.bitmap = bitmap;
                    tile.loading = false;
                    view.onTileLoaded();
                } else if (exception != null) {
                    exception.printStackTrace();
                }
            }
        }
    }

    /**
     * Called by worker task when a tile has loaded. Redraws the view.
     */
    private synchronized void onTileLoaded() {
        debug("onTileLoaded");
        checkReady();
        checkImageLoaded();
        invalidate();
    }

    private void execute(AsyncTask<Void, Void, ?> asyncTask) {
        asyncTask.executeOnExecutor(executor);
    }

    private static class Tile {
        private Rect sRect;
        private int sampleSize;
        private Bitmap bitmap;
        private boolean loading;
        private boolean visible;

        // Volatile fields instantiated once then updated before use to reduce GC.
        private Rect vRect;
        private Rect fileSRect;

    }

    private static class ScaleAndTranslate {
        private ScaleAndTranslate(float scale, PointF vTranslate) {
            this.scale = scale;
            this.vTranslate = vTranslate;
        }
        private float scale;
        private final PointF vTranslate;
    }

    /**
     * Converts source rectangle from tile, which treats the image file as if it were in the correct orientation already,
     * to the rectangle of the image that needs to be loaded.
     */
    @AnyThread
    private void fileSRect(Rect sRect, Rect target) {
        target.set(sRect);
    }

    /**
     * Pythagoras distance between two points.
     */
    private float distance(float x0, float x1, float y0, float y1) {
        float x = x0 - x1;
        float y = y0 - y1;
        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * Convert screen to source x coordinate.
     */
    private float viewToSourceX(float vx) {
        if (vTranslate == null) { return Float.NaN; }
        return (vx - vTranslate.x)/scale;
    }

    /**
     * Convert screen to source y coordinate.
     */
    private float viewToSourceY(float vy) {
        if (vTranslate == null) { return Float.NaN; }
        return (vy - vTranslate.y)/scale;
    }

    /**
     * Convert screen coordinate to source coordinate.
     * @param vx view X coordinate.
     * @param vy view Y coordinate.
     * @return a coordinate representing the corresponding source coordinate.
     */
    @Nullable
    public final PointF viewToSourceCoord(float vx, float vy) {
        return viewToSourceCoord(vx, vy, new PointF());
    }

    /**
     * Convert screen coordinate to source coordinate.
     * @param vx view X coordinate.
     * @param vy view Y coordinate.
     * @param sTarget target object for result. The same instance is also returned.
     * @return source coordinates. This is the same instance passed to the sTarget param.
     */
    @Nullable
    public final PointF viewToSourceCoord(float vx, float vy, @NonNull PointF sTarget) {
        if (vTranslate == null) {
            return null;
        }
        sTarget.set(viewToSourceX(vx), viewToSourceY(vy));
        return sTarget;
    }

    /**
     * Convert source to view x coordinate.
     */
    private float sourceToViewX(float sx) {
        if (vTranslate == null) { return Float.NaN; }
        return (sx * scale) + vTranslate.x;
    }

    /**
     * Convert source to view y coordinate.
     */
    private float sourceToViewY(float sy) {
        if (vTranslate == null) { return Float.NaN; }
        return (sy * scale) + vTranslate.y;
    }


    /**
     * Convert source rect to screen rect, integer values.
     */
    private void sourceToViewRect(@NonNull Rect sRect, @NonNull Rect vTarget) {
        vTarget.set(
            (int)sourceToViewX(sRect.left),
            (int)sourceToViewY(sRect.top),
            (int)sourceToViewX(sRect.right),
            (int)sourceToViewY(sRect.bottom)
        );
    }

    /**
     * Get the translation required to place a given source coordinate at the center of the screen, with the center
     * adjusted for asymmetric padding. Accepts the desired scale as an argument, so this is independent of current
     * translate and scale. The result is fitted to bounds, putting the image point as near to the screen center as permitted.
     */
    @NonNull
    private PointF vTranslateForSCenter(float sCenterX, float sCenterY, float scale) {
        int vxCenter = getPaddingLeft() + (getWidth() - getPaddingRight() - getPaddingLeft())/2;
        int vyCenter = getPaddingTop() + (getHeight() - getPaddingBottom() - getPaddingTop())/2;
        if (satTemp == null) {
            satTemp = new ScaleAndTranslate(0, new PointF(0, 0));
        }
        satTemp.scale = scale;
        satTemp.vTranslate.set(vxCenter - (sCenterX * scale), vyCenter - (sCenterY * scale));
        fitToBounds(true, satTemp);
        return satTemp.vTranslate;
    }

    /**
     * Returns the minimum allowed scale.
     */
    private float minScale() {
        int vPadding = getPaddingBottom() + getPaddingTop();
        int hPadding = getPaddingLeft() + getPaddingRight();
        return Math.min((getWidth() - hPadding) / (float) sWidth, (getHeight() - vPadding) / (float) sHeight);
    }

    /**
     * Adjust a requested scale to be within the allowed limits.
     */
    private float limitedScale(float targetScale) {
        targetScale = Math.max(minScale(), targetScale);
        targetScale = Math.min(maxScale, targetScale);
        return targetScale;
    }

    /**
     * Debug logger
     */
    @AnyThread
    private void debug(String message, Object... args) {
        if (debug) {
            Log.d(TAG, String.format(message, args));
        }
    }

    /**
     * For debug overlays. Scale pixel value according to screen density.
     */
    private int px(int px) {
        return (int)(density * px);
    }

    /**
     * This is a screen density aware alternative to setMaxScale; it allows you to express the maximum
     * allowed scale in terms of the minimum pixel density. This avoids the problem of 1:1 scale still being
     * too small on a high density screen. A sensible starting point is 160 - the default used by this view.
     * @param dpi Source image pixel density at maximum zoom.
     */
    private void setMinimumDpi(int dpi) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi)/2;
        this.maxScale = averageDpi/dpi;
    }

    /**
     * By default, image tiles are at least as high resolution as the screen. For a retina screen this may not be
     * necessary, and may increase the likelihood of an OutOfMemoryError. This method sets a DPI at which higher
     * resolution tiles should be loaded. Using a lower number will on average use less memory but result in a lower
     * quality image. 160-240dpi will usually be enough. This should be called before setting the image source,
     * because it affects which tiles get loaded. When using an untiled source image this method has no effect.
     * @param minimumTileDpi Tile loading threshold.
     */
    public void setMinimumTileDpi(int minimumTileDpi) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi)/2;
        this.minimumTileDpi = (int)Math.min(averageDpi, minimumTileDpi);
        if (readySent) {
            reset(false);
            invalidate();
        }
    }

    /**
     * Returns the source point at the center of the view.
     * @return the source coordinates current at the center of the view.
     */
    @Nullable
    public final PointF getCenter() {
        int mX = getWidth()/2;
        int mY = getHeight()/2;
        return viewToSourceCoord(mX, mY);
    }
}
