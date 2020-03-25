/*
 * Copyright (C) 2015-2020 akha, a.k.a. Alexander Kharitonov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package akha.yakhont.demo.gui;

import akha.yakhont.demo.R;

import akha.yakhont.Core;
import akha.yakhont.Core.Utils.MeasuredViewAdjuster;
import akha.yakhont.Core.Utils.ExecutorHelper;

import android.animation.Animator;
import android.animation.FloatEvaluator;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.os.Parcel;
import android.text.Html;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UpdateAppearance;
import android.util.DisplayMetrics;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * not directly related to the Yakhont Demo - just some GUI stuff
 */
@TargetApi  (      Build.VERSION_CODES.HONEYCOMB)
@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
public class Bubbles {

    private static final float              BUBBLES_INTERVAL_MAX    =  10   ;
    private static final float              BUBBLES_INTERVAL_MIN    =  10   ;
    private static final float              DURATION_MAX            =  10   ;
    private static final float              DURATION_FADE           =   2.5f;
    private static final float              DURATION_FIREWORKS      =   2   ;
    private static final float              DURATION_RAINBOW        = 180   ;

    @SuppressLint("StaticFieldLeak")
    private static ViewGroup                sRootLayout;

    private static LayoutInflater           sLayoutInflater;
    private static DisplayMetrics           sDisplayMetrics;
    
    private static CharSequence[]           sFunText;
    private static TypedArray               sFunColors;
    private static ArrayList<Typeface>      sFunTypefaces;
    private static OrderHelper              sFunTextOrder, sFunColorsOrder, sFunTypefacesOrder;

    private static final List<TextView>     sViews                  = Collections.synchronizedList(new ArrayList<>());

    private static       ExecutorHelper     sTimer;
    private static final Random             sRandom                 = new Random();
    private static final AtomicBoolean      sIsCancel               = new AtomicBoolean();

    private static final String             sNewLine                = Objects.requireNonNull /* should always happen */ (System.getProperty("line.separator"));

    private Bubbles() {
    }

    private static ViewGroup getRootLayout() {
        return sRootLayout;
    }

    public static void init(Activity activity) {
        sTimer                  = new ExecutorHelper();

        sIsCancel.set(false);
        synchronized (sViews) {
            sViews.clear();
        }

        sDisplayMetrics         = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(sDisplayMetrics);

        sRootLayout             = activity.findViewById(R.id.main_layout);
        sLayoutInflater         = LayoutInflater.from(activity);

        initFun(activity);

        scheduleNextBubble();
    }

    private static void scheduleNextBubble() {
        sTimer.runInBackground(new Runnable() {
            @Override
            public void run() {
                scheduleNextBubble();
                if (sIsCancel.get()) return;

                ExecutorHelper.postToMainLoop(new Runnable() {
                    @Override
                    public void run() {
                        startAnimation();
                    }

                    @NonNull
                    @Override
                    public String toString() {
                        return "startAnimation()";
                    }
                });
            }

            @NonNull
            @Override
            public String toString() {
                return "bubbles timer";
            }
        }, getBubblesInterval(), true);
    }

    private static int getBubblesInterval() {
        int interval    = sRandom.nextInt((int) (BUBBLES_INTERVAL_MAX * 1000));
        int intervalMin = (int) (BUBBLES_INTERVAL_MIN * 1000);
        return Math.max(interval, intervalMin);
    }

