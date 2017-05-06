package com.geeknat.spinify.activities;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.geeknat.spinify.R;
import com.geeknat.spinify.adapters.SpinnerGridAdapter;
import com.geeknat.spinify.utils.Utils;

import java.util.ArrayList;

public class Game extends AppCompatActivity {

    String MAXIMUM_VALUE = "MAXIMUM_VALUE",
            LAST_VALUE = "LAST_VALUE",
            TARGET = "TARGET";

    TextView tMax, tTarget, tCurrent;
    FrameLayout container;
    AppCompatButton btnHowToPlay, btnAmazon;
    int numberOfRotations = 0, target = 100, maximumValue = 0;
    Context context;
    SharedPreferences gameSettings;
    SharedPreferences.Editor gameSettingsEditor;
    private static Bitmap imageOriginal, imageScaled;
    private static Matrix matrix;
    private ImageButton btnSelect;
    private ImageView dialer;
    private int dialerHeight, dialerWidth;

    private GestureDetector detector;

    // needed for detecting the inversed rotations
    private boolean[] quadrantTouched;

    private boolean allowRotating;

    private Vibrator mVibrator;

    private String TAG = "SPINIFY";

    private ObjectAnimator rotationAnimator;

    int DURATION_FOR_ONE_ROTATION = 300;

    SoundPool soundPool;

    int soundId;

    boolean canAnimate = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        context = this;
        gameSettings = getSharedPreferences("gameSettings", MODE_PRIVATE);
        gameSettingsEditor = gameSettings.edit();
        gameSettingsEditor.apply();

        soundPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 100);

        container = (FrameLayout) findViewById(R.id.container);
        dialer = (ImageView) findViewById(R.id.fidgetSpinner);
        tMax = (TextView) findViewById(R.id.maximumScore);
        tTarget = (TextView) findViewById(R.id.targetValue);
        tCurrent = (TextView) findViewById(R.id.currentValue);
        btnAmazon = (AppCompatButton) findViewById(R.id.btnAmazon);
        btnHowToPlay = (AppCompatButton) findViewById(R.id.btnHowItWorks);

        btnHowToPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                howItWorks();
            }
        });

        btnAmazon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent amazonIntent = new Intent(Intent.ACTION_VIEW);
                amazonIntent.setData(Uri.parse("https://www.amazon.com/dp/B06ZZNBWGK"));
                startActivity(amazonIntent);
            }
        });

        btnSelect = (ImageButton) findViewById(R.id.btnSelect);
        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectSpinner();
            }
        });

        setImage(R.drawable.spinner_blue, false);

        dialer.setOnTouchListener(new MyOnTouchListener());
        dialer.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                // method called more than once, but the values only need to be initialized one time
                if (dialerHeight == 0 || dialerWidth == 0) {
                    dialerHeight = dialer.getHeight();
                    dialerWidth = dialer.getWidth();

                    // resize
                    Matrix resize = new Matrix();
                    resize.postScale((float) Math.min(dialerWidth, dialerHeight) / (float) imageOriginal.getWidth(), (float) Math.min(dialerWidth, dialerHeight) / (float) imageOriginal.getHeight());
                    imageScaled = Bitmap.createBitmap(imageOriginal, 0, 0, imageOriginal.getWidth(), imageOriginal.getHeight(), resize, false);

                    // translate to the image view's center
                    float translateX = dialerWidth / 2 - imageScaled.getWidth() / 2;
                    float translateY = dialerHeight / 2 - imageScaled.getHeight() / 2;
                    matrix.postTranslate(translateX, translateY);

                    dialer.setImageBitmap(imageScaled);
                    dialer.setImageMatrix(matrix);
                }
            }
        });

        updateViews();
        changeTarget();

    }


    public void setImage(int imageId, boolean isChanging) {
        // load the image only once
        imageOriginal = BitmapFactory.decodeResource(getResources(), imageId);

        matrix = new Matrix();

        detector = new GestureDetector(this, new MyGestureDetector());

        // there is no 0th quadrant, to keep it simple the first value gets ignored
        quadrantTouched = new boolean[]{false, false, false, false, false};

        allowRotating = true;

        if (isChanging) {

            dialerHeight = dialer.getHeight();
            dialerWidth = dialer.getWidth();
            // resize
            Matrix resize = new Matrix();
            resize.postScale((float) Math.min(dialerWidth, dialerHeight) / (float) imageOriginal.getWidth(), (float) Math.min(dialerWidth, dialerHeight) / (float) imageOriginal.getHeight());
            imageScaled = Bitmap.createBitmap(imageOriginal, 0, 0, imageOriginal.getWidth(), imageOriginal.getHeight(), resize, false);

            // translate to the image view's center
            float translateX = dialerWidth / 2 - imageScaled.getWidth() / 2;
            float translateY = dialerHeight / 2 - imageScaled.getHeight() / 2;
            matrix.postTranslate(translateX, translateY);

            dialer.setImageBitmap(imageScaled);
            dialer.setImageMatrix(matrix);

        }
    }

    /**
     * Rotate the dialer.
     *
     * @param degrees The degrees, the dialer should get rotated.
     */
    private void rotateDialer(float degrees) {
        matrix.postRotate(degrees, dialerWidth / 2, dialerHeight / 2);
        dialer.setImageMatrix(matrix);
    }

    /**
     * @return The angle of the unit circle with the image view's center
     */
    private double getAngle(double xTouch, double yTouch) {
        double x = xTouch - (dialerWidth / 2d);
        double y = dialerHeight - yTouch - (dialerHeight / 2d);

        switch (getQuadrant(x, y)) {
            case 1:
                return Math.asin(y / Math.hypot(x, y)) * 180 / Math.PI;
            case 2:
            case 3:
                return 180 - (Math.asin(y / Math.hypot(x, y)) * 180 / Math.PI);

            case 4:
                return 360 + Math.asin(y / Math.hypot(x, y)) * 180 / Math.PI;

            default:
                // ignore, does not happen
                return 0;
        }
    }

    /**
     * @return The selected quadrant.
     */
    private static int getQuadrant(double x, double y) {
        if (x >= 0) {
            return y >= 0 ? 1 : 4;
        } else {
            return y >= 0 ? 2 : 3;
        }

    }

    /**
     * Simple implementation of an {@link View.OnTouchListener} for registering the dialer's touch events.
     */
    private class MyOnTouchListener implements View.OnTouchListener {

        private double startAngle;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            soundPool.stop(soundId);
            if (rotationAnimator != null && rotationAnimator.isRunning()) {
                rotationAnimator.cancel();
            } else {

                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:
                        Log.d(TAG, "ACTION_DOWN");
                        // reset the touched quadrants
                        for (int i = 0; i < quadrantTouched.length; i++) {
                            quadrantTouched[i] = false;
                        }

                        allowRotating = false;

                        startAngle = getAngle(event.getX(), event.getY());
                        break;

                    case MotionEvent.ACTION_MOVE:
                        Log.d(TAG, "ACTION_MOVE_");
                        double currentAngle = getAngle(event.getX(), event.getY());
                        rotateDialer((float) (startAngle - currentAngle));
                        startAngle = currentAngle;
                        break;

                    case MotionEvent.ACTION_UP:
                        Log.d(TAG, "ACTION_UP");
                        allowRotating = true;
                        break;
                }
            }

            // set the touched quadrant to true
            quadrantTouched[getQuadrant(event.getX() - (dialerWidth / 2), dialerHeight - event.getY() - (dialerHeight / 2))] = true;

            detector.onTouchEvent(event);

            return true;
        }
    }

    /**
     * Simple implementation of a {@link GestureDetector.SimpleOnGestureListener} for detecting a fling event.
     */
    private class MyGestureDetector extends GestureDetector.SimpleOnGestureListener {


        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            Log.d(TAG, "ACTION_ON_SINGLE_TAP_PRESS");
            return super.onSingleTapConfirmed(e);
        }

        @Override
        public void onShowPress(MotionEvent e) {
            Log.d(TAG, "ACTION_ON_SHOW_PRESS");
            super.onShowPress(e);
        }

        @Override
        public void onLongPress(MotionEvent e) {
            Log.d(TAG, "ACTION_ON_LONG_PRESS");
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {

            // get the quadrant of the start and the end of the fling
            int q1 = getQuadrant(e1.getX() - (dialerWidth / 2), dialerHeight - e1.getY() - (dialerHeight / 2));
            int q2 = getQuadrant(e2.getX() - (dialerWidth / 2), dialerHeight - e2.getY() - (dialerHeight / 2));

            float velocity = (velocityX + velocityY) * 3;

            Log.d(TAG, "ACTION_FLING : X-VELOCITY : " + velocityX + ",Y-VELOCITY : " + velocityY + ",TOTAL : " + velocity);

            int rotationTime = (int) (velocity / 360) * DURATION_FOR_ONE_ROTATION;

            // the inversed rotations
            if ((q1 == 2 && q2 == 2 && Math.abs(velocityX) < Math.abs(velocityY))
                    || (q1 == 3 && q2 == 3)
                    || (q1 == 1 && q2 == 3)
                    || (q1 == 4 && q2 == 4 && Math.abs(velocityX) > Math.abs(velocityY))
                    || ((q1 == 2 && q2 == 3) || (q1 == 3 && q2 == 2))
                    || ((q1 == 3 && q2 == 4) || (q1 == 4 && q2 == 3))
                    || (q1 == 2 && q2 == 4 && quadrantTouched[3])
                    || (q1 == 4 && q2 == 2 && quadrantTouched[3])) {
                rotateWheelToTarget(dialer, 0, -1 * velocity, Utils.getUnsignedInt(rotationTime), 0);
            } else {
                rotateWheelToTarget(dialer, 0, velocity, Utils.getUnsignedInt(rotationTime), 0);
            }

            return true;
        }
    }

    float getDistance(float startX, float startY, MotionEvent ev) {
        float distanceSum = 0;
        final int historySize = ev.getHistorySize();
        for (int h = 0; h < historySize; h++) {
            // historical point
            float hx = ev.getHistoricalX(0, h);
            float hy = ev.getHistoricalY(0, h);
            // distance between startX,startY and historical point
            float dx = (hx - startX);
            float dy = (hy - startY);
            distanceSum += Math.sqrt(dx * dx + dy * dy);
            // make historical point the start point for next loop iteration
            startX = hx;
            startY = hy;
        }
        // add distance from last historical point to event's point
        float dx = (ev.getX(0) - startX);
        float dy = (ev.getY(0) - startY);
        distanceSum += Math.sqrt(dx * dx + dy * dy);
        return distanceSum;
    }


    private void howItWorks() {
        new AlertDialog.Builder(context)
                .setMessage("Reach the exact target, and see what happens...")
                .setPositiveButton("SURE", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(context)
                .setTitle("Exiting...")
                .setMessage("Are you sure you want to exit right now?")
                .setPositiveButton("NO", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("YES", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
                    }
                })
                .show();
    }

    public void rotateWheelToTarget(final View view, final float startDegree, final float endDegree, final int duration, final int numberOfRepeats) {

        final float playbackSpeed = 2f;

        AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        final float volume = mgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        if (rotationAnimator != null) {
            rotationAnimator.cancel();
        }

        rotationAnimator = ObjectAnimator.ofFloat(view, "rotation", startDegree, endDegree);
        rotationAnimator.setDuration(duration);
        rotationAnimator.setInterpolator(new DecelerateInterpolator());
        rotationAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {

                float currentRotation = view.getRotation();

                int totalRotations = (int) Math.floor(Utils.getUnsignedInt((int) endDegree) / 360);

                numberOfRotations = (int) Math.floor(Utils.getUnsignedInt((int) currentRotation) / 360);

                float volumePercentage = (float) numberOfRotations / totalRotations;

                Log.d(TAG, "TOTAL : " + totalRotations + ",NUMBER_OF_ROT : " + numberOfRotations + ",FULL DUR : " + duration + "VOLUME_" + volumePercentage);

                soundPool.setVolume(soundId, 1 - volumePercentage, 1 - volumePercentage);

                soundPool.setRate(soundId, playbackSpeed - volumePercentage);

                tCurrent.setText("Current:" + String.valueOf(numberOfRotations));
            }

        });

        rotationAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {

                Log.d(TAG, "ANIM_STARTED");

                tCurrent.setText("Current:0");

                soundId = soundPool.load(context, R.raw.spin, 1);

                soundPool.stop(soundId);

                soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                    @Override
                    public void onLoadComplete(SoundPool arg0, int arg1, int arg2) {
                        soundPool.play(soundId, volume, volume, 1, -1, playbackSpeed);
                    }
                });


            }

            @Override
            public void onAnimationEnd(Animator animation) {

                Log.d(TAG, "ANIM_STARTED");

                soundPool.stop(soundId);

                if (numberOfRotations > gameSettings.getInt(MAXIMUM_VALUE, 0)) {
                    //NEW MAXIMUM VALUE
                    gameSettingsEditor.putInt(MAXIMUM_VALUE, numberOfRotations);
                    gameSettingsEditor.apply();
                }

                if (numberOfRotations > gameSettings.getInt(LAST_VALUE, 0)) {
                    target = numberOfRotations + 20;
                    gameSettingsEditor.putInt(LAST_VALUE, numberOfRotations);
                    gameSettingsEditor.putInt(TARGET, target);
                    gameSettingsEditor.apply();
                }

                updateViews();
                changeTarget();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                soundPool.stop(soundId);
            }

            @Override
            public void onAnimationRepeat(Animator animation) {

            }
        });
        rotationAnimator.start();

    }

    private void updateViews() {
        maximumValue = gameSettings.getInt(MAXIMUM_VALUE, 0);
        tMax.setText("MAX:" + String.valueOf(maximumValue));
    }

    private void changeTarget() {
        target = gameSettings.getInt(TARGET, 100);
        tTarget.setText("Target:" + String.valueOf(target));
    }

    private synchronized void vibrate(long duration) {
        if (mVibrator == null) {
            mVibrator = (Vibrator)
                    getSystemService(Context.VIBRATOR_SERVICE);
        }
        mVibrator.vibrate(duration);
    }

    @Override
    protected void onPause() {
        if (rotationAnimator != null && rotationAnimator.isRunning()) {
            soundPool.pause(soundId);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                rotationAnimator.pause();
            } else {
                rotationAnimator.start();
            }
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (rotationAnimator != null && rotationAnimator.isPaused()) {
                soundPool.resume(soundId);
                rotationAnimator.resume();
            }
        } else {
            if (rotationAnimator != null) {
                rotationAnimator.start();
            }
        }
        super.onResume();
    }


    public void selectSpinner() {

        if (rotationAnimator != null) {
            rotationAnimator.cancel();
        }

        final ArrayList<Integer> spinnerList = new ArrayList<>();
        spinnerList.add(R.drawable.spinner);
        spinnerList.add(R.drawable.spinner_blue);
        spinnerList.add(R.drawable.spinner_blue_1);
        spinnerList.add(R.drawable.spinner_blue_2);
        spinnerList.add(R.drawable.spinner_red_1);
        spinnerList.add(R.drawable.spinner_red_2);
        spinnerList.add(R.drawable.spinner_red_4);
        spinnerList.add(R.drawable.spinner_red_5);
        spinnerList.add(R.drawable.spinner_gold);
        spinnerList.add(R.drawable.spinner_green);

        final Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.select_spinner_dialog);
        dialog.setCancelable(true);
        dialog.show();

        GridView gridView = (GridView) dialog.findViewById(R.id.spinnerGrid);
        gridView.setAdapter(new SpinnerGridAdapter(context, spinnerList));
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                setImage(spinnerList.get(position), true);
                btnSelect.setImageResource(spinnerList.get(position));
                dialog.dismiss();
            }
        });

    }
}
