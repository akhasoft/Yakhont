/*
 * Copyright (C) 2015-2017 akha, a.k.a. Alexander Kharitonov
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

package akha.yakhont.demo.gui;

import akha.yakhont.demo.MainFragment;
import akha.yakhont.demo.R;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.ViewSwitcher;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * not directly related to the Yakhont Demo - just some GUI stuff
 */
@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1) 
public class SlideShow {

    private static final int                SLIDE_SHOW_DELAY        = 2000;

    private ViewGroup                       mContainer;
    private View                            mSwipeRefreshView;
    private ImageSwitcher                   mImageView;

    private View                            mControlPanel;
    private View                            mControlPanelBtn;

    private Timer                           mTimer;
    private LayoutInflater                  mLayoutInflater;
    private int                             mImageCounter;

    private WeakReference<MainFragment>     mFragment               = new WeakReference<>(null);

    public void init(View view) {
        mContainer          = view.findViewById(R.id.container);
        mSwipeRefreshView   = view.findViewById(R.id.swipeContainer);
        mImageView          = view.findViewById(R.id.image_slide);

        mControlPanel       = view.findViewById(R.id.control_panel);
        mControlPanelBtn    = view.findViewById(R.id.btn_load);

        mContainer.setPersistentDrawingCache(ViewGroup.PERSISTENT_ANIMATION_CACHE);

        ((AbsListView) view.findViewById(R.id.grid)).setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                startSlideShow((String) view.findViewById(R.id.image).getTag());
            }
        });

        mImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cleanUpSlideShow();
            }
        });
    }

    public void init(MainFragment fragment) {
        mFragment       = new WeakReference<>(fragment);
        mLayoutInflater = LayoutInflater.from(fragment.getActivity());

        mImageView.setFactory(new ViewSwitcher.ViewFactory() {
            @Override
            public View makeView() {
                ImageView imageView = (ImageView) mLayoutInflater.inflate(R.layout.slide, mImageView, false);
                imageView.setScaleX(-1);    // 'cause of 3D rotation side effect
                return imageView;
            }
        });

        mImageView.setInAnimation (AnimationUtils.loadAnimation(fragment.getActivity(), android.R.anim.fade_in ));
        mImageView.setOutAnimation(AnimationUtils.loadAnimation(fragment.getActivity(), android.R.anim.fade_out));

        mImageView.setAnimateFirstView(false);
    }

    private void startSlideShow(final String idx) {
        cleanUpTimer();
        mTimer = new Timer("slide show timer");

        mTimer.scheduleAtFixedRate(new TimerTask() {

            private String              mPackage;
            private Resources           mResources;

            private int getResourceId(String name) {
                return mResources.getIdentifier(name, "drawable", mPackage);
            }

            @Override
            public void run() {
                Activity activity = getActivity();
                if (activity == null) return;

                mPackage   = activity.getPackageName();
                mResources = activity.getResources  ();

                if (getResourceId(getImageName(idx, ++mImageCounter)) == 0) mImageCounter = 1;

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        MainFragment fragment = mFragment.get();
                        if (fragment == null) return;

                        Rect rect = fragment.getSlideRect();
                        mImageView.setImageDrawable(new BitmapDrawable(mResources, Utils.decodeBitmap(
                                mResources, getResourceId(getImageName(idx, mImageCounter)),
                                rect.height(), rect.width())));
                    }
                });
            }
        }, 0, SLIDE_SHOW_DELAY);

        applyRotation(true);
    }

    private void cleanUpSlideShow() {
        cleanUpTimer();
        applyRotation(false);
    }

    private void cleanUpImage() {
        mImageView.reset();
    }

    public void cleanUp() {
        mFragment = new WeakReference<>(null);
        cleanUpTimer();
    }

    private void cleanUpTimer() {
        if (mTimer == null) return;

        mTimer.cancel();
        mTimer = null;

        mImageCounter = 0;
    }

    private String getImageName(String idx, int counter) {
        return String.format(Locale.getDefault(), "img_%s_%02d", idx, counter);
    }

    private Activity getActivity() {
        MainFragment fragment = mFragment.get();
        return fragment == null ? null: fragment.getActivity();
    }

    private void enableControlPanel(boolean enable) {
        mControlPanelBtn.setEnabled(enable);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////

    private final static int                DURATION                = 1000;
    private final static float              DEPTH_Z                 =  310;

    private float                           mDeltaY;

    private void applyRotation(final boolean showSlides) {
        MainFragment fragment = mFragment.get();
        if (fragment == null) return;

        fragment.onSlideShow(showSlides);
        
        float centerX = mContainer.getWidth()  / 2;
        float centerY = mContainer.getHeight() / 2;

        Rotate3dAnimation rotation = new Rotate3dAnimation(showSlides ? 0: 180, 90, centerX, centerY, DEPTH_Z, true);
        rotation.setDuration(DURATION / 2);
        rotation.setFillAfter(true);
        rotation.setInterpolator(new AccelerateInterpolator());

        rotation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }
            @Override
            public void onAnimationEnd(Animation animation) {
                mContainer.post(new SwapViews(showSlides));
            }
            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        mContainer.startAnimation(rotation);

        View view = fragment.getView();

        if (showSlides) {
            mDeltaY = mControlPanel.getHeight() -
                    ((ViewGroup.MarginLayoutParams) mControlPanel.getLayoutParams()).bottomMargin;

            mControlPanel.animate().setDuration((int) (DURATION * 0.75f)).alpha(0);
            //noinspection ConstantConditions
            view.animate().setDuration(DURATION).yBy(-mDeltaY).scaleY(1 + mDeltaY / view.getHeight());

            enableControlPanel(false);
        }
        else {
            mControlPanel.animate().setDuration((int) (DURATION / 0.75f)).alpha(1);
            //noinspection ConstantConditions
            view.animate().setDuration(DURATION).yBy(mDeltaY).scaleY(1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    enableControlPanel(true);
                }
            });
        }
    }

    private final class SwapViews implements Runnable {

        private final boolean               mShowSlides;

        public SwapViews(boolean showSlides) {
            mShowSlides = showSlides;
        }

        public void run() {
            float centerX = mContainer.getWidth()  / 2;
            float centerY = mContainer.getHeight() / 2;

            if (mShowSlides) {
                mSwipeRefreshView.setVisibility(View.GONE);
                mImageView.setVisibility(View.VISIBLE);
                mImageView.requestFocus();
            }
            else {
                mImageView.setVisibility(View.GONE);
                mSwipeRefreshView.setVisibility(View.VISIBLE);
                mSwipeRefreshView.requestFocus();

                cleanUpImage();
            }

            Rotate3dAnimation rotation = new Rotate3dAnimation(90, mShowSlides ? 180: 0, centerX, centerY, DEPTH_Z, false);
            rotation.setDuration(DURATION / 2);
            rotation.setFillAfter(true);
            rotation.setInterpolator(new DecelerateInterpolator());

            mContainer.startAnimation(rotation);
        }
    }
}