    public static void setState(final boolean cancel, final boolean clear) {
        sIsCancel.set(cancel);
        if (!cancel) return;

        synchronized (sViews) {
            for (TextView view: sViews.toArray(new TextView[0]))
                if (clear) {
                    cancelRainbow(view);

                    AnimatorHelper[] animators = (AnimatorHelper[]) view.getTag(R.id.animators);
                    if (animators != null) for (AnimatorHelper animator : animators)
                        animator.cancel();

                    ValueAnimator animator = (ValueAnimator) view.getTag(R.id.animator_main);
                    if (animator != null) animator.cancel();
                }
                else {
                    int size = 15;
                    float[] values = new float[size + 1];
                    for (int i = 0; i < size - 1; i++)
                        values[i] = 1f / size * (size - i);
                    values[size - 1] = 0.8f;
                    values[size    ] = 0;

                    ValueAnimator animatorFade = ValueAnimator.ofFloat(values);
                    animatorFade.setDuration((int) (DURATION_FADE * 1000));
                    animatorFade.setInterpolator(new LinearInterpolator());
                    //noinspection Convert2Lambda
                    animatorFade.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator valueAnimator) {
                            view.setAlpha((Float) valueAnimator.getAnimatedValue());
                        }
                    });
                    animatorFade.start();
                }
        }
    }

    private static void cancelRainbow(View view) {
        ObjectAnimator[] animators = (ObjectAnimator[]) view.getTag(R.id.animators_rainbow);
        if (animators != null) for (ObjectAnimator animator: animators)
            animator.cancel();
    }

    public static void cleanUp() {
        setState(true, true);

        sTimer.cancel();

        synchronized (sViews) {
            sViews.clear();
        }

        sLayoutInflater     = null;
        sRootLayout         = null;
    }

    private static void initFun(Activity activity) {
        Resources resources     = activity.getResources();
        
        sFunText                = resources.getTextArray    (R.array.advertisement_kitsch);
        sFunTextOrder           = new OrderHelper(sFunText.length / 2);
        
        sFunColors              = resources.obtainTypedArray(R.array.advertisement_colors);
        sFunColorsOrder         = new OrderHelper(sFunColors.length());
        
        sFunTypefaces           = new ArrayList<>();

        for (String name: new String[] {null /* default */, "monospace", "serif", "sans-serif", "sans-serif-light", "sans-serif-condensed"})
            for (int style: new int[] {Typeface.NORMAL, Typeface.BOLD, Typeface.ITALIC, Typeface.BOLD_ITALIC})
                sFunTypefaces.add(Typeface.create(name, style));

        sFunTypefacesOrder      = new OrderHelper(sFunTypefaces.size());
    }

    private static void startAnimation() {

        @SuppressLint("InflateParams")
        final TextView textView = (TextView) sLayoutInflater.inflate(R.layout.bubbles_text_view, null);

        int backgroundColor = sFunColors.getColor(sFunColorsOrder.get(), Color.TRANSPARENT /* just stub */ );
        int color = Core.Utils.getInvertedColor(backgroundColor);

        ShapeDrawable background = new ShapeDrawable(new OvalShape());
        background.getPaint().setColor(backgroundColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            textView.setBackground(background);
        else
            setBackgroundDrawable(textView, background);

        int idx = sFunTextOrder.get() * 2;
        setText(textView, String.format("%s%s%s", sFunText[idx], sNewLine, sFunText[idx + 1]), color);
        textView.setTypeface (sFunTypefaces.get(sFunTypefacesOrder.get()));
        textView.setTextColor(color);

        if (sIsCancel.get()) return;

        synchronized (sViews) {
            sViews.add(textView);
        }
        getRootLayout().addView(textView);

        //noinspection Convert2Lambda
        Core.Utils.onAdjustMeasuredView(new MeasuredViewAdjuster() {
            @SuppressWarnings("unused")
            @Override
            public void adjustMeasuredView(View view) {
                startAnimation((TextView) view);
            }
        }, textView);
    }

    @SuppressWarnings({"deprecation", "RedundantSuppression" /* lint bug workaround */ })
    private static void setBackgroundDrawable(View view, Drawable background) {
        view.setBackgroundDrawable(background);
    }

    private static void startAnimation(final TextView textView) {

        final int viewWidth  = textView.getMeasuredWidth ();
        final int viewHeight = textView.getMeasuredHeight();

        //noinspection SuspiciousNameCombination
        textView.getLayoutParams().height = viewWidth;

        //noinspection IntegerDivisionInFloatingPointContext
        textView.setPivotX(viewWidth  / 2);
        //noinspection IntegerDivisionInFloatingPointContext
        textView.setPivotY(viewHeight / 2);

        textView.setLeft(0);
        textView.setTop (0);

        ValueAnimator mainAnimator = ValueAnimator.ofInt(0, sDisplayMetrics.heightPixels);
        mainAnimator.setDuration((int) (DURATION_MAX * 1000));
        mainAnimator.setInterpolator(new AccelerateInterpolator());

        //noinspection Convert2Lambda
        mainAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                textView.setTranslationY((Integer) valueAnimator.getAnimatedValue());
            }
        });

        mainAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationCancel(Animator animator) {
            }
            @Override
            public void onAnimationEnd(Animator animator) {
                textView.setTag(R.id.animators, null);

                cancelRainbow(textView);
                textView.setTag(R.id.animators_rainbow, null);

                getRootLayout().removeView(textView);
                synchronized (sViews) {
                    sViews.remove(textView);
                }
            }
            @Override public void onAnimationStart (Animator animator) {}
            @Override public void onAnimationRepeat(Animator animator) {}
        });

        ArrayList<AnimatorHelper> animators = new ArrayList<>();

        //noinspection Convert2Lambda
        animators.add(new AnimatorHelper(textView, "ScaleX",           true, 3.5, 0.9, new AnimatorData() { @Override public float[] getData() {
            return new float[] {0.6f, 1 + sRandom.nextFloat()};
        }}));
        //noinspection Convert2Lambda
        animators.add(new AnimatorHelper(textView, "ScaleY",           true, 3.5, 0.9, new AnimatorData() { @Override public float[] getData() {
            return new float[] {0.6f, 1 + sRandom.nextFloat()};
        }}));
        //noinspection Convert2Lambda
        animators.add(new AnimatorHelper(textView, "Rotation",         true, 2.5, 0.5, new AnimatorData() { @Override public float[] getData() {
            return new float[] {   0, sRandom.nextInt(90) - 45};
        }}));
        animators.add(new AnimatorHelper(textView, "TranslationX",    false, 2.5,  -1, new AnimatorData() {

            private int start = getX();

            private int getX() {
                return sRandom.nextInt(sDisplayMetrics.widthPixels - viewWidth / 2);
            }

            @Override
            public float[] getData() {
                float[] data = new float[] {start, getX()};
                start = (int) data[1];
                return data;
            }
        }));

        textView.setTag(R.id.animator_main, mainAnimator);

        AnimatorHelper[] tmp = new AnimatorHelper[animators.size()];
        animators.toArray(tmp);
        textView.setTag(R.id.animators, tmp);

        mainAnimator.start();
        for (AnimatorHelper animator: animators)
            animator.start();
    }

    private static void setText(TextView view, String text, int color) {
        String target = "YAKHONT";
        text          = text.replace("Yakhont", target);

        SpannableString spannable = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            ArrayList<ObjectAnimator> animators = new ArrayList<>();
            spannable = AnimatedRainbow.run(view, text, target, animators);

            // same lint problem as above
//            view.setTag(R.id.animators_rainbow, animators.toArray(new ObjectAnimator[animators.size()]));
            ObjectAnimator[] tmp = new ObjectAnimator[animators.size()];
            animators.toArray(tmp);
            view.setTag(R.id.animators_rainbow, tmp);

            if (spannable == null) spannable = new SpannableString(text);
            AnimatedFireworks.run(view, spannable, color,
                    (int) (DURATION_FIREWORKS * 1000), animators);
        }        

        if (spannable == null) {
            text = text.replace(target, "<b>" + target + "</b>")
                    .replace(sNewLine, "<br>");
            //noinspection ConstantConditions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                @SuppressLint("InlinedApi") int flags = Html.FROM_HTML_MODE_LEGACY;
                view.setText(Html.fromHtml(text, flags));
            }
            else
                viewSetText(view, text);
        }
    }

    @SuppressWarnings({"deprecation", "RedundantSuppression" /* lint bug workaround */ })
    private static void viewSetText(TextView view, String text) {
        view.setText(Html.fromHtml(text));
    }

    private static class OrderHelper {

        private final ArrayList<Integer>    mList                   = new ArrayList<>();
        private final int                   mSize;

        private OrderHelper(int size) {
            mSize = size;
            fillList();
        }

        private void fillList() {
            for (int i = 0; i < mSize; i++)
                mList.add(i);
            Collections.shuffle(mList);
        }

        @SuppressWarnings("WeakerAccess")
        public int get() {
            int lastValue = mList.get(0);
            mList.remove(0);
            if (mList.isEmpty()) {
                fillList();
                if (lastValue == mList.get(0)) Collections.swap(mList, 0, 1);
            }
            return lastValue;
        }
    }

    private interface AnimatorData {
        float[] getData();
    }

    private static class AnimatorHelper implements Animator.AnimatorListener {
        
        private final View                  mView;
        private final String                mPropertyName;
        
        private final boolean               mReverse;
        private       boolean               mIsCancel;

        private final int                   mDuration;
        private final int                   mDurationThreshold;
        private final boolean               mRandomDuration;

        private       TimeInterpolator      mTimeInterpolator;
        private final AnimatorData          mAnimatorData;
        private       ObjectAnimator        mAnimator;
        private final Object                mLock                   = new Object();

        private AnimatorHelper(View view, String propertyName, boolean reverse, double duration, double durationThreshold, 
                               AnimatorData animatorData) {
            mView               = view;
            mPropertyName       = propertyName;
            mReverse            = reverse;
            mDuration           = toInt(duration         );
            mDurationThreshold  = toInt(durationThreshold);
            mRandomDuration     = durationThreshold >= 0;
            mAnimatorData       = animatorData;
        }

        private static int toInt(double d) {
            return (int) (d * 1000);
        }

        @SuppressWarnings("WeakerAccess")
        public void start() {
            synchronized (mLock) {
                startAsync();
            }
        }

        private void startAsync() {
            mAnimator = ObjectAnimator.ofFloat(mView, mPropertyName, mAnimatorData.getData());
            int duration = mRandomDuration ? sRandom.nextInt(mDuration) : mDuration;
            mAnimator.setDuration(mDurationThreshold > 0 && mDurationThreshold > duration ? mDurationThreshold : duration);
            if (mReverse) {
                mAnimator.setRepeatCount(1);
                mAnimator.setRepeatMode(ValueAnimator.REVERSE);
            }
            if (mTimeInterpolator != null) mAnimator.setInterpolator(mTimeInterpolator);
            mAnimator.addListener(this);
            mAnimator.start();
        }

        @SuppressWarnings("unused")
        public void setInterpolator(TimeInterpolator timeInterpolator) {
            mTimeInterpolator = timeInterpolator;
        }

        @SuppressWarnings("WeakerAccess")
        public void cancel() {
            synchronized (mLock) {
                mIsCancel = true;
                if (mAnimator != null) mAnimator.cancel();
            }
        }

        @Override
        public void onAnimationEnd(Animator animator) {
            synchronized (mLock) {
                if (!mIsCancel) startAsync();
            }
        }
        @Override public void onAnimationStart (Animator animator) {}
        @Override public void onAnimationCancel(Animator animator) {}
        @Override public void onAnimationRepeat(Animator animator) {}
    }

    /**
     * from https://github.com/chiuki/advanced-textView
     *
     * @author Chiu-Ki Chan
     */
    @TargetApi  (      Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private static class AnimatedRainbow {

        @SuppressWarnings("WeakerAccess")
        public static SpannableString run(final TextView view, String text, String toSpan,
                                          List<ObjectAnimator> animators) {
            final SpannableString spannable = new SpannableString(text);
            animators.clear();

            int pos = -1;
            for (;;) {
                pos = text.indexOf(toSpan, pos + 1);
                if (pos < 0) break;

                AnimatedColorSpan animatedColorSpan = new AnimatedColorSpan(view.getContext());
                StyleSpan         boldSpan          = new StyleSpan(Typeface.BOLD);

                spannable.setSpan(animatedColorSpan, pos, pos + toSpan.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannable.setSpan(boldSpan         , pos, pos + toSpan.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                ObjectAnimator animator = ObjectAnimator.ofFloat(animatedColorSpan, RAINBOW_PROPERTY, 0, 100);
                animator.setInterpolator  (new LinearInterpolator()       );
                animator.setDuration      ((int) (DURATION_RAINBOW * 1000));
                animator.setStartDelay    (animators.size() *  700        );
                animator.setRepeatCount   (ValueAnimator.INFINITE         );
                animator.setEvaluator     (new FloatEvaluator()           );
                //noinspection Convert2Lambda
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        view.setText(spannable);
                    }
                });
                animator.start();

                animators.add(animator);
            }
            return animators.size() > 0 ? spannable: null;
        }
        
        private static final Property<AnimatedColorSpan, Float> 
                                            RAINBOW_PROPERTY        = new Property<AnimatedColorSpan, Float>(Float.class, "RAINBOW_PROPERTY") {
            @Override
            public void set(AnimatedColorSpan span, Float value) {
                span.setTranslateXPercentage(value);
            }

            @Override
            public Float get(AnimatedColorSpan span) {
                return span.getTranslateXPercentage();
            }
        };
        
        private static class AnimatedColorSpan extends CharacterStyle implements UpdateAppearance {
            
            private static int[]            sColors;
            private Shader                  mShader;
            private final Matrix            mMatrix                 = new Matrix();
            private float                   mTranslateXPercentage;

            @SuppressWarnings("WeakerAccess")
            public AnimatedColorSpan(Context context) {
                if (sColors == null) sColors = context.getResources().getIntArray(R.array.rainbow_colors);
            }

            @SuppressWarnings("WeakerAccess")
            public void setTranslateXPercentage(float percentage) {
                mTranslateXPercentage = percentage;
            }

            @SuppressWarnings("WeakerAccess")
            public float getTranslateXPercentage() {
                return mTranslateXPercentage;
            }

            @Override
            public void updateDrawState(TextPaint paint) {
                paint.setStyle(Paint.Style.FILL);
                
                float y = paint.getTextSize() * sColors.length;
                if (mShader == null) mShader = new LinearGradient(0, 0, 0, y, sColors, null, Shader.TileMode.MIRROR);
                
                mMatrix.reset();
                mMatrix.setRotate(90);
                mMatrix.postTranslate(y * mTranslateXPercentage, 0);
                
                mShader.setLocalMatrix(mMatrix);
                paint.setShader(mShader);
            }
        }        
    }
    
    /**
     * from http://flavienlaurent.com/blog/2014/01/31/spans/
     *
     * @author Flavien Laurent (+ some corrections by akha)
     */
    @TargetApi  (      Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @RequiresApi(api = Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private static class AnimatedFireworks {
        
        @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
        public static void run(final TextView view, final SpannableString spannable, int color, int duration,
                               List<ObjectAnimator> animators) {
            final FireworksSpanGroup spanGroup = new FireworksSpanGroup();
            animators.clear();
            
            for (int i = 0; i < spannable.length(); i++) {
                MutableForegroundColorSpan span = new MutableForegroundColorSpan(0, color);
                spannable.setSpan(span, i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                spanGroup.addSpan(span);
            }
            
            spanGroup.init();
                        
            ObjectAnimator animator = ObjectAnimator.ofInt(spanGroup, FIREWORKS_PROPERTY, 0, spanGroup.getSpans().size());
            animator.setDuration(duration);
            //noinspection Convert2Lambda
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    view.setText(spannable);
                }
            });
            animator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    spanGroup.setProgress(spanGroup.getSpans().size());
                }
                @Override public void onAnimationStart (Animator animator) {}
                @Override public void onAnimationCancel(Animator animator) {}
                @Override public void onAnimationRepeat(Animator animator) {}
            });
            animator.start();

            animators.add(animator);
        }
        
        private static final Property<FireworksSpanGroup, Integer>
                                            FIREWORKS_PROPERTY      = new Property<FireworksSpanGroup, Integer>(Integer.class, "FIREWORKS_PROPERTY") {
            @Override
            public void set(FireworksSpanGroup spanGroup, Integer value) {
                spanGroup.setProgress(value);
            }

            @Override
            public Integer get(FireworksSpanGroup spanGroup) {
                return spanGroup.getProgress();
            }
        };
        
        private static class FireworksSpanGroup {
            
            private       int               mProgress;
            private final ArrayList<MutableForegroundColorSpan> 
                                            mSpans                  = new ArrayList<>();

            @SuppressWarnings("WeakerAccess")
            public void addSpan(MutableForegroundColorSpan span) {
                mSpans.add(span);
            }

            @SuppressWarnings("WeakerAccess")
            public ArrayList<MutableForegroundColorSpan> getSpans() {
                return mSpans;
            }

            @SuppressWarnings("WeakerAccess")
            public void init() {
                Collections.shuffle(mSpans);
            }

            @SuppressWarnings("WeakerAccess")
            public void setProgress(int progress) {
                mProgress = progress;

                for (int i = 0; i < mProgress; i++)
                    mSpans.get(i).setAlpha(255);
            }

            @SuppressWarnings("WeakerAccess")
            public int getProgress() {
                return mProgress;
            }
        }
        
        @SuppressLint("ParcelCreator")
        private static class MutableForegroundColorSpan extends ForegroundColorSpan {

            private int                     mAlpha;
            private int                     mForegroundColor;

            @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
            public MutableForegroundColorSpan(int alpha, int color) {
                super(color);
                
                mAlpha              = alpha;
                mForegroundColor    = color;
            }

            @SuppressWarnings("unused")
            public MutableForegroundColorSpan(Parcel src) {
                super(src);
                
                mForegroundColor    = src.readInt();
                mAlpha              = src.readInt();
            }

            public void writeToParcel(@NonNull Parcel dest, int flags) {
                super.writeToParcel(dest, flags);
                
                dest.writeInt  (mForegroundColor);
                dest.writeFloat(mAlpha);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint textPaint) {
                textPaint.setColor(getForegroundColor());
            }

            @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
            public void setAlpha(int alpha) {
                mAlpha = alpha;
            }

            @SuppressWarnings("unused")
            public void setForegroundColor(int foregroundColor) {
                mForegroundColor = foregroundColor;
            }

            @SuppressWarnings("unused")
            public float getAlpha() {
                return mAlpha;
            }

            @Override
            public int getForegroundColor() {
                return Color.argb(mAlpha, Color.red(mForegroundColor), Color.green(mForegroundColor), Color.blue(mForegroundColor));
            }
        }
    }
}
