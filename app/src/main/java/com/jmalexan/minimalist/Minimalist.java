package com.jmalexan.minimalist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;


import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static android.support.wearable.watchface.WatchFaceStyle.PROTECT_STATUS_BAR;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 * <p>
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
public class Minimalist extends CanvasWatchFaceService {

    /*
     * Updates rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<Minimalist.Engine> mWeakReference;

        public EngineHandler(Minimalist.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            Minimalist.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;
        private float mCenterX;
        private float mCenterY;
        private Paint mShapePaint;
        private Paint mWhiteTickPaint;
        private Paint mBlackTickPaint;
        private boolean mAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(Minimalist.this)
                    .setViewProtectionMode(PROTECT_STATUS_BAR)
                    .build());

            mCalendar = Calendar.getInstance();

            initializeWatchFace();
        }

        private void initializeWatchFace() {
            mShapePaint = new Paint();
            mShapePaint.setColor(Color.WHITE);
            mShapePaint.setStyle(Paint.Style.FILL_AND_STROKE);
            mShapePaint.setAntiAlias(true);

            mWhiteTickPaint = new Paint();
            mWhiteTickPaint.setColor(Color.WHITE);
            mWhiteTickPaint.setStrokeWidth(2f);
            mWhiteTickPaint.setAntiAlias(true);
            mWhiteTickPaint.setStyle(Paint.Style.STROKE);

            mBlackTickPaint = new Paint();
            mBlackTickPaint.setColor(Color.BLACK);
            mBlackTickPaint.setStrokeWidth(2f);
            mBlackTickPaint.setAntiAlias(true);
            mBlackTickPaint.setStyle(Paint.Style.STROKE);
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

            if (mAmbient) {
                mShapePaint.setAntiAlias(false);
                mWhiteTickPaint.setAntiAlias(false);
                mBlackTickPaint.setAntiAlias(false);
            } else {
                mShapePaint.setAntiAlias(true);
                mWhiteTickPaint.setAntiAlias(false);
                mBlackTickPaint.setAntiAlias(true);
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            drawBackground(canvas);
            drawWatchFace(canvas);
        }

        private void drawBackground(Canvas canvas) {
            canvas.drawColor(Color.BLACK);
        }

        private void drawWatchFace(Canvas canvas) {
            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */

            float innerTickRadius = mCenterX - 10;
            float outerTickRadius = mCenterX;
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mWhiteTickPaint);
            }

            final float minuteHandOffset = mCalendar.get(Calendar.SECOND) / 10f;
            final float minutesRotation = (mCalendar.get(Calendar.MINUTE) * 6f) + minuteHandOffset;

            final float hourHandOffset = minutesRotation / 12f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            Path path = new Path();
            path.moveTo(mCenterX, mCenterY);

            addLineFromBorder(path, hoursRotation);

            float newMinRot = minutesRotation;
            if (hoursRotation > minutesRotation) {
                newMinRot += 360;
            }

            int currentDeg = (int) hoursRotation + 1;

            for (int i = currentDeg; i < newMinRot; i++) {
                if (i % 360 == 315) {
                    addLineToPath(path, 0, 0);
                } else if (i % 360 == 135) {
                    addLineToPath(path, mCenterX * 2, mCenterY * 2);
                } else if (i % 360 == 45) {
                    addLineToPath(path, mCenterX * 2, 0);
                } else if (i % 360 == 225) {
                    addLineToPath(path, 0, mCenterY * 2);
                }
            }

            addLineFromBorder(path, minutesRotation);

            path.close();
            canvas.drawPath(path, mShapePaint);

            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                float tickAngle = tickIndex * 30f;
                if ((tickAngle > hoursRotation && tickAngle < newMinRot) || (tickAngle + 360 > hoursRotation && tickAngle + 360 < newMinRot)) {
                    float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                    float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                    float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                    float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                    float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                    canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                            mCenterX + outerX, mCenterY + outerY, mBlackTickPaint);
                }
            }
        }

        private void addLineFromBorder(Path path, float angle) {
            if (angle < 45) {
                float angleRad = (float) Math.toRadians(angle);
                addLineToPath(path, mCenterX + ((float) Math.tan(angleRad) * mCenterY), 0);
            } else if (angle < 90) {
                float angleRad = (float) Math.toRadians(Math.abs(angle - 90));
                addLineToPath(path, mCenterX * 2, mCenterY - ((float) Math.tan(angleRad) * mCenterX));
            } else if (angle < 135) {
                float angleRad = (float) Math.toRadians(Math.abs(angle - 90));
                addLineToPath(path, mCenterX * 2, mCenterY + ((float) Math.tan(angleRad) * mCenterX));
            } else if (angle < 180) {
                float angleRad = (float) Math.toRadians(Math.abs(angle - 180));
                addLineToPath(path, mCenterX + ((float) Math.tan(angleRad) * mCenterY), mCenterY * 2);
            } else if (angle < 225) {
                float angleRad = (float) Math.toRadians(Math.abs(angle - 180));
                addLineToPath(path, mCenterX - ((float) Math.tan(angleRad) * mCenterY), mCenterY * 2);
            } else if (angle < 270) {
                float angleRad = (float) Math.toRadians(Math.abs(angle - 270));
                addLineToPath(path, 0, mCenterY + ((float) Math.tan(angleRad) * mCenterX));
            } else if (angle < 315) {
                float angleRad = (float) Math.toRadians(Math.abs(angle - 270));
                addLineToPath(path, 0, mCenterY - ((float) Math.tan(angleRad) * mCenterX));
            } else {
                float angleRad = (float) Math.toRadians(Math.abs(angle - 360));
                addLineToPath(path, mCenterX - ((float) Math.tan(angleRad) * mCenterY), 0);
            }
        }

        private void addLineToPath(Path path, float x, float y) {
            path.lineTo(x, y);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            Minimalist.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            Minimalist.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
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
    }
}
