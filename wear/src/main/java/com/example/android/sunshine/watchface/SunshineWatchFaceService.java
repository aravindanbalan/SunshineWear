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

package com.example.android.sunshine.watchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Sample digital watch face with blinking colons and seconds. In ambient mode, the seconds are
 * replaced with an AM/PM indicator and the colons don't blink. On devices with low-bit ambient
 * mode, the text is drawn without anti-aliasing in ambient mode. On devices which require burn-in
 * protection, the hours are drawn in normal rather than bold. The time is drawn with less contrast
 * and without seconds in mute mode.
 */
public class SunshineWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "WatchFaceService";

    private static final Typeface BOLD_TYPEFACE =
        Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface NORMAL_TYPEFACE =
        Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for normal (not ambient and not mute) mode. We update twice
     * a second to blink the colons.
     */
    private static final long NORMAL_UPDATE_RATE_MS = 500;
    private static final long TIMEOUT_MS = 500;

    /**
     * Update rate in milliseconds for mute mode. We update every minute, like in ambient mode.
     */
    private static final long MUTE_UPDATE_RATE_MS = TimeUnit.MINUTES.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        static final String COLON_STRING = ":";

        /**
         * Alpha value for drawing time when in mute mode.
         */
        static final int MUTE_ALPHA = 100;

        /**
         * Alpha value for drawing time when not in mute mode.
         */
        static final int NORMAL_ALPHA = 255;

        static final int MSG_UPDATE_TIME = 0;

        /**
         * How often {@link #mUpdateTimeHandler} ticks in milliseconds.
         */
        long mInteractiveUpdateRateMs = NORMAL_UPDATE_RATE_MS;

        /**
         * Handler to update the time periodically in interactive mode.
         */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs =
                                mInteractiveUpdateRateMs - (timeMs % mInteractiveUpdateRateMs);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        GoogleApiClient mGoogleApiClient;

        /**
         * Handles time zone and locale changes.
         */
        final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
                invalidate();
            }
        };

        /**
         * Unregistering an unregistered receiver throws an exception. Keep track of the
         * registration state to prevent that.
         */
        boolean mRegisteredReceiver = false;

        Paint mBackgroundPaint;
        Paint mImageBackgroundPaint;
        Paint mDatePaint;
        Paint mHourPaint;
        Paint mMinutePaint;
        Paint mSecondPaint;
        Paint mAmPmPaint;
        Paint mFormattedHighLowPaint;
        Paint mHorizontalLinePaint;
        Paint mColonPaint;
        float mColonWidth;
        int weatherId;
        Bitmap mWeatherIcon;
        Bitmap mGrayWeatherIcon;
        String mFormattedHighLow;
        boolean mMute;

        Calendar mCalendar;
        Date mDate;
        SimpleDateFormat mDayOfWeekFormat;

        boolean mShouldDrawColons;
        float mXOffset;
        float mXOffsetDate;
        float mXHorizontalLineOffset;
        float mYOffset;
        float mLineHeight;
        String mAmString;
        String mPmString;
        int mInteractiveBackgroundColor =
            SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND;
        int mInteractiveHourDigitsColor =
            SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS;
        int mInteractiveMinuteDigitsColor =
            SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS;
        int mInteractiveSecondDigitsColor =
            SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        boolean mBurnInProtection;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFaceService.this)
                .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                .setShowSystemUiTime(false)
                .build());
            Resources resources = SunshineWatchFaceService.this.getResources();
            mLineHeight = resources.getDimension(R.dimen.digital_line_height);
            mAmString = resources.getString(R.string.digital_am);
            mPmString = resources.getString(R.string.digital_pm);

            mBackgroundPaint = new Paint();
            mImageBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mInteractiveBackgroundColor);
            mDatePaint = createTextPaint(
                ContextCompat.getColor(getApplicationContext(), R.color.digital_date));
            mHourPaint = createTextPaint(mInteractiveHourDigitsColor, BOLD_TYPEFACE);
            mMinutePaint = createTextPaint(mInteractiveMinuteDigitsColor);
            mSecondPaint = createTextPaint(mInteractiveSecondDigitsColor);
            mAmPmPaint = createTextPaint(
                ContextCompat.getColor(getApplicationContext(), R.color.digital_am_pm));
            mColonPaint = createTextPaint(
                ContextCompat.getColor(getApplicationContext(), R.color.digital_colons));

            mFormattedHighLowPaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.low_color));

            mHorizontalLinePaint = new Paint();
            mHorizontalLinePaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.white_76));
            mCalendar = Calendar.getInstance();
            mDate = new Date();

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFaceService.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

            initFormats();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int defaultInteractiveColor) {
            return createTextPaint(defaultInteractiveColor, NORMAL_TYPEFACE);
        }

        private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();

                registerReceiver();

                // Update time zone and date formats, in case they changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                initFormats();
            } else {
                unregisterReceiver();

                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void initFormats() {
            mDayOfWeekFormat = new SimpleDateFormat("EE, MMM dd yyyy", Locale.getDefault());
            mDayOfWeekFormat.setCalendar(mCalendar);
        }

        private void registerReceiver() {
            if (mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            SunshineWatchFaceService.this.registerReceiver(mReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredReceiver) {
                return;
            }
            mRegisteredReceiver = false;
            SunshineWatchFaceService.this.unregisterReceiver(mReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();

            mYOffset = resources.getDimension(isRound
                ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
            mXHorizontalLineOffset =resources.getDimension(isRound
                ? R.dimen.digital_x_offset_horizontal_line_round : R.dimen.digital_x_offset_horizontal_line);
            mXOffsetDate = resources.getDimension(isRound
                ? R.dimen.digital_x_offset_date_round : R.dimen.digital_x_offset_date);

            mXOffset = resources.getDimension(isRound
                ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float amPmSize = resources.getDimension(isRound
                ? R.dimen.digital_am_pm_size_round : R.dimen.digital_am_pm_size);

            mDatePaint.setTextSize(resources.getDimension(R.dimen.digital_date_text_size));
            mHourPaint.setTextSize(textSize);
            mMinutePaint.setTextSize(textSize);
            mSecondPaint.setTextSize(textSize);
            mAmPmPaint.setTextSize(amPmSize);
            mColonPaint.setTextSize(textSize);
            mFormattedHighLowPaint.setTextSize(resources.getDimension(R.dimen.high_low_temp_text_size));
            mColonWidth = mColonPaint.measureText(COLON_STRING);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
            mHourPaint.setTypeface(mBurnInProtection ? NORMAL_TYPEFACE : BOLD_TYPEFACE);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);

            Log.d(TAG, "onPropertiesChanged: burn-in protection = " + mBurnInProtection
                + ", low-bit ambient = " + mLowBitAmbient);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);

            adjustPaintColorToCurrentMode(mHourPaint, mInteractiveHourDigitsColor,
                SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_HOUR_DIGITS);
            adjustPaintColorToCurrentMode(mMinutePaint, mInteractiveMinuteDigitsColor,
                SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_MINUTE_DIGITS);
            // Actually, the seconds are not rendered in the ambient mode, so we could pass just any
            // value as ambientColor here.
            adjustPaintColorToCurrentMode(mSecondPaint, mInteractiveSecondDigitsColor,
                SunshineWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_SECOND_DIGITS);

            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mDatePaint.setAntiAlias(antiAlias);
                mHourPaint.setAntiAlias(antiAlias);
                mMinutePaint.setAntiAlias(antiAlias);
                mSecondPaint.setAntiAlias(antiAlias);
                mAmPmPaint.setAntiAlias(antiAlias);
                mColonPaint.setAntiAlias(antiAlias);
                mFormattedHighLowPaint.setAntiAlias(antiAlias);
            }
            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        private void adjustPaintColorToCurrentMode(Paint paint, int interactiveColor,
                                                   int ambientColor) {
            paint.setColor(isInAmbientMode() ? ambientColor : interactiveColor);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            if (mWeatherIcon != null) {
                /* Scale loaded background image (more efficient) if surface dimensions change. */
                float scale = ((float) width) / (float) mWeatherIcon.getWidth();

                mWeatherIcon = Bitmap.createScaledBitmap(mWeatherIcon,
                    (int) (mWeatherIcon.getWidth() * scale),
                    (int) (mWeatherIcon.getHeight() * scale), true);

                if (!mBurnInProtection && !mLowBitAmbient) {
                    initGrayBackgroundBitmap();
                }
            }
        }

        private void initGrayBackgroundBitmap() {
            mGrayWeatherIcon = Bitmap.createBitmap(
                mWeatherIcon.getWidth(),
                mWeatherIcon.getHeight(),
                Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayWeatherIcon);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mWeatherIcon, 0, 0, grayPaint);
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);

            boolean inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE;
            // We only need to update once a minute in mute mode.
            setInteractiveUpdateRateMs(inMuteMode ? MUTE_UPDATE_RATE_MS : NORMAL_UPDATE_RATE_MS);

            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                int alpha = inMuteMode ? MUTE_ALPHA : NORMAL_ALPHA;
                mDatePaint.setAlpha(alpha);
                mHourPaint.setAlpha(alpha);
                mMinutePaint.setAlpha(alpha);
                mSecondPaint.setAlpha(alpha);
                mFormattedHighLowPaint.setAlpha(alpha);
                mColonPaint.setAlpha(alpha);
                mAmPmPaint.setAlpha(alpha);
                invalidate();
            }
        }

        public void setInteractiveUpdateRateMs(long updateRateMs) {
            if (updateRateMs == mInteractiveUpdateRateMs) {
                return;
            }
            mInteractiveUpdateRateMs = updateRateMs;

            // Stop and restart the timer so the new update rate takes effect immediately.
            if (shouldTimerBeRunning()) {
                updateTimer();
            }
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        private String getAmPmString(int amPm) {
            return amPm == Calendar.AM ? mAmString : mPmString;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            mDate.setTime(now);
            boolean is24Hour = DateFormat.is24HourFormat(SunshineWatchFaceService.this);

            // Show colons for the first half of each second so the colons blink on when the time
            // updates.
            mShouldDrawColons = (System.currentTimeMillis() % 1000) < 500;

            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                // Draw the background.
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw the hours.
            float x = mXOffset;
            String hourString;
            if (is24Hour) {
                hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            } else {
                int hour = mCalendar.get(Calendar.HOUR);
                if (hour == 0) {
                    hour = 12;
                }
                hourString = String.valueOf(hour);
            }
            canvas.drawText(hourString, x, mYOffset, mHourPaint);
            x += mHourPaint.measureText(hourString);

            // In ambient and mute modes, always draw the first colon. Otherwise, draw the
            // first colon for the first half of each second.
            if (isInAmbientMode() || mMute || mShouldDrawColons) {
                canvas.drawText(COLON_STRING, x, mYOffset, mColonPaint);
            }
            x += mColonWidth;

            // Draw the minutes.
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            canvas.drawText(minuteString, x, mYOffset, mMinutePaint);
            x += mMinutePaint.measureText(minuteString);

            // In unmuted interactive mode, draw a second blinking colon followed by the seconds.
            // Otherwise, if we're in 12-hour mode, draw AM/PM
            if (!isInAmbientMode() && !mMute) {
                if (mShouldDrawColons) {
                    canvas.drawText(COLON_STRING, x, mYOffset, mColonPaint);
                }
                x += mColonWidth;
                canvas.drawText(formatTwoDigitNumber(
                    mCalendar.get(Calendar.SECOND)), x, mYOffset, mSecondPaint);
            } else if (!is24Hour) {
                x += mColonWidth;
                canvas.drawText(getAmPmString(
                    mCalendar.get(Calendar.AM_PM)), x, mYOffset, mAmPmPaint);
            }


            float vertical_offset = mYOffset + mLineHeight;
            // Day of week
            canvas.drawText(
                mDayOfWeekFormat.format(mDate),
                mXOffsetDate, vertical_offset, mDatePaint);

            // Only render the day of week if there is no peek card.
            if (getPeekCardPosition().isEmpty()) {
                vertical_offset += mLineHeight;

                //draw line only when peekcard is not shown and not in mute or ambient mode
                if (!isInAmbientMode() && !mMute) {
                    canvas.drawLine(mXHorizontalLineOffset, vertical_offset, mXHorizontalLineOffset + 50, vertical_offset, mHorizontalLinePaint);
                }
                vertical_offset += mLineHeight;

                //Draw weather details
                x = mXOffset + 10;
                if (mWeatherIcon != null && !TextUtils.isEmpty(mFormattedHighLow)) {
                    Bitmap image;
                    if (isInAmbientMode()) {
                        initGrayBackgroundBitmap();
                        image = mGrayWeatherIcon;
                    } else {
                        image = mWeatherIcon;
                    }

                    canvas.drawText(
                        mFormattedHighLow,
                        x + image.getWidth() + 10, vertical_offset + 10, mFormattedHighLowPaint);
                    canvas.drawBitmap(image, x, vertical_offset - (image.getHeight() / 2), mImageBackgroundPaint);
                }
            }
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

        @Override // DataApi.DataListener
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }

                DataItem dataItem = dataEvent.getDataItem();
                if (!dataItem.getUri().getPath().equals(
                    SunshineWatchFaceUtil.PATH_WITH_FEATURE)) {
                    continue;
                }

                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap dataMap = dataMapItem.getDataMap();

                weatherId = dataMap.getInt(SunshineWatchFaceUtil.WEATHER_IMAGE_PATH);
                mWeatherIcon = BitmapFactory.decodeResource(getResources(), SunshineWatchFaceUtil.getSmallArtResourceIdForWeatherCondition(weatherId));
                mFormattedHighLow = dataMap.getString(SunshineWatchFaceUtil.WEATHER_FORMATTED_HIGH_LOW);

                invalidate();
            }
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            Log.d(TAG, "onConnected: " + connectionHint);
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            Log.d(TAG, "onConnectionSuspended: " + cause);
        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            Log.d(TAG, "onConnectionFailed: " + result);
        }

        public Bitmap loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            ConnectionResult result =
                mGoogleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!result.isSuccess()) {
                return null;
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();
            mGoogleApiClient.disconnect();

            if (assetInputStream == null) {
                Log.w(TAG, "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }
    }
}
