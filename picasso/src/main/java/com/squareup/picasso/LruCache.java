/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.picasso;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import java.util.LinkedHashMap;
import java.util.Map;

import static android.content.Context.ACTIVITY_SERVICE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.HONEYCOMB_MR1;

public class LruCache implements Cache {
  final LinkedHashMap<String, Bitmap> map;
  private final int maxSize;

  private int size;
  private int putCount;
  private int evictionCount;
  private int hitCount;
  private int missCount;

  public LruCache(Context context) {
    this(calculateMaxSize(context));
  }

  public LruCache(int maxSize) {
    if (maxSize <= 0) {
      throw new IllegalArgumentException("Max size must be positive.");
    }
    this.maxSize = maxSize;
    this.map = new LinkedHashMap<String, Bitmap>(0, 0.75f, true);
  }

  @Override public Bitmap get(String key) {
    if (key == null) {
      throw new NullPointerException("key == null");
    }

    Bitmap mapValue;
    synchronized (this) {
      mapValue = map.get(key);
      if (mapValue != null) {
        hitCount++;
        return mapValue;
      }
      missCount++;
    }

    return null;
  }

  @Override public void set(String key, Bitmap bitmap) {
    if (key == null || bitmap == null) {
      throw new NullPointerException("key == null || bitmap == null");
    }

    Bitmap previous;
    synchronized (this) {
      putCount++;
      size += safeSizeOf(bitmap);
      previous = map.put(key, bitmap);
      if (previous != null) {
        size -= safeSizeOf(previous);
      }
    }

    trimToSize(maxSize);
  }

  private void trimToSize(int maxSize) {
    while (true) {
      String key;
      Bitmap value;
      synchronized (this) {
        if (size < 0 || (map.isEmpty() && size != 0)) {
          throw new IllegalStateException(
              getClass().getName() + ".sizeOf() is reporting inconsistent results!");
        }

        if (size <= maxSize || map.isEmpty()) {
          break;
        }

        Map.Entry<String, Bitmap> toEvict = map.entrySet().iterator().next();
        key = toEvict.getKey();
        value = toEvict.getValue();
        map.remove(key);
        size -= safeSizeOf(value);
        evictionCount++;
      }
    }
  }

  private int safeSizeOf(Bitmap value) {
    int result;
    if (SDK_INT >= HONEYCOMB_MR1) {
      result = BitmapHoneycombMR1.getByteCount(value);
    } else {
      result = value.getRowBytes() * value.getHeight();
    }
    if (result < 0) {
      throw new IllegalStateException("Negative size: " + value);
    }
    return result;
  }

  /** Clear the cache. */
  public final void evictAll() {
    trimToSize(-1); // -1 will evict 0-sized elements
  }

  /** Returns the sum of the sizes of the entries in this cache. */
  public synchronized final int size() {
    return size;
  }

  /** Returns the maximum sum of the sizes of the entries in this cache. */
  public synchronized final int maxSize() {
    return maxSize;
  }

  /** Returns the number of times {@link #get} returned a value. */
  public synchronized final int hitCount() {
    return hitCount;
  }

  /** Returns the number of times {@link #get} returned {@code null}. */
  public synchronized final int missCount() {
    return missCount;
  }

  /** Returns the number of times {@link #set(String, android.graphics.Bitmap)} was called. */
  public synchronized final int putCount() {
    return putCount;
  }

  /** Returns the number of values that have been evicted. */
  public synchronized final int evictionCount() {
    return evictionCount;
  }

  private static int calculateMaxSize(Context context) {
    ActivityManager am = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
    return 1024 * 1024 * am.getMemoryClass() / 6;
  }

  private static class BitmapHoneycombMR1 {
    static int getByteCount(Bitmap bitmap) {
      return bitmap.getByteCount();
    }
  }
}