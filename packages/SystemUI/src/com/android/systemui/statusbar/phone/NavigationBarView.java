/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.StatusBarManager;
import android.content.ContentResolver;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.database.ContentObserver;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import com.android.internal.util.vanir.KeyButtonInfo;
import com.android.internal.util.vanir.NavbarConstants;
import com.android.internal.util.vanir.NavbarConstants.NavbarConstant;
import com.android.systemui.R;
import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.DelegateViewHelper;
import com.android.systemui.statusbar.policy.DeadZone;
import com.android.systemui.statusbar.policy.KeyButtonView;
import com.android.systemui.statusbar.policy.LayoutChangerButtonView;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

public class NavigationBarView extends LinearLayout {
    final static boolean DEBUG = false;
    final static String TAG = "PhoneStatusBar/NavigationBarView";
 
    private static final int LONGPRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private static final int CHANGER_LEFT_SIDE = 0;
    private static final int CHANGER_RIGHT_SIDE = 1;
    private static final int LAYOUT_IME = NavbarConstants.LAYOUT_IME;

    // slippery nav bar when everything is disabled, e.g. during setup
    final static boolean SLIPPERY_WHEN_DISABLED = true;

    final static String NAVBAR_EDIT_ACTION = "android.intent.action.NAVBAR_EDIT";
    final static boolean NAVBAR_ALWAYS_AT_RIGHT = true;


    private boolean mInEditMode;
    private NavbarEditor mEditBar;
    private NavBarReceiver mNavBarReceiver;
    private OnClickListener mRecentsClickListener;
    private OnTouchListener mRecentsPreloadListener;
    private OnTouchListener mHomeSearchActionListener;
    private OnLongClickListener mRecentsBackListener;

    private BaseStatusBar mBar;
    final Display mDisplay;
    View mCurrentView = null;
    View[] mRotatedViews = new View[4];
    private LinearLayout navButtons;
    private LinearLayout lightsOut;

    int mBarSize;
    boolean mVertical;
    boolean landscape;
    boolean mScreenOn;
    boolean mLeftInLandscape;

    boolean mShowMenu;
    int mDisabledFlags = 0;
    int mNavigationIconHints = 0;

    private Drawable mBackIcon, mBackLandIcon, mBackAltIcon, mBackAltLandIcon;
    private Drawable mHomeIcon, mHomeLandIcon;

    private NavigationBarViewTaskSwitchHelper mTaskSwitchHelper;
    private DelegateViewHelper mDelegateHelper;
    private DeadZone mDeadZone;
    private final NavigationBarTransitions mBarTransitions;
    private KeyButtonView button;
    private LayoutChangerButtonView changer;
    private KeyButtonInfo info;

    // Visibility of R.id.one view prior to swapping it for a left arrow key
    public int mSlotOneVisibility = -1;

    // Visibility of R.id.six view prior to swapping it for a right arrow key
    public int mSlotSixVisibility = -1;

    // workaround for LayoutTransitions leaving the nav buttons in a weird state (bug 5549288)
    final static boolean WORKAROUND_INVALID_LAYOUT = true;
    final static int MSG_CHECK_INVALID_LAYOUT = 8686;

    // performs manual animation in sync with layout transitions
    private final NavTransitionListener mTransitionListener = new NavTransitionListener();

    private Resources mThemedResources;

    private OnVerticalChangedListener mOnVerticalChangedListener;
    private boolean mIsLayoutRtl;
    private boolean mDelegateIntercepted;

    private SettingsObserver mSettingsObserver;
    private boolean mShowDpadArrowKeys;

    // Navigation bar customizations
    final boolean mTablet = isTablet(mContext);
    private boolean mLegacyMenu;
    private boolean mImeLayout;
    private int mLongPressTimeout = LONGPRESS_TIMEOUT;
    private String mIMEKeyLayout;
    private boolean showingIME;
    private int mButtonLayouts;
    private int mCurrentLayout = 0; //the first one
    private float mButtonWidth, mMenuButtonWidth, mLayoutChangerWidth;
    private boolean mShowingImeLayout;

    private String[] mButtonContainerStrings = new String[5];
    ArrayList<ArrayList<KeyButtonInfo>> mAllButtonContainers = new ArrayList<ArrayList<KeyButtonInfo>>();

    private static final String[] buttonSettings = new String[] {
        Settings.System.NAVIGATION_BAR_BUTTONS,
        Settings.System.NAVIGATION_BAR_BUTTONS_TWO,
        Settings.System.NAVIGATION_BAR_BUTTONS_THREE,
        Settings.System.NAVIGATION_BAR_BUTTONS_FOUR,
        Settings.System.NAVIGATION_BAR_BUTTONS_FIVE
    };

    private ContentObserver mSettingsObserver;

    private class NavTransitionListener implements TransitionListener {
        private boolean mBackTransitioning;
        private boolean mHomeAppearing;
        private long mStartDelay;
        private long mDuration;
        private TimeInterpolator mInterpolator;

        @Override
        public void startTransition(LayoutTransition transition, ViewGroup container,
                View view, int transitionType) {
            if (view.getTag() != null) {
                if (view.getTag().equals(NavbarConstant.ACTION_BACK.value())) {
                    mBackTransitioning = true;
                } else if (view.getTag().equals(NavbarConstant.ACTION_HOME.value())
                    && transitionType == LayoutTransition.APPEARING) {
                    mHomeAppearing = true;
                    mStartDelay = transition.getStartDelay(transitionType);
                    mDuration = transition.getDuration(transitionType);
                    mInterpolator = transition.getInterpolator(transitionType);
                }
            }
        }

        @Override
        public void endTransition(LayoutTransition transition, ViewGroup container,
                View view, int transitionType) {
            if (view.getTag() != null) {
                if (view.getTag().equals(NavbarConstant.ACTION_BACK.value())) {
                    mBackTransitioning = false;
                } else if (view.getTag().equals(NavbarConstant.ACTION_HOME.value())
                    && transitionType == LayoutTransition.APPEARING) {
                    mHomeAppearing = false;
                }
            }
        }

