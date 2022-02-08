package com.eightbit.samsprung.panels;

import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import com.eightbit.samsprung.R;
import com.eightbit.samsprung.SamSprung;
import com.eightbit.samsprung.SamSprungPanels;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.Executors;

abstract class SoftReferenceThreadLocal<T> {
    private final ThreadLocal<SoftReference<T>> mThreadLocal;
    public SoftReferenceThreadLocal() {
        mThreadLocal = new ThreadLocal<>();
    }

    abstract T initialValue();

    public void set(T t) {
        mThreadLocal.set(new SoftReference<T>(t));
    }

    public T get() {
        SoftReference<T> reference = mThreadLocal.get();
        T obj;
        if (reference == null) {
            obj = initialValue();
            mThreadLocal.set(new SoftReference<T>(obj));
        } else {
            obj = reference.get();
            if (obj == null) {
                obj = initialValue();
                mThreadLocal.set(new SoftReference<T>(obj));
            }
        }
        return obj;
    }
}

class CanvasCache extends SoftReferenceThreadLocal<Canvas> {
    @Override
    protected Canvas initialValue() {
        return new Canvas();
    }
}

class PaintCache extends SoftReferenceThreadLocal<Paint> {
    @Override
    protected Paint initialValue() {
        return null;
    }
}

class BitmapCache extends SoftReferenceThreadLocal<Bitmap> {
    @Override
    protected Bitmap initialValue() {
        return null;
    }
}

class RectCache extends SoftReferenceThreadLocal<Rect> {
    @Override
    protected Rect initialValue() {
        return new Rect();
    }
}

class BitmapFactoryOptionsCache extends SoftReferenceThreadLocal<BitmapFactory.Options> {
    @Override
    protected BitmapFactory.Options initialValue() {
        return new BitmapFactory.Options();
    }
}

public class WidgetPreviewLoader {
    static final String TAG = "WidgetPreviewLoader";
    static final String ANDROID_INCREMENTAL_VERSION_NAME_KEY = "android.incremental.version";

    private int mPreviewBitmapWidth;
    private int mPreviewBitmapHeight;
    private String mSize;
    private final Context mContext;
    private final SamSprungPanels mLauncher;
    private final PackageManager mPackageManager;

    // Used for drawing shortcut previews
    private final BitmapCache mCachedShortcutPreviewBitmap = new BitmapCache();
    private final PaintCache mCachedShortcutPreviewPaint = new PaintCache();
    private final CanvasCache mCachedShortcutPreviewCanvas = new CanvasCache();

    // Used for drawing widget previews
    private final CanvasCache mCachedAppWidgetPreviewCanvas = new CanvasCache();
    private final RectCache mCachedAppWidgetPreviewSrcRect = new RectCache();
    private final RectCache mCachedAppWidgetPreviewDestRect = new RectCache();
    private final PaintCache mCachedAppWidgetPreviewPaint = new PaintCache();
    private String mCachedSelectQuery;
    private final BitmapFactoryOptionsCache mCachedBitmapFactoryOptions = new BitmapFactoryOptionsCache();

    private final int mAppIconSize;
    private final int mProfileBadgeSize;
    private final int mProfileBadgeMargin;

    private CacheDb mDb;

    private final HashMap<String, WeakReference<Bitmap>> mLoadedPreviews;
    private final ArrayList<SoftReference<Bitmap>> mUnusedBitmaps;
    private static final HashSet<String> sInvalidPackages;

    static {
        sInvalidPackages = new HashSet<>();
    }

    public WidgetPreviewLoader(SamSprungPanels launcher) {
        mContext = mLauncher = launcher;
        mPackageManager = mContext.getPackageManager();
        mAppIconSize = mContext.getResources().getDimensionPixelSize(R.dimen.app_icon_size);
        mProfileBadgeSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.profile_badge_size);
        mProfileBadgeMargin = mContext.getResources().getDimensionPixelSize(
                R.dimen.profile_badge_margin);
        SamSprung app = (SamSprung) launcher.getApplicationContext();
        mDb = app.getWidgetPreviewCacheDb();
        mLoadedPreviews = new HashMap<>();
        mUnusedBitmaps = new ArrayList<>();

