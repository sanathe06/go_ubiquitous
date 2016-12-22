/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.example.android.sunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunShineWatchFace extends CanvasWatchFaceService {
    public static final String TAG = SunShineWatchFace.class.getSimpleName();
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    private static final String PATH_WEATHER = "/weather";
    private static final String KEY_WEATHER_ID = "key_weather_id";
    private static final String KEY_MAX_TEMP = "key_max_temp";
    private static final String KEY_MIN_TEMP = "key_min_temp";
    private String mHighTemp;
    private String mLowTemp;
    private int mWeatherId;

    private SimpleDateFormat mSdfDate = new SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault());
    private SimpleDateFormat mSdfTime = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private GoogleApiClient mGoogleApiClient;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunShineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunShineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunShineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, DataApi.DataListener {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;

        private Paint mBackgroundPaint;
        private Paint mTextPaintTime;
        private Paint mTextPaintDate;
        private Paint mTextPaintHighTemp;
        private Paint mTextPaintLowTemp;

        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mYOffsetTime;
        float mYOffsetDate;
        float mYOffsetDivider;
        float mDividerHalfWidth;
        float mYOffsetTemp;


        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunShineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunShineWatchFace.this.getResources();

            mGoogleApiClient = new GoogleApiClient.Builder(SunShineWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.color_primary));

            mTextPaintTime = createTextPaint(resources.getColor(R.color.digital_text),
                    resources.getDimension(R.dimen.text_size_time));

            mTextPaintDate = createTextPaint(resources.getColor(R.color.color_primary_light),
                    resources.getDimension(R.dimen.text_size_date));

            mTextPaintHighTemp = createTextPaint(resources.getColor(R.color.digital_text),
                    resources.getDimension(R.dimen.text_size_temp));
            mTextPaintLowTemp = createTextPaint(resources.getColor(R.color.color_primary_light),
                    resources.getDimension(R.dimen.text_size_temp));

            //offsets
            mYOffsetTime = resources.getDimension(R.dimen.time_y_offset);
            mYOffsetDate = resources.getDimension(R.dimen.date_y_offset);
            mYOffsetDivider = resources.getDimension(R.dimen.divider_y_offset);
            mYOffsetTemp = resources.getDimension(R.dimen.temp_y_offset);

            mDividerHalfWidth = resources.getDimension(R.dimen.divider_half_width);

            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
                mTextPaintDate.setColor(getResources().getColor(R.color.digital_text));
                mTextPaintLowTemp.setColor(getResources().getColor(R.color.digital_text));
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                mTextPaintDate.setColor(getResources().getColor(R.color.color_primary_light));
                mTextPaintLowTemp.setColor(getResources().getColor(R.color.color_primary_light));
            }

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            Date dateTime = mCalendar.getTime();
            String time = mSdfTime.format(dateTime);
            String date = mSdfDate.format(dateTime);
            //draw time
            canvas.drawText(time,
                    bounds.centerX() - (mTextPaintTime.measureText(time) / 2),
                    mYOffsetTime,
                    mTextPaintTime);
            //draw date
            canvas.drawText(date.toUpperCase(Locale.getDefault()),
                    bounds.centerX() - (mTextPaintDate.measureText(date) / 2),
                    mYOffsetDate,
                    mTextPaintDate);

            if (!TextUtils.isEmpty(mHighTemp) && !TextUtils.isEmpty(mLowTemp)) {
                //draw divider line
                canvas.drawLine(bounds.centerX() - mDividerHalfWidth,
                        mYOffsetDivider,
                        bounds.centerX() + mDividerHalfWidth,
                        mYOffsetDivider,
                        mTextPaintDate);

                //measure text sizes
                float highTempTextSize = mTextPaintHighTemp.measureText(mHighTemp);
                float lowTempTextSize = mTextPaintLowTemp.measureText(mLowTemp);
                if (isInAmbientMode()) {
                    //draw high temp
                    canvas.drawText(mHighTemp,
                            bounds.centerX() - ((highTempTextSize + lowTempTextSize) / 2) - 5,
                            mYOffsetTemp, mTextPaintHighTemp);
                    //draw low temp
                    canvas.drawText(mLowTemp, bounds.centerX() + 5, mYOffsetTemp, mTextPaintLowTemp);
                } else {
                    Drawable b = getResources().getDrawable(
                            Utils.getSmallArtResourceIdForWeatherCondition(mWeatherId));
                    Bitmap icon = ((BitmapDrawable) b).getBitmap();
                    float scaledWidth = (mTextPaintHighTemp.getTextSize() / icon.getHeight()) * icon.getWidth();
                    Bitmap weatherIcon = Bitmap.createScaledBitmap(icon,
                            (int) scaledWidth,
                            (int) mTextPaintHighTemp.getTextSize(), true);

                    //draw bitmap
                    canvas.drawBitmap(weatherIcon,
                            bounds.centerX() - weatherIcon.getWidth() / 2,
                            mYOffsetTemp - weatherIcon.getHeight(),
                            null);
                    //draw high temp
                    canvas.drawText(mHighTemp,
                            bounds.centerX() - highTempTextSize - weatherIcon.getWidth() / 2,
                            mYOffsetTemp,
                            mTextPaintHighTemp);
                    //draw low temp
                    canvas.drawText(mLowTemp,
                            bounds.centerX() + weatherIcon.getWidth() / 2,
                            mYOffsetTemp,
                            mTextPaintLowTemp);
                }

            }
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, float textSize) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setTextSize(textSize);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunShineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunShineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

           /* // Load resources that have alternate values for round watches.
            Resources resources = SunShineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffsetTime = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaintTime.setTextSize(textSize);*/
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaintTime.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(TAG, "Connected: bundle = [" + bundle + "]");
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(TAG, "Connection Suspended");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(TAG, "Connection Failed: connectionResult = [" + connectionResult.getErrorMessage() + "]");
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem dataItem = dataEvent.getDataItem();
                    if (dataItem.getUri().getPath().compareTo(PATH_WEATHER) == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(dataItem).getDataMap();
                        mHighTemp = dataMap.getString(KEY_MAX_TEMP);
                        mLowTemp = dataMap.getString(KEY_MIN_TEMP);
                        mWeatherId = dataMap.getInt(KEY_WEATHER_ID);
                        Log.d(TAG, "onDataChanged() returned: [ High Temp " + mHighTemp
                                + ", Low Temp " + mLowTemp + ", Weather Id " + mWeatherId + " ] ");
                        invalidate();
                    }
                }
            }
        }
    }
}