        public void onBackAltCleared() {
            if (getBackButton() == null) return;
            // When dismissing ime during unlock, force the back button to run the same appearance
            // animation as home (if we catch this condition early enough).
            if (!mBackTransitioning && getBackButton().getVisibility() == VISIBLE
                    && mHomeAppearing && getHomeButton().getAlpha() == 0) {
                getBackButton().setAlpha(0);
                ValueAnimator a = ObjectAnimator.ofFloat(getBackButton(), "alpha", 0, 1);
                a.setStartDelay(mStartDelay);
                a.setDuration(mDuration);
                a.setInterpolator(mInterpolator);
                a.start();
            }
        }
    }

    private final OnClickListener mImeSwitcherClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            ((InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showInputMethodPicker();
        }
    };

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_CHECK_INVALID_LAYOUT:
                    final String how = "" + m.obj;
                    final int w = getWidth();
                    final int h = getHeight();
                    final int vw = mCurrentView.getWidth();
                    final int vh = mCurrentView.getHeight();

                    if (h != vh || w != vw) {
                        Log.w(TAG, String.format(
                            "*** Invalid layout in navigation bar (%s this=%dx%d cur=%dx%d)",
                            how, w, h, vw, vh));
                        if (WORKAROUND_INVALID_LAYOUT) {
                            requestLayout();
                        }
                    }
                    break;
            }
        }
    }

    public NavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mDisplay = ((WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();

        final Resources res = getContext().getResources();
        final ContentResolver cr = mContext.getContentResolver();

        getIcons(res);

        mBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);
        mVertical = false;
        mShowMenu = false;
        mDelegateHelper = new DelegateViewHelper(this);
        mTaskSwitchHelper = new NavigationBarViewTaskSwitchHelper(context);

        mButtonWidth = res.getDimensionPixelSize(R.dimen.navigation_key_width);
        mMenuButtonWidth = res.getDimensionPixelSize(R.dimen.navigation_menu_key_width);
        mLayoutChangerWidth = res.getDimensionPixelSize(R.dimen.navigation_layout_changer_width);

        mLegacyMenu = Settings.System.getInt(cr, Settings.System.NAVIGATION_BAR_SIDEKEYS, 1) == 1;
        mImeLayout = Settings.System.getInt(cr, Settings.System.NAVIGATION_BAR_ARROWS, 0) == 1;
        mButtonLayouts = Settings.System.getInt(cr, Settings.System.NAVIGATION_BAR_ALTERNATE_LAYOUTS, 1);
        for(int i=0;i<mButtonLayouts;i++)
            mButtonContainerStrings[i] = Settings.System.getString(cr, buttonSettings[i]);
        if (mButtonLayouts == 1)
            mCurrentLayout = 0; //1;
        mIMEKeyLayout = NavbarConstants.defaultIMEKeyLayout(mContext);
        mLongPressTimeout = Settings.System.getInt(cr,
                Settings.System.SOFTKEY_LONG_PRESS_CONFIGURATION, LONGPRESS_TIMEOUT);

        mBarTransitions = new NavigationBarTransitions(this);

        mNavBarReceiver = new NavBarReceiver();
        mSettingsObserver = new SettingsObserver(new Handler());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
        mContext.registerReceiverAsUser(mNavBarReceiver, UserHandle.ALL,
                new IntentFilter(NAVBAR_EDIT_ACTION), null, null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
        mContext.unregisterReceiver(mNavBarReceiver);
    }

    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    public void setDelegateView(View view) {
        mDelegateHelper.setDelegateView(view);
    }

    public void setBar(BaseStatusBar phoneStatusBar) {
        mBar = phoneStatusBar;
        mTaskSwitchHelper.setBar(mBar);
        mDelegateHelper.setBar(mBar);
    }

    public void disableSearchBar() {
        mDelegateHelper.setDisabled(true);
    }

    public void enableSearchBar() {
        mDelegateHelper.setDisabled(false);
    }

    public void setOnVerticalChangedListener(OnVerticalChangedListener onVerticalChangedListener) {
        mOnVerticalChangedListener = onVerticalChangedListener;
        notifyVerticalChangedListener(mVertical);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        initDownStates(event);
        if (!mDelegateIntercepted && mTaskSwitchHelper.onTouchEvent(event)) {
            return true;
        }
        if (mDeadZone != null && event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            mDeadZone.poke(event);
        }
        if (mDelegateHelper != null && mDelegateIntercepted) {
            boolean ret = mDelegateHelper.onInterceptTouchEvent(event);
            if (ret) return true;
        }
        return super.onTouchEvent(event);
    }

    private void initDownStates(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mDelegateIntercepted = false;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        initDownStates(event);
        boolean intercept = mTaskSwitchHelper.onInterceptTouchEvent(event);
        if (!intercept || mInEditMode) {
            mDelegateIntercepted = mDelegateHelper.onInterceptTouchEvent(event);
            intercept = mDelegateIntercepted;
        } else {
            MotionEvent cancelEvent = MotionEvent.obtain(event);
            cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
            mDelegateHelper.onInterceptTouchEvent(cancelEvent);
            cancelEvent.recycle();
        }
        return intercept;
    }

    private H mHandler = new H();

    public View getCurrentView() {
        return mCurrentView;
    }

    public View getRecentsButton() {
        return mCurrentView.findViewWithTag(NavbarConstant.ACTION_RECENTS.value());
    }

    public View getLeftLayoutButton() {
        return mCurrentView.findViewWithTag(NavbarConstant.ACTION_LAYOUT_LEFT.value());
    }

    public View getRightLayoutButton() {
        return mCurrentView.findViewWithTag(NavbarConstant.ACTION_LAYOUT_RIGHT.value());
    }

    public View getMenuButton() {
        return mCurrentView.findViewWithTag(NavbarConstant.ACTION_MENU.value());
    }

    public View getBackButton() {
        return mCurrentView.findViewWithTag(NavbarConstant.ACTION_BACK.value());
    }

    public View getEmptySpace() {
        return mCurrentView.findViewWithTag(NavbarConstant.ACTION_BLANK.value());
    }

    public View getHomeButton() {
        return mCurrentView.findViewWithTag(NavbarConstant.ACTION_HOME.value());
    }

    public View getImeSwitchButton() {
        return mCurrentView.findViewWithTag(NavbarConstant.ACTION_IME.value());
    }

    public View getImeLayoutChanger() {
        return mCurrentView.findViewWithTag(NavbarConstant.ACTION_IME_LAYOUT.value());
    }

    private void getIcons(Resources res) {
        mBackIcon = res.getDrawable(R.drawable.ic_sysbar_back);
        mBackLandIcon = res.getDrawable(R.drawable.ic_sysbar_back_land);
        mBackAltIcon = res.getDrawable(R.drawable.ic_sysbar_back_ime);
        mBackAltLandIcon = res.getDrawable(R.drawable.ic_sysbar_back_ime);
        mHomeIcon = res.getDrawable(R.drawable.ic_sysbar_home);
        mHomeLandIcon = res.getDrawable(R.drawable.ic_sysbar_home_land);
    }

    public void updateResources(Resources res) {
        mThemedResources = res;
        getIcons(mThemedResources);
        mBarTransitions.updateResources(res);
        for (int i = 0; i < mRotatedViews.length; i++) {
            ViewGroup container = (ViewGroup) mRotatedViews[i];
            if (container != null) {
                updateLightsOutResources(container);
            }
        }
    }

    private void updateLightsOutResources(ViewGroup container) {
        ViewGroup lightsOut = (ViewGroup) container.findViewById(R.id.lights_out);
        if (lightsOut != null) {
            final int nChildren = lightsOut.getChildCount();
            for (int i = 0; i < nChildren; i++) {
                final View child = lightsOut.getChildAt(i);
                if (child instanceof ImageView) {
                    final ImageView iv = (ImageView) child;
                    // clear out the existing drawable, this is required since the
                    // ImageView keeps track of the resource ID and if it is the same
                    // it will not update the drawable.
                    iv.setImageDrawable(null);
                    iv.setImageDrawable(mThemedResources.getDrawable(
                            R.drawable.ic_sysbar_lights_out_dot_large));
                }
            }
        }
    }

    @Override
    public void setLayoutDirection(int layoutDirection) {
        getIcons(mThemedResources != null ? mThemedResources : getContext().getResources());
        super.setLayoutDirection(layoutDirection);
    }

    public void notifyScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        setDisabledFlags(mDisabledFlags, true);
    }

    public void setNavigationIconHints(int hints) {
        setNavigationIconHints(hints, false);
    }

    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints) return;
        showingIME = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;

        if ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0 && !showingIME) {
            mTransitionListener.onBackAltCleared();
        }
        if (DEBUG) {
            android.widget.Toast.makeText(getContext(),
                "Navigation icon hints = " + hints,
                500).show();
        }

        mNavigationIconHints = hints;

        if (mImeLayout) {
            if (mLegacyMenu && mButtonLayouts == 1) {
				// show hard-coded switchers here when written
				getImeSwitchButton().setVisibility(showingIME ? View.VISIBLE : View.INVISIBLE);
				getImeLayoutChanger().setVisibility(showingIME ? View.VISIBLE : View.INVISIBLE);
            }
            if (mButtonLayouts > 1) {
                if (getLeftLayoutButton() != null) {
                    setLayoutChangerType(getLeftLayoutButton(), CHANGER_LEFT_SIDE);
                } else if (getImeLayoutChanger() != null) {
                    setLayoutChangerType(getImeLayoutChanger(), CHANGER_LEFT_SIDE);
                }
                if (getRightLayoutButton() != null) {
                    setLayoutChangerType(getRightLayoutButton(), CHANGER_RIGHT_SIDE);
                } else if (getImeSwitchButton() != null) { 
                    setLayoutChangerType(getImeSwitchButton(), CHANGER_RIGHT_SIDE);
                } else if (getMenuButton() != null) {
                    setLayoutChangerType(getMenuButton(), CHANGER_RIGHT_SIDE);
                }
            }
            if (!showingIME) notifyLayoutChange(0);
        }

        if (getBackButton() != null) {
/*          comment this out until backbuttondrawable is properly sizing images
            ((KeyButtonView) getBackButton()).setImageDrawable(null);
            ((KeyButtonView) getBackButton()).setImageDrawable(mVertical ? mBackLandIcon : mBackIcon);
            mBackLandIcon.setImeVisible(showingIME);
            mBackIcon.setImeVisible(showingIME);
*/          if (showingIME) {
                ((ImageView) getBackButton()).setImageResource(R.drawable.ic_sysbar_back_ime);
            } else {
                ((KeyButtonView) getBackButton()).setImage();
            }
        }

        ((ImageView)getHomeButton()).setImageDrawable(mVertical ? mHomeLandIcon : mHomeIcon);

        final boolean showImeButton = ((hints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) != 0)
                && !mShowDpadArrowKeys;
        getImeSwitchButton().setVisibility(showImeButton ? View.VISIBLE : View.INVISIBLE);

        if (mShowDpadArrowKeys) { // overrides IME button
            final boolean showingIme = ((mNavigationIconHints
                    & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0);

            setVisibleOrGone(getCurrentView().findViewById(R.id.dpad_left), showingIme);
            setVisibleOrGone(getCurrentView().findViewById(R.id.dpad_right), showingIme);

            View one = getCurrentView().findViewById(mVertical ? R.id.six : R.id.one);
            View six = getCurrentView().findViewById(mVertical ? R.id.one : R.id.six);
            if (showingIme) {
                mSlotOneVisibility = one.getVisibility();
                mSlotSixVisibility = six.getVisibility();
                setVisibleOrGone(one, false);
                setVisibleOrGone(six, false);
            } else {
                if (mSlotOneVisibility != -1) {
                    one.setVisibility(mSlotOneVisibility);
                    mSlotOneVisibility = -1;
                }
                if (mSlotSixVisibility != -1) {
                    six.setVisibility(mSlotSixVisibility);
                    mSlotSixVisibility = -1;
                }
            }
        }
        // Update menu button in case the IME state has changed.
        setMenuVisibility(mShowMenu, true);
        setDisabledFlags(mDisabledFlags, true);
    }
 
    private void setLayoutChangerType(View v, int side) {
        switch (side) {
            case CHANGER_LEFT_SIDE:
               ((LayoutChangerButtonView) v).setImeLayout(
                        showingIME, getResources().getConfiguration().orientation, mTablet);
                break;
            case CHANGER_RIGHT_SIDE:
               ((LayoutChangerButtonView) v).setImeSwitch(
                        showingIME, getResources().getConfiguration().orientation, mTablet);
                break;
        }
    }

    public void notifyLayoutChange(int direction) {
        if (direction == LAYOUT_IME) {
            if (!mShowingImeLayout) {
                mHandler.post(mNotifyImeLayoutChange);
                mShowingImeLayout = true;
            } else {
                mHandler.post(mNotifyLayoutChanged);
                mShowingImeLayout = false;
            }
        } else {
            mCurrentLayout = (mCurrentLayout + direction + mButtonLayouts) % mButtonLayouts;
            mHandler.post(mNotifyLayoutChanged);
            mShowingImeLayout = false;
        }
    }

    final Runnable mNotifyImeLayoutChange = new Runnable() {
        @Override
        public void run() {
            setupNavigationButtons(getButtonsArray(mIMEKeyLayout.split("\\|")));
        }
    };

    final Runnable mNotifyLayoutChanged = new Runnable() {
        @Override
        public void run() {
            setupNavigationButtons(getCurrentButtonArray());
        }
    };

    public void setDisabledFlags(int disabledFlags) {
        setDisabledFlags(disabledFlags, false);
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        if (mAllButtonContainers.get(mCurrentLayout).size() == 0) return; // no buttons yet!

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);
        final boolean disableSearch = ((disabledFlags & View.STATUS_BAR_DISABLE_SEARCH) != 0);

        if (SLIPPERY_WHEN_DISABLED) {
            setSlippery(disableHome && disableRecent && disableBack && disableSearch);
        }

        ViewGroup navButtons = (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
        if (navButtons != null) {
            LayoutTransition lt = navButtons.getLayoutTransition();
            if (lt != null) {
                if (!lt.getTransitionListeners().contains(mTransitionListener)) {
                    lt.addTransitionListener(mTransitionListener);
                }
                if (!mScreenOn && mCurrentView != null) {
                    lt.disableTransitionType(
                            LayoutTransition.CHANGE_APPEARING |
                            LayoutTransition.CHANGE_DISAPPEARING |
                            LayoutTransition.APPEARING |
                            LayoutTransition.DISAPPEARING);
                }
            }
        }

        KeyButtonView[] allButtons = getAllButtons();
        for (KeyButtonView button : allButtons) {
            if (button != null) {
                Object tag = button.getTag();
                if (tag == null) {
                    setVisibleOrInvisible(button, !disableHome);
                } else if (NavbarConstant.ACTION_HOME.value().equals(tag)) {
                    setVisibleOrInvisible(button, !disableHome);
                } else if (NavbarConstant.ACTION_BACK.value().equals(tag)) {
                    setVisibleOrInvisible(button, !disableBack);
                } else if (NavbarConstant.ACTION_RECENTS.value().equals(tag)) {
                    setVisibleOrInvisible(button, !disableRecent);
                } else {
                    setVisibleOrInvisible(button, !disableRecent);
                }
            }
        }

        if (getButtonView(ACTION_BACK) != null)
                getButtonView(ACTION_BACK)   .setVisibility(disableBack       ? View.INVISIBLE : View.VISIBLE);
        if (getButtonView(ACTION_HOME) != null)
                getButtonView(ACTION_HOME)   .setVisibility(disableHome       ? View.INVISIBLE : View.VISIBLE);
        if (getButtonView(ACTION_RECENTS) != null)
                getButtonView(ACTION_RECENTS).setVisibility(disableRecent     ? View.INVISIBLE : View.VISIBLE);

        mBarTransitions.applyBackButtonQuiescentAlpha(mBarTransitions.getMode(), true /*animate*/);

        if (mButtonLayouts > 1) {
            if (!mImeLayout) {
                final boolean allowLayoutArrows = !disableHome && !showingIME;
                setVisibleOrInvisible(getLeftLayoutButton(), allowLayoutArrows);
                setVisibleOrInvisible(getRightLayoutButton(), allowLayoutArrows);
            }
        } else if (mButtonLayouts == 1) {
            if (mLegacyMenu && mImeLayout) {
				// show hard-coded switchers here when written
				if (getImeSwitchButton() != null) getImeSwitchButton()
						.setVisibility(showingIME ? View.VISIBLE : View.INVISIBLE);
				if (getImeLayoutChanger() != null) getImeLayoutChanger()
						.setVisibility(showingIME ? View.VISIBLE : View.INVISIBLE);
            }
		}
    }

    private void setVisibleOrInvisible(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : INVISIBLE);
        }
    }

    private void setVisibleOrGone(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    public void setSlippery(boolean newSlippery) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        if (lp != null) {
            boolean oldSlippery = (lp.flags & WindowManager.LayoutParams.FLAG_SLIPPERY) != 0;
            if (!oldSlippery && newSlippery) {
                lp.flags |= WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else if (oldSlippery && !newSlippery) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else {
                return;
            }
            WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(this, lp);
        }
    }

    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    public void setMenuVisibility(final boolean show, final boolean force) {
        if (!force && mShowMenu == show) return;

        mShowMenu = show;

        // Only show Menu if IME switcher not shown.
        final boolean shouldShow = mShowMenu &&
                ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) == 0);

        if (mLegacyMenu && !showingIME) {
            if (mButtonLayouts != 1) {
                if (attachedToLayoutButtonView(ACTION_LAYOUT_RIGHT) != null) {
                    ((LayoutChangerButtonView) getButtonView(ACTION_LAYOUT_RIGHT)).setMenuAction(
                            shouldShow, getResources().getConfiguration().orientation, mTablet);
                } else if (attachedToLayoutButtonView(ACTION_MENU) != null) {
                    ((LayoutChangerButtonView) getButtonView(ACTION_MENU)).setMenuAction(
                            shouldShow, getResources().getConfiguration().orientation, mTablet);
                }
            } else {
                if (!mImeLayout && (getButtonView(ACTION_MENU) != null)) setVisibleOrInvisible(getButtonView(ACTION_MENU), mShowMenu);
            }
        }
    }

    @Override
    public void onFinishInflate() {
        mRotatedViews[Configuration.ORIENTATION_PORTRAIT] = findViewById(R.id.rot0);
        mRotatedViews[Configuration.ORIENTATION_LANDSCAPE] = findViewById(R.id.rot90);
        mCurrentView = mRotatedViews[mContext.getResources().getConfiguration().orientation];

        getImeSwitchButton().setOnClickListener(mImeSwitcherClickListener);

        updateRTLOrder();
    }

   @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        final ContentResolver r = mContext.getContentResolver();

        if (mSettingsObserver == null) {
            mSettingsObserver = new ContentObserver(new Handler()) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
					if (uri.equals(Settings.System.getUriFor(Settings.System.SOFTKEY_LONG_PRESS_CONFIGURATION))) {
						mLongPressTimeout = Settings.System.getInt(r,
								Settings.System.SOFTKEY_LONG_PRESS_CONFIGURATION, LONGPRESS_TIMEOUT);
					} else {
						mImeLayout = Settings.System.getInt(r, Settings.System.NAVIGATION_BAR_ARROWS, 0) == 1;
                        mLegacyMenu = Settings.System.getInt(r, Settings.System.NAVIGATION_BAR_SIDEKEYS, 1) == 1;
						mButtonLayouts = Settings.System.getInt(r, Settings.System.NAVIGATION_BAR_ALTERNATE_LAYOUTS, 1);

						for(int i=0;i<mButtonLayouts;i++)
							mButtonContainerStrings[i] = Settings.System.getString(r, buttonSettings[i]);

						loadButtonArrays();
					}
                }};

            for(int i=0;i<5;i++)
                r.registerContentObserver(Settings.System.getUriFor(buttonSettings[i]), false, mSettingsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_SIDEKEYS),
                    false, mSettingsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_ARROWS),
                    false, mSettingsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_IME_LAYOUT),
                    false, mSettingsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_ALTERNATE_LAYOUTS),
                    false, mSettingsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.SOFTKEY_LONG_PRESS_CONFIGURATION),
                    false, mSettingsObserver);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        final ContentResolver r = mContext.getContentResolver();

        if (mSettingsObserver != null) {
            r.unregisterContentObserver(mSettingsObserver);
            mSettingsObserver = null;
        }
    }

    private void loadButtonArrays() {
        mAllButtonContainers.clear();
        for (int j = 0; j < mButtonLayouts; j++) {
            if (mButtonContainerStrings[j] == null) {
                mButtonContainerStrings[j] = NavbarConstants.defaultNavbarLayout(mContext);
            }
            mAllButtonContainers.add(getButtonsArray(mButtonContainerStrings[j].split("\\|")));
        }
        setupNavigationButtons(getCurrentButtonArray());
    }

    private ArrayList<KeyButtonInfo> getButtonsArray(final String[] userButtons) {
        final ArrayList<KeyButtonInfo> mButtonsContainer = new ArrayList<KeyButtonInfo>();
        for (String button : userButtons) {
            final String[] actions = button.split(",", 4);
            mButtonsContainer.add(new KeyButtonInfo(actions[0], actions[1], actions[2], actions[3]));
        }
        return mButtonsContainer;
    }

    private ArrayList<KeyButtonInfo> getCurrentButtonArray() {
		if (mCurrentLayout >= mButtonLayouts) mCurrentLayout = mButtonLayouts - 1;
        return mAllButtonContainers.get(mCurrentLayout);
    }

    private void setupNavigationButtons(ArrayList<KeyButtonInfo> buttonsArray) {
        final boolean stockThreeButtonLayout = buttonsArray.size() == 3;
        final int length = buttonsArray.size();
        final int separatorSize = (int) mMenuButtonWidth;

        for (int i = 0; i <= 1; i++) {
            landscape = (i == 1);

            navButtons = (LinearLayout) (landscape ? mRotatedViews[Surface.ROTATION_90]
                    .findViewById(R.id.nav_buttons) : mRotatedViews[Surface.ROTATION_0]
                    .findViewById(R.id.nav_buttons));
            lightsOut = (LinearLayout) (landscape ? mRotatedViews[Surface.ROTATION_90]
                    .findViewById(R.id.lights_out) : mRotatedViews[Surface.ROTATION_0]
                    .findViewById(R.id.lights_out));
            navButtons.removeAllViews();
            lightsOut.removeAllViews();

            // multiple layouts: left-side layout changer
            if (mButtonLayouts > 1) {
                if (!mImeLayout || (mImeLayout && !showingIME)) {
                    info = new KeyButtonInfo(NavbarConstant.ACTION_LAYOUT_LEFT.value());
                    changer = new LayoutChangerButtonView(mContext, null);
                    changer.setButtonActions(info);
                    if (mTablet) {
                        changer.setImageResource(R.drawable.ic_sysbar_layout_left);
                    } else {
                        changer.setImageResource(landscape
                                ? R.drawable.ic_sysbar_layout_left_landscape
                                : R.drawable.ic_sysbar_layout_left);
                    }
                    changer.setLayoutParams(getLayoutParams(landscape, mLayoutChangerWidth, 0f));

                    addButton(navButtons, changer, landscape);
                    addLightsOutButton(lightsOut, changer, landscape, false);
                }
                if (mImeLayout && showingIME) {
                    info = new KeyButtonInfo(NavbarConstant.ACTION_IME_LAYOUT.value());
                    changer = new LayoutChangerButtonView(mContext, null);
                    changer.setButtonActions(info);
                    changer.setImageResource(R.drawable.ic_sysbar_ime_arrows);
                    changer.setLayoutParams(getLayoutParams(landscape, mLayoutChangerWidth, 0f));

                    addButton(navButtons, changer, landscape);
                    addLightsOutButton(lightsOut, changer, landscape, false);
                }
            }

            // single layout: AOSP key spacing on left side
            if (mLegacyMenu && mButtonLayouts == 1) {
				if (mImeLayout) {
                    info = new KeyButtonInfo(NavbarConstant.ACTION_IME_LAYOUT.value());
                    changer = new LayoutChangerButtonView(mContext, null);
                    changer.setButtonActions(info);
                    changer.setImageResource(R.drawable.ic_sysbar_ime_arrows);
                    changer.setLayoutParams(getLayoutParams(landscape, mTablet ? mMenuButtonWidth : separatorSize, 0f));

                    addButton(navButtons, changer, landscape);
                    addLightsOutButton(lightsOut, changer, landscape, false);
                    changer.setVisibility(showingIME ? View.VISIBLE : View.INVISIBLE);
                } else {
                    addSeparator(navButtons, landscape, mTablet ? (int) mMenuButtonWidth : separatorSize, 0f);
                    addSeparator(lightsOut, landscape, mTablet ? (int) mMenuButtonWidth : separatorSize, 0f);
                }
                if (mTablet) {
                    addSeparator(navButtons, landscape, 0, stockThreeButtonLayout ? 1f : 0.5f);
                    addSeparator(lightsOut, landscape, 0, stockThreeButtonLayout ? 1f : 0.5f);
                }
            }

            // add the custom buttons
            for (int j = 0; j < length; j++) {
                info = buttonsArray.get(j);
                button = new KeyButtonView(mContext, null);
                button.setButtonActions(info);
                button.setLongPressTimeout(mLongPressTimeout);
                button.setLayoutParams(getLayoutParams(landscape, mButtonWidth, mTablet ? 1f : 0.5f));
                addButton(navButtons, button, landscape);

                if (!button.mHasBlankSingleAction) {
                    addLightsOutButton(lightsOut, button, landscape, false);
                } else {
                    addSeparator(lightsOut, landscape, (int) mButtonWidth, mTablet ? 1f : 0.5f);
                }
            }

            // single layout: legacy menu button/AOSP spacing on right side
            if (mLegacyMenu && mButtonLayouts == 1) {
                info = new KeyButtonInfo(mImeLayout
					   ? mShowMenu
								? NavbarConstant.ACTION_MENU.value()
								: NavbarConstant.ACTION_IME.value()
					   : NavbarConstant.ACTION_MENU.value());
                changer = new LayoutChangerButtonView(mContext, null);
                changer.setButtonActions(info);
                changer.setImageResource(mImeLayout
						? mShowMenu
								? R.drawable.ic_sysbar_menu
								: R.drawable.ic_ime_switcher_default
						: R.drawable.ic_sysbar_menu);
                changer.setLayoutParams(getLayoutParams(landscape, mTablet ? mMenuButtonWidth : separatorSize, 0f));
                changer.setVisibility(mShowMenu || (mImeLayout && showingIME) ? View.VISIBLE : View.INVISIBLE);

                if (mTablet) {
                    addSeparator(navButtons, landscape, 0,  stockThreeButtonLayout ? 1f : 0.5f);
                    addSeparator(lightsOut, landscape, 0,  stockThreeButtonLayout ? 1f : 0.5f);
                }
                addButton(navButtons, changer, landscape);
                addLightsOutButton(lightsOut, changer, landscape, true);
            }

            // multiple layouts: right-side layout changer button/ime switcher/legacy menu
            if (mButtonLayouts > 1) {
                if (!mImeLayout || (mImeLayout && !showingIME)) {
                    info = new KeyButtonInfo(mShowMenu
                            ? NavbarConstant.ACTION_MENU.value()
                            : NavbarConstant.ACTION_LAYOUT_RIGHT.value());
                    changer = new LayoutChangerButtonView(mContext, null);
                    changer.setButtonActions(info);
                    if (mTablet) {
                        changer.setImageResource(mShowMenu
                                ? R.drawable.ic_sysbar_menu
                                : R.drawable.ic_sysbar_layout_right);
                    } else {
                         changer.setImageResource(mShowMenu
                                ? R.drawable.ic_sysbar_menu
                                : landscape
                                        ? R.drawable.ic_sysbar_layout_right_landscape
                                        : R.drawable.ic_sysbar_layout_right);
                    }
                    changer.setLayoutParams(getLayoutParams(landscape, mLayoutChangerWidth, 0f));

                    addButton(navButtons, changer, landscape);
                    addLightsOutButton(lightsOut, changer, landscape, false);
                }
                if (mImeLayout && showingIME) {
                    info = new KeyButtonInfo(NavbarConstant.ACTION_IME.value());
                    changer = new LayoutChangerButtonView(mContext, null);
                    changer.setButtonActions(info);
                    changer.setImageResource(R.drawable.ic_ime_switcher_default);
                    changer.setLayoutParams(getLayoutParams(landscape, mLayoutChangerWidth, 0f));

                    addButton(navButtons, changer, landscape);
                    addLightsOutButton(lightsOut, changer, landscape, false);
                }
            }
        }
        invalidate();

        // Reset the navigation search assistant
	if (getHomeButton() != null && mBar != null) {
	    boolean needsHomeActionListener = !((KeyButtonView) getHomeButton()).mHasLongAction;
	    if (needsHomeActionListener) mBar.setHomeActionListener();
	}
    }

    public boolean isVertical() {
        return mVertical;
    }

    public void setLeftInLandscape(boolean leftInLandscape) {
        mLeftInLandscape = leftInLandscape;
        mDeadZone.setStartFromRight(leftInLandscape);
    }

    public void reorient() {
        int orientation = mContext.getResources().getConfiguration().orientation;
        mRotatedViews[Configuration.ORIENTATION_PORTRAIT].setVisibility(View.GONE);
        mRotatedViews[Configuration.ORIENTATION_LANDSCAPE].setVisibility(View.GONE);
        mCurrentView = mRotatedViews[orientation];
        mCurrentView.setVisibility(View.VISIBLE);
        if (NavbarEditor.isDevicePhone(mContext)) {
            int rotation = mDisplay.getRotation();
            mVertical = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
        } else {
            mVertical = getWidth() > 0 && getHeight() > getWidth();
        }
        mEditBar = new NavbarEditor(mCurrentView, mVertical, mIsLayoutRtl);
        updateSettings();

        mDeadZone = (DeadZone) mCurrentView.findViewById(R.id.deadzone);
        mDeadZone.setStartFromRight(mLeftInLandscape);

        // force the low profile & disabled states into compliance
        mBarTransitions.init(mVertical);
        setMenuVisibility(mShowMenu, true /* force */);

        if (DEBUG) {
            Log.d(TAG, "reorient(): rot=" + mDisplay.getRotation());
        }

        // swap to x coordinate if orientation is not in vertical
        if (mDelegateHelper != null) {
            mDelegateHelper.setSwapXY(mVertical);
        }
        updateTaskSwitchHelper();

        setNavigationIconHints(mNavigationIconHints, true);
        setDisabledFlags(mDisabledFlags, true /* force */);
    }

    private void updateTaskSwitchHelper() {
        boolean isRtl = (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
        mTaskSwitchHelper.setBarState(mVertical, isRtl);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mDelegateHelper.setInitialTouchRegion(getHomeButton(), getBackButton(), getRecentsButton());
        ViewGroup midNavButtons = (ViewGroup) mCurrentView.findViewById(R.id.mid_nav_buttons);
        int count = midNavButtons.getChildCount();
        View buttons[] = new View[count];

        for (int i = 0; i < count; i++) {
            buttons[i] = midNavButtons.getChildAt(i);
        }

        mDelegateHelper.setInitialTouchRegion(buttons);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onSizeChanged: (%dx%d) old: (%dx%d)", w, h, oldw, oldh));

        final boolean newVertical = w > 0 && h > w;
        if (newVertical != mVertical) {
            mVertical = newVertical;
            //Log.v(TAG, String.format("onSizeChanged: h=%d, w=%d, vert=%s", h, w, mVertical?"y":"n"));
            reorient();
            notifyVerticalChangedListener(newVertical);
        }

        postCheckForInvalidLayout("sizeChanged");
        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void notifyVerticalChangedListener(boolean newVertical) {
        if (mOnVerticalChangedListener != null) {
            mOnVerticalChangedListener.onVerticalChanged(newVertical);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateRTLOrder();
        updateTaskSwitchHelper();
    }

    public KeyButtonView[] getAllButtons() {
        ViewGroup view = (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
        int N = view.getChildCount();
        KeyButtonView[] views = new KeyButtonView[N];

        int workingIdx = 0;
        for (int i = 0; i < N; i++) {
            View child = view.getChildAt(i);
            if (child instanceof KeyButtonView) {
                views[workingIdx++] = (KeyButtonView) child;
            }
        }
        return views;
    }

    /**
     * In landscape, the LinearLayout is not auto mirrored since it is vertical. Therefore we
     * have to do it manually
     */
    private void updateRTLOrder() {
        boolean isLayoutRtl = getResources().getConfiguration()
                .getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        if (mIsLayoutRtl != isLayoutRtl) {
            mIsLayoutRtl = isLayoutRtl;
            reorient();
        }
    }

    /*
    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onLayout: %s (%d,%d,%d,%d)",
                    changed?"changed":"notchanged", left, top, right, bottom));
        super.onLayout(changed, left, top, right, bottom);
    }

    // uncomment this for extra defensiveness in WORKAROUND_INVALID_LAYOUT situations: if all else
    // fails, any touch on the display will fix the layout.
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (DEBUG) Log.d(TAG, "onInterceptTouchEvent: " + ev.toString());
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            postCheckForInvalidLayout("touch");
        }
        return super.onInterceptTouchEvent(ev);
    }
    */


    private String getResourceName(int resId) {
        if (resId != 0) {
            final android.content.res.Resources res = getContext().getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    private void postCheckForInvalidLayout(final String how) {
        mHandler.obtainMessage(MSG_CHECK_INVALID_LAYOUT, 0, 0, how).sendToTarget();
    }

    private static String visibilityToString(int vis) {
        switch (vis) {
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return "VISIBLE";
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NavigationBarView {");
        final Rect r = new Rect();
        final Point size = new Point();
        mDisplay.getRealSize(size);

        pw.println(String.format("      this: " + PhoneStatusBar.viewInfo(this)
                        + " " + visibilityToString(getVisibility())));

        getWindowVisibleDisplayFrame(r);
        final boolean offscreen = r.right > size.x || r.bottom > size.y;
        pw.println("      window: "
                + r.toShortString()
                + " " + visibilityToString(getWindowVisibility())
                + (offscreen ? " OFFSCREEN!" : ""));

        pw.println(String.format("      mCurrentView: id=%s (%dx%d) %s",
                        getResourceName(mCurrentView.getId()),
                        mCurrentView.getWidth(), mCurrentView.getHeight(),
                        visibilityToString(mCurrentView.getVisibility())));

        pw.println(String.format("      disabled=0x%08x vertical=%s menu=%s",
                        mDisabledFlags,
                        mVertical ? "true" : "false",
                        mShowMenu ? "true" : "false"));

        dumpButton(pw, "back", getBackButton());
        dumpButton(pw, "home", getHomeButton());
        dumpButton(pw, "rcnt", getRecentsButton());
        dumpButton(pw, "menu", getMenuButton());

        pw.println("    }");
    }

    private static void dumpButton(PrintWriter pw, String caption, View button) {
        pw.print("      " + caption + ": ");
        if (button == null) {
            pw.print("null");
        } else {
            pw.print(PhoneStatusBar.viewInfo(button)
                    + " " + visibilityToString(button.getVisibility())
                    + " alpha=" + button.getAlpha()
                    );
            if (button instanceof KeyButtonView) {
                pw.print(" drawingAlpha=" + ((KeyButtonView)button).getDrawingAlpha());
                pw.print(" quiescentAlpha=" + ((KeyButtonView)button).getQuiescentAlpha());
            }
        }
        pw.println();
    }

    private void addSeparator(LinearLayout layout, boolean landscape, int size, float weight) {
        Space separator = new Space(mContext);
        separator.setLayoutParams(getLayoutParams(landscape, size, weight));
        if (landscape && !mTablet) {
            layout.addView(separator, 0);
        } else {
            layout.addView(separator);
        }
    }

    private void addButton(ViewGroup root, View v, boolean landscape) {
        if (landscape && !mTablet)
            root.addView(v, 0);
        else
            root.addView(v);
    }

    public LinearLayout.LayoutParams getLayoutParams(boolean landscape, float px, float weight) {
        if (weight != 0) {
            px = 0;
        }
        return landscape && !mTablet ?
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) px, weight) :
                new LinearLayout.LayoutParams((int) px, LinearLayout.LayoutParams.MATCH_PARENT, weight);
    }

    private void addLightsOutButton(LinearLayout root, View v, boolean landscape, boolean empty) {
        ImageView addMe = new ImageView(mContext);
        addMe.setLayoutParams(v.getLayoutParams());
        addMe.setImageResource(empty ? R.drawable.ic_sysbar_lights_out_dot_large
                : R.drawable.ic_sysbar_lights_out_dot_small);
        addMe.setScaleType(ImageView.ScaleType.CENTER);
        addMe.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        if (landscape && !mTablet)
            root.addView(addMe, 0);
        else
            root.addView(addMe);
    }

    public static boolean isTablet(Context context) {
        boolean xlarge = ((context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == 4);
        boolean large = ((context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_LARGE);
        return (xlarge || large);
    }

    public interface OnVerticalChangedListener {
        void onVerticalChanged(boolean isVertical);
    }

    void setListeners(OnClickListener recentsClickListener, OnTouchListener recentsPreloadListener,
                      OnLongClickListener recentsBackListener, OnTouchListener homeSearchActionListener) {
        mRecentsClickListener = recentsClickListener;
        mRecentsPreloadListener = recentsPreloadListener;
        mHomeSearchActionListener = homeSearchActionListener;
        mRecentsBackListener = recentsBackListener;
        updateButtonListeners();
    }

    private void removeButtonListeners() {
        ViewGroup container = (ViewGroup) mCurrentView.findViewById(R.id.container);
        int viewCount = container.getChildCount();
        for (int i = 0; i < viewCount; i++) {
            View button = container.getChildAt(i);
            if (button instanceof KeyButtonView) {
                button.setOnClickListener(null);
                button.setOnTouchListener(null);
            }
        }
    }

    protected void updateButtonListeners() {
        View recentView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_RECENT);
        if (recentView != null) {
            recentView.setOnClickListener(mRecentsClickListener);
            recentView.setOnTouchListener(mRecentsPreloadListener);
            recentView.setLongClickable(true);
            recentView.setOnLongClickListener(mRecentsBackListener);
        }
        View backView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_BACK);
        if (backView != null) {
            backView.setLongClickable(true);
            backView.setOnLongClickListener(mRecentsBackListener);
        }
        View homeView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_HOME);
        if (homeView != null) {
            homeView.setOnTouchListener(mHomeSearchActionListener);
        }
    }

    public boolean isInEditMode() {
        return mInEditMode;
    }

    private void setButtonWithTagVisibility(Object tag, boolean visible) {
        View findView = mCurrentView.findViewWithTag(tag);
        if (findView == null) {
            return;
        }
        int visibility = visible ? View.VISIBLE : View.INVISIBLE;
        if (mSlotOneVisibility != -1 && findView.getId() == R.id.one) {
            mSlotOneVisibility = visibility;
        } else if (mSlotSixVisibility != -1 && findView.getId() == R.id.six) {
            mSlotSixVisibility = visibility;
        } else {
            findView.setVisibility(visibility);
        }
    }

    // TODO LINK TO THIS ONCE THEMES GOES IN
    protected void updateResources() {
        getIcons(mContext.getResources());
    }

    public class NavBarReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean edit = intent.getBooleanExtra("edit", false);
            boolean save = intent.getBooleanExtra("save", false);
            if (edit != mInEditMode) {
                mInEditMode = edit;
                if (edit) {
                    removeButtonListeners();
                    mEditBar.setEditMode(true);
                } else {
                    if (save) {
                        mEditBar.saveKeys();
                    }
                    mEditBar.setEditMode(false);
                    updateSettings();
                }
            }
        }
    }

    public void updateSettings() {
        mEditBar.updateKeys();
        removeButtonListeners();
        updateButtonListeners();
        setDisabledFlags(mDisabledFlags, true /* force */);
        setMenuVisibility(mShowMenu, true);
    }

    private class SettingsObserver extends UserContentObserver {

        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_MENU_ARROW_KEYS),
                    false, this, UserHandle.USER_ALL);

            // intialize mModlockDisabled
            onChange(false);
        }

        @Override
        protected void unobserve() {
            super.unobserve();
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        protected void update() {
            mShowDpadArrowKeys = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.NAVIGATION_BAR_MENU_ARROW_KEYS, 0, UserHandle.USER_CURRENT) != 0;
            mSlotOneVisibility = -1;
            mSlotSixVisibility = -1;
            setNavigationIconHints(mNavigationIconHints, true);
        }
    }
}