        SharedPreferences sp = launcher.getSharedPreferences(
                SamSprung.prefsValue, Context.MODE_PRIVATE);
        final String lastVersionName = sp.getString(ANDROID_INCREMENTAL_VERSION_NAME_KEY, null);
        final String versionName = android.os.Build.VERSION.INCREMENTAL;
        if (!versionName.equals(lastVersionName)) {
            // clear all the previews whenever the system version changes, to ensure that previews
            // are up-to-date for any apps that might have been updated with the system
            clearDb();
            SharedPreferences.Editor editor = sp.edit();
            editor.putString(ANDROID_INCREMENTAL_VERSION_NAME_KEY, versionName);
            editor.apply();
        }
    }

    public void recreateDb() {
        SamSprung app = (SamSprung) mLauncher.getApplication();
        app.recreateWidgetPreviewDb();
        mDb = app.getWidgetPreviewCacheDb();
    }

    public void setPreviewSize(int previewWidth, int previewHeight) {
        mPreviewBitmapWidth = previewWidth;
        mPreviewBitmapHeight = previewHeight;
        mSize = previewWidth + "x" + previewHeight;
    }

    public Bitmap getPreview(final Object o) {
        String name = getObjectName(o);
        // check if the package is valid
        boolean packageValid = true;
        synchronized(sInvalidPackages) {
            packageValid = !sInvalidPackages.contains(getObjectPackage(o));
        }
        if (!packageValid) {
            return null;
        }
        synchronized(mLoadedPreviews) {
            // check if it exists in our existing cache
            if (mLoadedPreviews.containsKey(name) && mLoadedPreviews.get(name).get() != null) {
                return mLoadedPreviews.get(name).get();
            }
        }

        Bitmap unusedBitmap = null;
        synchronized(mUnusedBitmaps) {
            // not in cache; we need to load it from the db
            while ((unusedBitmap == null || !unusedBitmap.isMutable() ||
                    unusedBitmap.getWidth() != mPreviewBitmapWidth ||
                    unusedBitmap.getHeight() != mPreviewBitmapHeight)
                    && mUnusedBitmaps.size() > 0) {
                unusedBitmap = mUnusedBitmaps.remove(0).get();
            }
            if (unusedBitmap != null) {
                final Canvas c = mCachedAppWidgetPreviewCanvas.get();
                c.setBitmap(unusedBitmap);
                c.drawColor(0, PorterDuff.Mode.CLEAR);
                c.setBitmap(null);
            }
        }

        if (unusedBitmap == null) {
            unusedBitmap = Bitmap.createBitmap(mPreviewBitmapWidth, mPreviewBitmapHeight,
                    Bitmap.Config.ARGB_8888);
        }

        Bitmap preview = null;

        preview = readFromDb(name, unusedBitmap);

        if (preview != null) {
            synchronized(mLoadedPreviews) {
                mLoadedPreviews.put(name, new WeakReference<Bitmap>(preview));
            }
        } else {
            // it's not in the db... we need to generate it
            final Bitmap generatedPreview = generatePreview(o, unusedBitmap);
            preview = generatedPreview;
            if (preview != unusedBitmap) {
                throw new RuntimeException("generatePreview is not recycling the bitmap " + o);
            }

            synchronized(mLoadedPreviews) {
                mLoadedPreviews.put(name, new WeakReference<>(preview));
            }

            // write to db on a thread pool... this can be done lazily and improves the performance
            // of the first time widget previews are loaded
            Executors.newSingleThreadExecutor().execute(() ->
                    writeToDb(o, generatedPreview));
        }
        return preview;
    }

    public void recycleBitmap(Object o, Bitmap bitmapToRecycle) {
        String name = getObjectName(o);
        synchronized (mLoadedPreviews) {
            if (mLoadedPreviews.containsKey(name)) {
                Bitmap b = Objects.requireNonNull(mLoadedPreviews.get(name)).get();
                if (b == bitmapToRecycle) {
                    mLoadedPreviews.remove(name);
                    if (bitmapToRecycle.isMutable()) {
                        synchronized (mUnusedBitmaps) {
                            mUnusedBitmaps.add(new SoftReference<>(b));
                        }
                    }
                } else {
                    throw new RuntimeException("Bitmap passed in doesn't match up");
                }
            }
        }
    }

    public static class CacheDb extends SQLiteOpenHelper {
        final static int DB_VERSION = 2;
        final static String DB_NAME = "widgetpreviews.db";
        final static String TABLE_NAME = "shortcut_and_widget_previews";
        final static String COLUMN_NAME = "name";
        final static String COLUMN_SIZE = "size";
        final static String COLUMN_PREVIEW_BITMAP = "preview_bitmap";
        Context mContext;

        public CacheDb(Context context) {
            super(context, new File(context.getCacheDir(), DB_NAME).getPath(), null, DB_VERSION);
            // Store the context for later use
            mContext = context;
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    COLUMN_NAME + " TEXT NOT NULL, " +
                    COLUMN_SIZE + " TEXT NOT NULL, " +
                    COLUMN_PREVIEW_BITMAP + " BLOB NOT NULL, " +
                    "PRIMARY KEY (" + COLUMN_NAME + ", " + COLUMN_SIZE + ") " +
                    ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion != newVersion) {
                // Delete all the records; they'll be repopulated as this is a cache
                db.execSQL("DELETE FROM " + TABLE_NAME);
            }
        }
    }

    private static final String WIDGET_PREFIX = "Widget:";
    private static final String SHORTCUT_PREFIX = "Shortcut:";

    private static String getObjectName(Object o) {
        // should cache the string builder
        StringBuilder sb = new StringBuilder();
        String output;
        if (o instanceof AppWidgetProviderInfo) {
            AppWidgetProviderInfo info = (AppWidgetProviderInfo) o;
            sb.append(WIDGET_PREFIX);
            sb.append(info.getProfile());
            sb.append('/');
            sb.append(info.provider.flattenToString());
        } else {
            sb.append(SHORTCUT_PREFIX);

            ResolveInfo info = (ResolveInfo) o;
            sb.append(new ComponentName(info.activityInfo.packageName,
                    info.activityInfo.name).flattenToString());
        }
        output = sb.toString();
        sb.setLength(0);
        return output;
    }

    private String getObjectPackage(Object o) {
        if (o instanceof AppWidgetProviderInfo) {
            return ((AppWidgetProviderInfo) o).provider.getPackageName();
        } else {
            ResolveInfo info = (ResolveInfo) o;
            return info.activityInfo.packageName;
        }
    }

    private void writeToDb(Object o, Bitmap preview) {
        String name = getObjectName(o);
        SQLiteDatabase db = mDb.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(CacheDb.COLUMN_NAME, name);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        preview.compress(Bitmap.CompressFormat.PNG, 100, stream);
        values.put(CacheDb.COLUMN_PREVIEW_BITMAP, stream.toByteArray());
        values.put(CacheDb.COLUMN_SIZE, mSize);
        try {
            db.insert(CacheDb.TABLE_NAME, null, values);
        } catch (SQLiteDiskIOException e) {
            recreateDb();
        }
    }

    private void clearDb() {
        SQLiteDatabase db = mDb.getWritableDatabase();
        // Delete everything
        try {
            db.delete(CacheDb.TABLE_NAME, null, null);
        } catch (SQLiteDiskIOException ignored) { }
    }

    public static void removeFromDb(final CacheDb cacheDb, final String packageName) {
        synchronized(sInvalidPackages) {
            sInvalidPackages.add(packageName);
        }
        Executors.newSingleThreadExecutor().execute(() -> {
            SQLiteDatabase db = cacheDb.getWritableDatabase();
            try {
                db.delete(CacheDb.TABLE_NAME,
                        CacheDb.COLUMN_NAME + " LIKE ? OR " +
                                CacheDb.COLUMN_NAME + " LIKE ?", // SELECT query
                        new String[] {
                                WIDGET_PREFIX + packageName + "/%",
                                SHORTCUT_PREFIX + packageName + "/%"} // args to SELECT query
                );
                synchronized(sInvalidPackages) {
                    sInvalidPackages.remove(packageName);
                }
            } catch (SQLiteDiskIOException ignored) { }
        });
    }

    private Bitmap readFromDb(String name, Bitmap b) {
        if (mCachedSelectQuery == null) {
            mCachedSelectQuery = CacheDb.COLUMN_NAME + " = ? AND " +
                    CacheDb.COLUMN_SIZE + " = ?";
        }
        SQLiteDatabase db = mDb.getReadableDatabase();
        Cursor result;
        try {
            result = db.query(CacheDb.TABLE_NAME,
                    new String[] { CacheDb.COLUMN_PREVIEW_BITMAP }, // cols to return
                    mCachedSelectQuery, // select query
                    new String[] { name, mSize }, // args to select query
                    null,
                    null,
                    null,
                    null);
        } catch (SQLiteDiskIOException e) {
            recreateDb();
            return null;
        }
        if (result.getCount() > 0) {
            result.moveToFirst();
            byte[] blob = result.getBlob(0);
            result.close();
            final BitmapFactory.Options opts = mCachedBitmapFactoryOptions.get();
            opts.inBitmap = b;
            opts.inSampleSize = 1;
            return BitmapFactory.decodeByteArray(blob, 0, blob.length, opts);
        } else {
            result.close();
            return null;
        }
    }

    public Bitmap generatePreview(Object info, Bitmap preview) {
        if (preview != null &&
                (preview.getWidth() != mPreviewBitmapWidth ||
                preview.getHeight() != mPreviewBitmapHeight)) {
            throw new RuntimeException("Improperly sized bitmap passed as argument");
        }
        if (info instanceof AppWidgetProviderInfo) {
            return generateWidgetPreview((AppWidgetProviderInfo) info, preview);
        } else {
            return generateShortcutPreview(
                    (ResolveInfo) info, mPreviewBitmapWidth, mPreviewBitmapHeight, preview);
        }
    }

    public Bitmap generateWidgetPreview(AppWidgetProviderInfo info, Bitmap preview) {
        int[] cellSpans = mLauncher.getWidgetMaxSize(info);
        int maxWidth = maxWidthForWidgetPreview(cellSpans[0]);
        int maxHeight = maxHeightForWidgetPreview(cellSpans[1]);
        return generateWidgetPreview(info, maxWidth, maxHeight, preview, null);
    }

    public int maxWidthForWidgetPreview(int spanX) {
        return Math.min(mPreviewBitmapWidth, spanX);
    }

    public int maxHeightForWidgetPreview(int spanY) {
        return Math.min(mPreviewBitmapHeight, spanY);
    }

    public Bitmap generateWidgetPreview(AppWidgetProviderInfo info,
            int maxPreviewWidth, int maxPreviewHeight, Bitmap preview,
            int[] preScaledWidthOut) {
        // Load the preview image if possible
        if (maxPreviewWidth < 0) maxPreviewWidth = Integer.MAX_VALUE;
        if (maxPreviewHeight < 0) maxPreviewHeight = Integer.MAX_VALUE;

        Drawable drawable = info.loadPreviewImage(mContext, 0);

        int previewWidth = 0;
        int previewHeight = 0;
        Bitmap defaultPreview = null;
        boolean widgetPreviewExists = (drawable != null);
        if (widgetPreviewExists) {
            previewWidth = drawable.getIntrinsicWidth();
            previewHeight = drawable.getIntrinsicHeight();
        } else {
            // Generate a preview image if we couldn't load one
            BitmapDrawable previewDrawable = (BitmapDrawable) ResourcesCompat.getDrawable(
                    mContext.getResources(), R.drawable.widget_preview_tile, mContext.getTheme());
            if (null != previewDrawable) {
                final int previewDrawableWidth = previewDrawable.getIntrinsicWidth();
                final int previewDrawableHeight = previewDrawable.getIntrinsicHeight();
                previewWidth = previewDrawableWidth; // subtract 2 dips
                previewHeight = previewDrawableHeight;

                defaultPreview = Bitmap.createBitmap(previewWidth, previewHeight,
                        Config.ARGB_8888);
                final Canvas c = mCachedAppWidgetPreviewCanvas.get();
                c.setBitmap(defaultPreview);
                previewDrawable.setBounds(0, 0, previewWidth, previewHeight);
                previewDrawable.setTileModeXY(Shader.TileMode.REPEAT,
                        Shader.TileMode.REPEAT);
                previewDrawable.draw(c);
                c.setBitmap(null);

                // Draw the icon in the top left corner
                float sWidgetPreviewIconPaddingPercentage = 0.25f;
                int minOffset = (int) (mAppIconSize * sWidgetPreviewIconPaddingPercentage);
                int smallestSide = Math.min(previewWidth, previewHeight);
                float iconScale = Math.min((float) smallestSide
                        / (mAppIconSize + 2 * minOffset), 1f);

                try {
                    Drawable icon = null;
                    int hoffset =
                            (int) ((previewDrawableWidth - mAppIconSize * iconScale) / 2);
                    int yoffset =
                            (int) ((previewDrawableHeight - mAppIconSize * iconScale) / 2);
                    if (info.icon > 0)
                        icon = getFullResIcon(info.provider.getPackageName(),
                                info.icon, info.getProfile());
                    if (icon != null) {
                        renderDrawableToBitmap(icon, defaultPreview, hoffset,
                                yoffset, (int) (mAppIconSize * iconScale),
                                (int) (mAppIconSize * iconScale));
                    }
                } catch (Resources.NotFoundException ignored) { }
            }
        }

        // Scale to fit width only - let the widget preview be clipped in the
        // vertical dimension
        float scale = 1f;
        if (preScaledWidthOut != null) {
            preScaledWidthOut[0] = previewWidth;
        }
        if (previewWidth > maxPreviewWidth) {
            scale = maxPreviewWidth / (float) previewWidth;
        }
        if (scale != 1f) {
            previewWidth = (int) (scale * previewWidth);
            previewHeight = (int) (scale * previewHeight);
        }

        // If a bitmap is passed in, we use it; otherwise, we create a bitmap of the right size
        if (preview == null) {
            preview = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        }

        // Draw the scaled preview into the final bitmap
        int x = (preview.getWidth() - previewWidth) / 2;
        if (widgetPreviewExists) {
            renderDrawableToBitmap(drawable, preview, x, 0, previewWidth,
                    previewHeight);
        } else {
            final Canvas c = mCachedAppWidgetPreviewCanvas.get();
            final Rect src = mCachedAppWidgetPreviewSrcRect.get();
            final Rect dest = mCachedAppWidgetPreviewDestRect.get();
            c.setBitmap(preview);
            if (null != defaultPreview)
                src.set(0, 0, defaultPreview.getWidth(), defaultPreview.getHeight());
            dest.set(x, 0, x + previewWidth, previewHeight);

            Paint p = mCachedAppWidgetPreviewPaint.get();
            if (p == null) {
                p = new Paint();
                p.setFilterBitmap(true);
                mCachedAppWidgetPreviewPaint.set(p);
            }
            c.drawBitmap(defaultPreview, src, dest, p);
            c.setBitmap(null);
        }

        // Finally, if the preview is for a managed profile, badge it.
        if (!info.getProfile().equals(android.os.Process.myUserHandle())) {
            final int previewBitmapWidth = preview.getWidth();
            final int previewBitmapHeight = preview.getHeight();

            // Figure out the badge location.
            final Rect badgeLocation;
            Configuration configuration = mContext.getResources().getConfiguration();
            if (configuration.getLayoutDirection() == View.LAYOUT_DIRECTION_LTR) {
                final int badgeLeft = previewBitmapWidth - mProfileBadgeSize - mProfileBadgeMargin;
                final int badgeTop = previewBitmapHeight - mProfileBadgeSize - mProfileBadgeMargin;
                final int badgeRight = badgeLeft + mProfileBadgeSize;
                final int badgeBottom = badgeTop + mProfileBadgeSize;
                badgeLocation = new Rect(badgeLeft, badgeTop, badgeRight, badgeBottom);
            } else {
                final int badgeLeft = mProfileBadgeMargin;
                final int badgeTop = previewBitmapHeight - mProfileBadgeSize - mProfileBadgeMargin;
                final int badgeRight = badgeLeft + mProfileBadgeSize;
                final int badgeBottom = badgeTop + mProfileBadgeSize;
                badgeLocation = new Rect(badgeLeft, badgeTop, badgeRight, badgeBottom);
            }

            // Badge the preview.
            BitmapDrawable previewDrawable = new BitmapDrawable(
                    mContext.getResources(), preview);
            Drawable badgedPreviewDrawable = mContext.getPackageManager().getUserBadgedDrawableForDensity(
                    previewDrawable, info.getProfile(), badgeLocation, 0);

            // Return the badged bitmap.
            if (badgedPreviewDrawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) badgedPreviewDrawable;
                return bitmapDrawable.getBitmap();
            }
        }

        return preview;
    }

    public Drawable getFullResDefaultActivityIcon() {
        return getFullResIcon(Resources.getSystem(),
                android.R.mipmap.sym_def_app_icon, android.os.Process.myUserHandle());
    }

    public Drawable getFullResIcon(Resources resources, int iconId, UserHandle user) {
        Drawable d;
        try {
            d = ResourcesCompat.getDrawableForDensity(resources, iconId, 160, resources.newTheme());
        } catch (Resources.NotFoundException e) {
            d = null;
        }

        if (d == null) {
            d = getFullResDefaultActivityIcon();
        }
        return mPackageManager.getUserBadgedIcon(d, user);
    }

    public Drawable getFullResIcon(String packageName, int iconId, UserHandle user) {
        Resources resources;
        try {
            // TODO: Check if this needs to use the user param if we support
            // shortcuts/widgets from other profiles. It won't work as is
            // for packages that are only available in a different user profile.
            resources = mPackageManager.getResourcesForApplication(packageName);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            if (iconId != 0) {
                return getFullResIcon(resources, iconId, user);
            }
        }
        return getFullResDefaultActivityIcon();
    }

    public Drawable getFullResIcon(ResolveInfo info, UserHandle user) {
        return getFullResIcon(info.activityInfo, user);
    }

    public Drawable getFullResIcon(ActivityInfo info, UserHandle user) {
        Resources resources;
        try {
            resources = mPackageManager.getResourcesForApplication(
                    info.applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            resources = null;
        }
        if (resources != null) {
            int iconId = info.getIconResource();
            if (iconId != 0) {
                return getFullResIcon(resources, iconId, user);
            }
        }
        return getFullResDefaultActivityIcon();
    }

    private Bitmap generateShortcutPreview(
            ResolveInfo info, int maxWidth, int maxHeight, Bitmap preview) {
        Bitmap tempBitmap = mCachedShortcutPreviewBitmap.get();
        final Canvas c = mCachedShortcutPreviewCanvas.get();
        if (tempBitmap == null ||
                tempBitmap.getWidth() != maxWidth ||
                tempBitmap.getHeight() != maxHeight) {
            tempBitmap = Bitmap.createBitmap(maxWidth, maxHeight, Config.ARGB_8888);
            mCachedShortcutPreviewBitmap.set(tempBitmap);
        } else {
            c.setBitmap(tempBitmap);
            c.drawColor(0, PorterDuff.Mode.CLEAR);
            c.setBitmap(null);
        }
        // Render the icon
        Drawable icon = getFullResIcon(info, android.os.Process.myUserHandle());

        renderDrawableToBitmap(icon, tempBitmap, 0, 0, (maxWidth), (maxWidth));

        if (preview != null &&
                (preview.getWidth() != maxWidth || preview.getHeight() != maxHeight)) {
            throw new RuntimeException("Improperly sized bitmap passed as argument");
        } else if (preview == null) {
            preview = Bitmap.createBitmap(maxWidth, maxHeight, Config.ARGB_8888);
        }

        c.setBitmap(preview);
        // Draw a desaturated/scaled version of the icon in the background as a watermark
        Paint p = mCachedShortcutPreviewPaint.get();
        if (p == null) {
            p = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            p.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            p.setAlpha((int) (255 * 0.06f));
            mCachedShortcutPreviewPaint.set(p);
        }
        c.drawBitmap(tempBitmap, 0, 0, p);
        c.setBitmap(null);

        renderDrawableToBitmap(icon, preview, 0, 0, mAppIconSize, mAppIconSize);

        return preview;
    }


    public static void renderDrawableToBitmap(
            Drawable d, Bitmap bitmap, int x, int y, int w, int h) {
        renderDrawableToBitmap(d, bitmap, x, y, w, h, 1f);
    }

    private static void renderDrawableToBitmap(
            Drawable d, Bitmap bitmap, int x, int y, int w, int h, float scale) {
        if (bitmap != null) {
            Canvas c = new Canvas(bitmap);
            c.scale(scale, scale);
            Rect oldBounds = d.copyBounds();
            d.setBounds(x, y, x + w, y + h);
            d.draw(c);
            d.setBounds(oldBounds); // Restore the bounds
            c.setBitmap(null);
        }
    }
}
