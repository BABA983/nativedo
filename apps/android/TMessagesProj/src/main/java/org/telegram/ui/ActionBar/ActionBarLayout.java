/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.RoundedCorner;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.Keep;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class ActionBarLayout extends FrameLayout implements INavigationLayout {

    public class LayoutContainer extends FrameLayout {

        private boolean isSupportEdgeToEdge;

        public LayoutContainer(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        public void setShouldHandleBottomInsets(boolean isSupportEdgeToEdge) {
            if (this.isSupportEdgeToEdge != isSupportEdgeToEdge) {
                this.isSupportEdgeToEdge = isSupportEdgeToEdge;
                ViewCompat.requestApplyInsets(ActionBarLayout.this);
            }
        }

        @Override
        public void setTranslationX(float translationX) {
            boolean invalidate = getTranslationX() != translationX;
            super.setTranslationX(translationX);
            if (invalidate) {
                ActionBarLayout.this.invalidate();
            }
        }

        @Override
        public void setAlpha(float alpha) {
            boolean invalidate = getAlpha() != alpha;
            super.setAlpha(alpha);
            if (invalidate) {
                ActionBarLayout.this.invalidate();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            int actionBarHeight = 0;

            for (int i = 0, count = getChildCount(); i < count; i++) {
                View child = getChildAt(i);
                if (child instanceof ActionBar && child.getVisibility() != GONE) {
                    child.measure(
                            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED)
                    );
                    actionBarHeight = child.getMeasuredHeight();
                    break;
                }
            }

            for (int i = 0, count = getChildCount(); i < count; i++) {
                View child = getChildAt(i);
                if (!(child instanceof ActionBar)) {
                    if (child.getTag(0xFF112233) != null || child.getFitsSystemWindows() || child instanceof BaseFragment.AttachedSheetWindow) {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, actionBarHeight);
                    }
                }
            }

            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int actionBarHeight = 0;
            for (int i = 0, count = getChildCount(); i < count; i++) {
                View child = getChildAt(i);
                if (child instanceof ActionBar && child.getVisibility() != GONE) {
                    actionBarHeight = child.getMeasuredHeight();
                    child.layout(0, 0, child.getMeasuredWidth(), actionBarHeight);
                    break;
                }
            }

            for (int i = 0, count = getChildCount(); i < count; i++) {
                View child = getChildAt(i);
                if (!(child instanceof ActionBar) && child.getVisibility() != GONE) {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
                    if (child.getTag(0xFF112233) != null || child.getFitsSystemWindows() || child instanceof BaseFragment.AttachedSheetWindow) {
                        child.layout(
                                lp.leftMargin,
                                lp.topMargin,
                                lp.leftMargin + child.getMeasuredWidth(),
                                lp.topMargin + child.getMeasuredHeight()
                        );
                    } else {
                        child.layout(
                                lp.leftMargin,
                                lp.topMargin + actionBarHeight,
                                lp.leftMargin + child.getMeasuredWidth(),
                                lp.topMargin + actionBarHeight + child.getMeasuredHeight()
                        );
                    }
                }
            }
        }
    }

    private LayoutContainer containerViewBack;
    public LayoutContainer containerView;
    public View sheetContainer;
    private BottomSheetTabs bottomSheetTabs;
    private BottomSheetTabs.ClipTools bottomSheetTabsClip;
    private BottomSheetTabsOverlay bottomSheetTabsOverlay;
    private final ArrayList<BaseFragment> fragmentsStack = new ArrayList<>();
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect rect = new Rect();
    private final RectF roundedClipRect = new RectF();
    private final Path clipPath = new Path();
    private final float[] radii = new float[8];

    public float innerTranslationX;
    private boolean maybeStartTracking;
    private boolean startedTracking;
    private int startedTrackingX;
    private int startedTrackingY;
    private int startedTrackingPointerId = -1;
    private VelocityTracker velocityTracker;
    private View layoutToIgnore;
    private boolean beginTrackingSent;

    private final ArrayList<int[]> animateStartColors = new ArrayList<>();
    private final ArrayList<int[]> animateEndColors = new ArrayList<>();
    private final StartColorsProvider startColorsProvider = new StartColorsProvider();
    public MessageDrawable messageDrawableOutStart;
    public MessageDrawable messageDrawableOutMediaStart;
    public ThemeAnimationSettings.onAnimationProgress animationProgressListener;
    private final ArrayList<ArrayList<ThemeDescription>> themeAnimatorDescriptions = new ArrayList<>();
    private ArrayList<ThemeDescription> presentingFragmentDescriptions;
    private final ArrayList<ThemeDescription.ThemeDescriptionDelegate> themeAnimatorDelegate = new ArrayList<>();
    private AnimatorSet themeAnimatorSet;
    private final AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    private float themeAnimationValue;
    private boolean animateThemeAfterAnimation;
    private Theme.ThemeInfo animateSetThemeAfterAnimation;
    private boolean animateSetThemeAfterAnimationApply;
    private boolean animateSetThemeNightAfterAnimation;
    private int animateSetThemeAccentIdAfterAnimation;

    private boolean animationInProgress;
    private boolean predictiveInput;
    private boolean predictiveBackInProgress;
    private boolean predictiveBackHasProgress;
    private boolean predictiveBackLeft = true;
    private float predictiveBackY;
    private AnimatorSet backAnimator;
    private AnimatorSet bottomSheetAnimator;
    private Runnable animationRunnable;
    private float animationProgress;
    private long lastFrameTime;
    private BaseFragment newFragment;
    private BaseFragment oldFragment;
    private WindowInsetsCompat lastWindowInsetsCompat;
    private boolean isKeyboardVisible;
    private boolean withShadow;
    private Runnable rootDismissRunnable;
    private final DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator(1.5f);
    private boolean mainLayout = true;
    private boolean sheetLayout;
    private Runnable fragmentStackChangedListener;
    private DrawerLayoutContainer drawerLayoutContainer;
    private Window attachedWindow;
    private int lastNavigationBarColor;
    private boolean lifecycleResumed = true;

    public ActionBarLayout(Context context) {
        this(context, true);
    }

    public ActionBarLayout(Context context, boolean main) {
        super(context);
        mainLayout = main;
        // Root layout is registered as the global by MainActivity. Sheets (ActionBarSheet)
        // create their own ActionBarLayout and must NOT clobber LaunchActivity.actionBarLayout.
        AndroidUtilities.init(context);
        setClipChildren(false);
        setClipToPadding(false);
        updateShellBackgroundColor();

        if (mainLayout) {
            bottomSheetTabs = new BottomSheetTabs(context, this);
            bottomSheetTabsClip = new BottomSheetTabs.ClipTools(bottomSheetTabs);
            bottomSheetTabs.listen(this::invalidate, this::relayout);
            addView(bottomSheetTabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 68 + 8, Gravity.BOTTOM | Gravity.START));
        }

        containerViewBack = new LayoutContainer(context);
        containerViewBack.setVisibility(INVISIBLE);
        addView(containerViewBack, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));

        containerView = new LayoutContainer(context);
        addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));
        sheetContainer = containerView;

        if (mainLayout) {
            bottomSheetTabsOverlay = new BottomSheetTabsOverlay(context);
            bottomSheetTabsOverlay.setTabsView(bottomSheetTabs);
            addView(bottomSheetTabsOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));
        }

        ViewCompat.setOnApplyWindowInsetsListener(this, this::onApplyWindowInsets);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        if (lastWindowInsetsCompat != null) {
            dispatchApplyWindowInsetsInternal(child, lastWindowInsetsCompat);
        }
    }

    @Override
    public boolean addFragmentToStack(BaseFragment fragment, int position) {
        if (fragment == null) {
            return false;
        }
        fragment.setParentLayout(this);
        if (position == -1 || position >= fragmentsStack.size()) {
            fragmentsStack.add(fragment);
        } else {
            fragmentsStack.add(position, fragment);
        }
        if (fragmentsStack.get(fragmentsStack.size() - 1) == fragment) {
            containerView.removeAllViews();
            attachView(fragment);
            fragment.onResume();
            updateSystemBars();
        }
        onFragmentStackChanged();
        return true;
    }

    public void setFragmentStack(List<BaseFragment> stack) {
        BaseFragment previous = getLastFragment();
        BaseFragment next = stack == null || stack.isEmpty() ? null : stack.get(stack.size() - 1);
        if (previous != null && previous != next) {
            previous.onPause();
        }
        fragmentsStack.clear();
        if (stack != null) {
            for (BaseFragment fragment : stack) {
                if (fragment != null) {
                    fragment.setParentLayout(this);
                    fragmentsStack.add(fragment);
                }
            }
        }
        showLastFragment();
        onFragmentStackChanged();
    }

    public void setFragmentStackChangedListener(Runnable listener) {
        fragmentStackChangedListener = listener;
    }

    private void onFragmentStackChanged() {
        if (fragmentStackChangedListener != null) {
            fragmentStackChangedListener.run();
        }
    }

    public boolean bringToFront(int position) {
        if (position < 0 || position >= fragmentsStack.size()) return false;
        BaseFragment previous = getLastFragment();
        BaseFragment fragment = fragmentsStack.remove(position);
        fragmentsStack.add(fragment);
        if (previous != null && previous != fragment) previous.onPause();
        showLastFragment();
        onFragmentStackChanged();
        return true;
    }

    public void showLastFragment() {
        BaseFragment fragment = getLastFragment();
        if (fragment == null) return;
        containerView.removeAllViews();
        attachView(fragment);
        fragment.onResume();
        updateSystemBars();
    }

    public boolean removeFragmentFromStack(BaseFragment fragment, boolean allowFinishFragment) {
        boolean wasLast = fragment != null && fragment == getLastFragment();
        if (fragment == null || !fragmentsStack.remove(fragment)) return false;
        fragment.onPause();
        fragment.onFragmentDestroy();
        if (wasLast) showLastFragment();
        onFragmentStackChanged();
        return true;
    }

    public void removeAllFragments() {
        for (BaseFragment fragment : fragmentsStack) {
            fragment.onPause();
            fragment.onFragmentDestroy();
        }
        fragmentsStack.clear();
        containerView.removeAllViews();
        containerViewBack.removeAllViews();
        updateSystemBars();
        onFragmentStackChanged();
    }

    public boolean canHandleBack() {
        BaseFragment currentFragment = fragmentsStack.isEmpty() ? null : fragmentsStack.get(fragmentsStack.size() - 1);
        return bottomSheetTabsOverlay != null && bottomSheetTabsOverlay.isOpened()
                || fragmentsStack.size() > 1
                || currentFragment != null && currentFragment.hasShownSheet();
    }

    public boolean canStartPredictiveBack() {
        if (bottomSheetTabsOverlay != null && bottomSheetTabsOverlay.isOpened()) {
            return false;
        }
        if (animationInProgress || predictiveBackInProgress || predictiveInput || startedTracking || fragmentsStack.size() <= 1) {
            return false;
        }
        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        return !currentFragment.hasShownSheet() && currentFragment.canBeginSlide();
    }

    public boolean handleBackPressed() {
        if (animationInProgress) {
            return true;
        }
        if (fragmentsStack.isEmpty()) {
            return false;
        }
        if (bottomSheetTabsOverlay != null && bottomSheetTabsOverlay.onBackPressed()) {
            return true;
        }
        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        if (!currentFragment.onBackPressed()) {
            return true;
        }
        if (fragmentsStack.size() <= 1) {
            return false;
        }
        closeLastFragment(true, false);
        return true;
    }

    @Override
    public boolean presentFragment(NavigationParams params) {
        BaseFragment fragment = params.fragment;
        boolean forceWithoutAnimation = params.noAnimation;
        boolean removeLast = params.removeLast;
        if (fragment == null || animationInProgress) {
            return false;
        }
        if (fragmentsStack.isEmpty()) {
            return addFragmentToStack(fragment);
        }
        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        currentFragment.onPause();
        fragment.setParentLayout(this);
        fragmentsStack.add(fragment);
        if (removeLast) {
            fragmentsStack.remove(currentFragment);
            currentFragment.onFragmentDestroy();
        }

        if (forceWithoutAnimation || getWidth() == 0) {
            containerView.removeAllViews();
            attachView(fragment);
            fragment.onResume();
            updateSystemBars();
            onFragmentStackChanged();
            return true;
        }

        animationInProgress = true;
        containerViewBack.removeAllViews();
        attachViewTo(fragment, containerViewBack);
        containerViewBack.setVisibility(VISIBLE);

        LayoutContainer temp = containerView;
        containerView = containerViewBack;
        containerViewBack = temp;
        containerView.setAlpha(0.0f);
        containerView.setTranslationX(48.0f);
        bringChildToFront(containerView);
        bringBrowserTabViewsToFront();

        oldFragment = currentFragment;
        newFragment = fragment;
        updateSystemBars();
        startLayoutAnimation(true, true);
        onFragmentStackChanged();
        return true;
    }

    public boolean presentFragmentFromBottom(BaseFragment fragment) {
        if (fragment == null || animationInProgress) {
            return false;
        }
        if (fragmentsStack.isEmpty()) {
            return addFragmentToStack(fragment);
        }

        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        currentFragment.onPause();
        fragment.setParentLayout(this);
        fragmentsStack.add(fragment);

        animationInProgress = true;
        containerViewBack.removeAllViews();
        attachViewTo(fragment, containerViewBack);
        containerViewBack.setVisibility(VISIBLE);

        LayoutContainer temp = containerView;
        containerView = containerViewBack;
        containerViewBack = temp;
        containerView.setAlpha(1.0f);
        containerView.setTranslationX(0.0f);
        containerView.setTranslationY(getBottomPresentationDistance());
        bringChildToFront(containerView);
        bringBrowserTabViewsToFront();

        oldFragment = currentFragment;
        newFragment = fragment;
        updateSystemBars();
        startBottomOpenAnimation();
        onFragmentStackChanged();
        return true;
    }

    @Override
    public void closeLastFragment(boolean animated, boolean forceNoAnimation) {
        if (fragmentsStack.size() <= 1 || animationInProgress) {
            return;
        }
        if (forceNoAnimation || !animated || getWidth() == 0) {
            if (!prepareForMoving()) {
                return;
            }
            onSlideAnimationEnd(false);
            return;
        }
        startLayoutCloseAnimation();
    }

    public boolean closeLastFragmentFromBottom() {
        if (fragmentsStack.size() <= 1 || animationInProgress) {
            return false;
        }

        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        BaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        previousFragment.setParentLayout(this);
        containerViewBack.removeAllViews();
        attachViewTo(previousFragment, containerViewBack);
        containerViewBack.setVisibility(VISIBLE);
        containerViewBack.setAlpha(1.0f);
        containerViewBack.setTranslationX(0.0f);
        containerViewBack.setTranslationY(0.0f);
        previousFragment.onResume();
        bringChildToFront(containerView);
        bringBrowserTabViewsToFront();

        oldFragment = currentFragment;
        newFragment = previousFragment;
        animationInProgress = true;
        startBottomCloseAnimation();
        return true;
    }

    @Override
    public List<BaseFragment> getFragmentStack() {
        return fragmentsStack;
    }

    public void onBackStarted(float touchX, float touchY) {
        if (animationInProgress) {
            if (backAnimator != null) {
                backAnimator.end();
                backAnimator = null;
            } else {
                return;
            }
            if (animationInProgress) {
                return;
            }
        }
        if (predictiveBackInProgress || predictiveInput || startedTracking || fragmentsStack.size() <= 1) {
            return;
        }
        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        if (!currentFragment.onBackPressed() || !currentFragment.canBeginSlide()) {
            return;
        }
        predictiveBackHasProgress = false;
        predictiveBackInProgress = true;
        predictiveInput = true;
        predictiveBackLeft = touchX < getWidth() / 2f;
        predictiveBackY = touchY;
        if (!prepareForMoving()) {
            predictiveBackInProgress = false;
            predictiveInput = false;
            return;
        }
        sendBeginSlideIfNeeded();
    }

    public void onBackProgress(float progress) {
        if (!predictiveInput) {
            return;
        }
        float dx = AndroidUtilities.dp(56) * progress;
        predictiveBackHasProgress = progress > 0;
        containerView.setTranslationX(dx);
        setInnerTranslationX(dx);
    }

    public void onBackCancelled() {
        if (!predictiveInput) {
            return;
        }
        predictiveInput = false;
        animateBackEndAnimation(true);
    }

    public void onBackInvoked() {
        if (!predictiveInput) {
            if (!handleBackPressed()) {
                ((Activity) getContext()).finish();
            }
            return;
        }
        predictiveInput = false;
        animateBackEndAnimation(false);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (bottomSheetTabsOverlay != null && bottomSheetTabsOverlay.isOpened()) {
            return false;
        }
        return animationInProgress || onTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!animationInProgress && !predictiveBackInProgress && fragmentsStack.size() > 1) {
            if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
                BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                if (ev.getX() > AndroidUtilities.dp(24) || !currentFragment.isSwipeBackEnabled(ev)) {
                    maybeStartTracking = false;
                    startedTracking = false;
                    startedTrackingPointerId = -1;
                    beginTrackingSent = false;
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                    containerView.setLayerType(LAYER_TYPE_NONE, null);
                    return false;
                }
                startedTrackingPointerId = ev.getPointerId(0);
                maybeStartTracking = true;
                startedTrackingX = (int) ev.getX();
                startedTrackingY = (int) ev.getY();
                if (velocityTracker != null) {
                    velocityTracker.clear();
                }
            } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.findPointerIndex(startedTrackingPointerId) >= 0) {
                int pointerIndex = ev.findPointerIndex(startedTrackingPointerId);
                float x = ev.getX(pointerIndex);
                float y = ev.getY(pointerIndex);
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                int dx = Math.max(0, (int) (x - startedTrackingX));
                int dy = Math.abs((int) y - startedTrackingY);
                velocityTracker.addMovement(ev);
                if (maybeStartTracking && !startedTracking && dx >= AndroidUtilities.getPixelsInCM(0.4f, true) && Math.abs(dx) / 3 > dy) {
                    BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                    if (currentFragment.canBeginSlide() && findScrollingChild(this, x, y) == null) {
                        startedTrackingX = (int) x;
                        prepareForMoving();
                    } else {
                        maybeStartTracking = false;
                    }
                } else if (startedTracking) {
                    sendBeginSlideIfNeeded();
                    containerView.setTranslationX(dx);
                    setInnerTranslationX(dx);
                }
            } else if (ev != null && ev.findPointerIndex(startedTrackingPointerId) >= 0 && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.addMovement(ev);
                velocityTracker.computeCurrentVelocity(1000);
                BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                if (!startedTracking && ev.getX() <= AndroidUtilities.dp(24) && currentFragment.isSwipeBackEnabled(ev)) {
                    float velX = velocityTracker.getXVelocity();
                    float velY = velocityTracker.getYVelocity();
                    if (velX >= 3500 && velX > Math.abs(velY) && currentFragment.canBeginSlide()) {
                        startedTrackingX = (int) ev.getX();
                        prepareForMoving();
                        sendBeginSlideIfNeeded();
                    }
                }
                if (startedTracking) {
                    float x = containerView.getX();
                    float velX = velocityTracker.getXVelocity();
                    float velY = velocityTracker.getYVelocity();
                    boolean backAnimation = x < containerView.getMeasuredWidth() / 3.0f && (velX < 3500 || Math.abs(velX) < Math.abs(velY));
                    animateBackEndAnimation(backAnimation);
                } else {
                    maybeStartTracking = false;
                    startedTracking = false;
                    layoutToIgnore = null;
                    containerView.setLayerType(LAYER_TYPE_NONE, null);
                }
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
            } else if (ev == null) {
                maybeStartTracking = false;
                startedTracking = false;
                startedTrackingPointerId = -1;
                beginTrackingSent = false;
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                layoutToIgnore = null;
                containerView.setLayerType(LAYER_TYPE_NONE, null);
            }
            return startedTracking;
        }
        return false;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        onTouchEvent(null);
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        withShadow = true;
        super.dispatchDraw(canvas);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        int width = getWidth();
        int translationX = (int) innerTranslationX;
        int clipLeft = 0;
        int clipRight = width;

        if (child == containerViewBack) {
            clipRight = translationX + AndroidUtilities.dp(1);
        } else if (child == containerView) {
            clipLeft = translationX;
        }

        int restoreCount = canvas.save();
        if (child != bottomSheetTabs && child != bottomSheetTabsOverlay && bottomSheetTabsClip != null) {
            bottomSheetTabsClip.clip(canvas, withShadow, isKeyboardVisible, getWidth(), (int) getY() + getHeight(), 1.0f);
            withShadow = false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && translationX != 0) {
            if (child == containerView) {
                WindowInsets insets = getRootWindowInsets();
                if (insets != null) {
                    roundedClipRect.set(translationX, 0, translationX + child.getWidth(), getHeight());
                    if (newBackTransitions()) {
                        float progress = clamp01(translationX / AndroidUtilities.dpf2(56));
                        float scale = lerp(1.0f, 0.90f, progress);
                        float dx = translationX > AndroidUtilities.dp(56) && !animationInProgress && predictiveBackInProgress ? translationX : clamp(translationX, 0, AndroidUtilities.dp(56));
                        canvas.translate(-dx, 0);
                        clipRight += dx;
                        float pivotX = predictiveBackLeft ? roundedClipRect.right - AndroidUtilities.dp(82) : roundedClipRect.left + AndroidUtilities.dp(82);
                        canvas.scale(scale, scale, pivotX, predictiveBackY);
                    }

                    RoundedCorner topLeft = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
                    RoundedCorner topRight = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
                    RoundedCorner bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);
                    RoundedCorner bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
                    radii[0] = radii[1] = topLeft == null ? 0 : topLeft.getRadius();
                    radii[2] = radii[3] = topRight == null ? 0 : topRight.getRadius();
                    radii[4] = radii[5] = bottomRight == null ? 0 : bottomRight.getRadius();
                    radii[6] = radii[7] = bottomLeft == null ? 0 : bottomLeft.getRadius();

                    clipPath.rewind();
                    clipPath.addRoundRect(roundedClipRect, radii, Path.Direction.CW);
                    canvas.clipPath(clipPath);
                }
            } else if (child == containerViewBack) {
                WindowInsets insets = getRootWindowInsets();
                if (insets != null) {
                    RoundedCorner topLeft = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT);
                    RoundedCorner bottomLeft = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT);
                    clipRight += Math.max(topLeft == null ? 0 : topLeft.getRadius(), bottomLeft == null ? 0 : bottomLeft.getRadius());
                    if (newBackTransitions()) {
                        clipRight = width;
                    }
                }
            }
        }

        int childRestoreCount = canvas.save();
        if (!animationInProgress) {
            canvas.clipRect(clipLeft, 0, clipRight, getHeight());
        }
        float bottomTabsTranslationY = 0f;
        if (child instanceof LayoutContainer && bottomSheetTabs != null) {
            bottomTabsTranslationY = getBottomTabsHeight(false) - getBottomTabsHeight(true);
        }
        if (bottomTabsTranslationY != 0f) {
            canvas.translate(0, bottomTabsTranslationY);
        }
        boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(childRestoreCount);

        if (translationX != 0 && child == containerViewBack) {
            int widthOffset = width - translationX;
            float opacity = clamp(widthOffset / (float) Math.max(1, width), 0f, 0.8f);
            scrimPaint.setColor(Color.argb((int) (120 * opacity), 0x00, 0x00, 0x00));
            canvas.drawRect(clipLeft, 0, clipRight, getHeight() * 1.5f, scrimPaint);
        }
        canvas.restoreToCount(restoreCount);
        return result;
    }

    public void setInnerTranslationX(float value) {
        innerTranslationX = value;
        invalidate();
    }

    public float getInnerTranslationX() {
        return innerTranslationX;
    }

    private boolean prepareForMoving() {
        if (fragmentsStack.size() <= 1) {
            return false;
        }
        maybeStartTracking = false;
        startedTracking = true;
        layoutToIgnore = containerViewBack;
        containerViewBack.setVisibility(VISIBLE);
        beginTrackingSent = false;

        BaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        previousFragment.setParentLayout(this);
        containerViewBack.removeAllViews();
        attachViewTo(previousFragment, containerViewBack);
        containerViewBack.setAlpha(1.0f);
        containerViewBack.setTranslationX(0);
        previousFragment.onResume();

        containerView.setLayerType(LAYER_TYPE_HARDWARE, null);
        View focus = findFocus();
        if (focus != null) {
            AndroidUtilities.hideKeyboard(focus);
        }
        return true;
    }

    private void sendBeginSlideIfNeeded() {
        if (beginTrackingSent || fragmentsStack.isEmpty()) {
            return;
        }
        View focus = findFocus();
        if (focus != null) {
            AndroidUtilities.hideKeyboard(focus);
        }
        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        currentFragment.onBeginSlide();
        beginTrackingSent = true;
    }

    private void animateBackEndAnimation(boolean backAnimation) {
        BaseFragment currentFragment = fragmentsStack.isEmpty() ? null : fragmentsStack.get(fragmentsStack.size() - 1);
        if (currentFragment == null) {
            return;
        }

        if (backAnimator != null) {
            backAnimator.cancel();
        }

        float x = containerView.getX();
        float measuredWidth = Math.max(1, containerView.getMeasuredWidth());
        AnimatorSet animatorSet = new AnimatorSet();
        float distToMove;
        if (!backAnimation) {
            distToMove = Math.abs(measuredWidth - x);
            int duration = Math.max((int) (200.0f / measuredWidth * distToMove), newBackTransitions() ? 380 : 50);
            animatorSet.playTogether(
                ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, measuredWidth + (predictiveBackInProgress ? AndroidUtilities.dp(56) : 0)).setDuration(duration),
                ObjectAnimator.ofFloat(this, "innerTranslationX", measuredWidth).setDuration(duration)
            );
        } else {
            distToMove = x;
            int duration = Math.max((int) (320.0f / measuredWidth * distToMove), newBackTransitions() ? 320 : 120);
            animatorSet.playTogether(
                ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, 0).setDuration(duration),
                ObjectAnimator.ofFloat(this, "innerTranslationX", 0.0f).setDuration(duration)
            );
        }
        if (newBackTransitions()) {
            animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        }
        animatorSet.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
                predictiveBackInProgress = false;
                containerView.setAlpha(1.0f);
                onSlideAnimationEnd(true);
                backAnimator = null;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) {
                    return;
                }
                predictiveBackInProgress = false;
                containerView.setAlpha(1.0f);
                onSlideAnimationEnd(backAnimation);
                backAnimator = null;
            }
        });
        backAnimator = animatorSet;
        animationInProgress = true;
        animatorSet.start();
    }

    private void startLayoutCloseAnimation() {
        if (!prepareForMoving()) {
            return;
        }
        BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);

        LayoutContainer temp = containerView;
        containerView = containerViewBack;
        containerViewBack = temp;

        animationInProgress = true;
        containerViewBack.setAlpha(1.0f);
        containerViewBack.setTranslationX(0.0f);
        oldFragment = currentFragment;
        newFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        startLayoutAnimation(false, true);
    }

    private void startBottomOpenAnimation() {
        if (bottomSheetAnimator != null) {
            bottomSheetAnimator.cancel();
        }
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, 0.0f));
        animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animatorSet.setDuration(320);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) {
                    return;
                }
                containerView.setTranslationY(0.0f);
                containerViewBack.removeAllViews();
                containerViewBack.setVisibility(INVISIBLE);
                bringBrowserTabViewsToFront();
                if (newFragment != null) {
                    newFragment.onResume();
                }
                oldFragment = null;
                newFragment = null;
                animationInProgress = false;
                runDeferredThemeAnimation();
                bottomSheetAnimator = null;
            }
        });
        bottomSheetAnimator = animatorSet;
        animatorSet.start();
    }

    private void startBottomCloseAnimation() {
        if (bottomSheetAnimator != null) {
            bottomSheetAnimator.cancel();
        }
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(containerView, View.TRANSLATION_Y, getBottomPresentationDistance()));
        animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animatorSet.setDuration(250);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) {
                    return;
                }
                if (fragmentsStack.size() >= 2 && oldFragment != null) {
                    fragmentsStack.remove(fragmentsStack.size() - 1);
                    oldFragment.onPause();
                    oldFragment.onFragmentDestroy();
                }
                containerView.removeAllViews();
                containerView.setTranslationY(0.0f);

                LayoutContainer temp = containerView;
                containerView = containerViewBack;
                containerViewBack = temp;
                containerViewBack.removeAllViews();
                containerViewBack.setVisibility(INVISIBLE);
                containerViewBack.setAlpha(1.0f);
                containerViewBack.setTranslationX(0.0f);
                containerViewBack.setTranslationY(0.0f);
                bringChildToFront(containerView);
                bringBrowserTabViewsToFront();

                oldFragment = null;
                newFragment = null;
                animationInProgress = false;
                runDeferredThemeAnimation();
                bottomSheetAnimator = null;
            }
        });
        bottomSheetAnimator = animatorSet;
        animatorSet.start();
    }

    private float getBottomPresentationDistance() {
        return Math.max(getHeight(), AndroidUtilities.displaySize.y);
    }

    public BottomSheetTabs getBottomSheetTabs() {
        return bottomSheetTabs;
    }

    public BottomSheetTabsOverlay getBottomSheetTabsOverlay() {
        return bottomSheetTabsOverlay;
    }

    public void setIsSheet(boolean value) {
        sheetLayout = value;
    }

    public boolean isSheet() {
        return sheetLayout;
    }

    public void setWindow(Window window) {
        attachedWindow = window;
    }

    public Window getWindow() {
        return attachedWindow;
    }

    public void setDrawerLayoutContainer(DrawerLayoutContainer drawerLayoutContainer) {
        this.drawerLayoutContainer = drawerLayoutContainer;
        if (drawerLayoutContainer != null) {
            drawerLayoutContainer.setParentActionBarLayout(this);
        }
    }

    public DrawerLayoutContainer getDrawerLayoutContainer() {
        return drawerLayoutContainer;
    }

    public void onResume() {
        BaseFragment fragment = getLastFragment();
        if (lifecycleResumed) {
            if (fragment != null && fragment.getActionBar() != null) {
                fragment.getActionBar().onResume();
            }
            updateSystemBars();
            return;
        }
        lifecycleResumed = true;
        if (fragment != null) {
            fragment.onResume();
            if (fragment.getActionBar() != null) {
                fragment.getActionBar().onResume();
            }
        }
        updateSystemBars();
    }

    public void onPause() {
        if (!lifecycleResumed) {
            return;
        }
        lifecycleResumed = false;
        BaseFragment fragment = getLastFragment();
        if (fragment != null) {
            if (fragment.getActionBar() != null) {
                fragment.getActionBar().onPause();
            }
            fragment.onPause();
        }
    }

    public void onUserLeaveHint() {
        BaseFragment fragment = getLastFragment();
        if (fragment != null) fragment.onUserLeaveHint();
    }

    public void onLowMemory() {
        BaseFragment fragment = getLastFragment();
        if (fragment != null) fragment.onLowMemory();
    }

    public void onConfigurationChanged(Configuration configuration) {
        requestLayout();
        ViewCompat.requestApplyInsets(this);
        BaseFragment fragment = getLastFragment();
        if (fragment != null) {
            fragment.onConfigurationChanged(configuration);
        }
    }

    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        return super.dispatchKeyEventPreIme(event);
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            BaseFragment fragment = getLastFragment();
            if (fragment != null && fragment.getActionBar() != null) {
                fragment.getActionBar().onMenuButtonPressed();
                return true;
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    public void setNavigationBarColor(int color) {
        lastNavigationBarColor = color;
        if (bottomSheetTabs != null) {
            bottomSheetTabs.setNavigationBarColor(color, !(startedTracking || animationInProgress));
        }
        Window target = attachedWindow != null ? attachedWindow : getContext() instanceof Activity ? ((Activity) getContext()).getWindow() : null;
        if (target != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            target.setNavigationBarColor(color);
        }
    }

    public int getNavigationBarColor() {
        return lastNavigationBarColor;
    }

    /** Upstream DrawerLayoutContainer keyboard path; NativeDo has no story viewer yet. */
    public boolean storyViewerAttached() {
        return false;
    }

    /** Upstream drawer intercept gate; NativeDo has no slide transition yet. */
    public boolean checkTransitionAnimation() {
        return false;
    }

    public BaseFragment getLastFragment() {
        return fragmentsStack.isEmpty() ? null : fragmentsStack.get(fragmentsStack.size() - 1);
    }

    public void rebuildAllFragmentViews(boolean last) {
        rebuildAllFragmentViews(last, true);
    }

    public void rebuildAllFragmentViews(boolean last, boolean showLastAfter) {
        int start = last ? Math.max(0, fragmentsStack.size() - 1) : 0;
        for (int i = start; i < fragmentsStack.size(); i++) {
            BaseFragment fragment = fragmentsStack.get(i);
            fragment.clearViews();
            fragment.setParentLayout(this);
        }
        containerView.removeAllViews();
        containerViewBack.removeAllViews();
        if (showLastAfter) {
            showLastFragment();
        } else {
            updateSystemBars();
        }
    }

    public void rebuildLogout() {
        rebuildAllFragmentViews(false, true);
    }

    void setRootDismissRunnable(Runnable rootDismissRunnable) {
        this.rootDismissRunnable = rootDismissRunnable;
    }

    public boolean dismissRootSheet() {
        if (rootDismissRunnable == null) {
            return false;
        }
        rootDismissRunnable.run();
        return true;
    }

    @Override
    public int getBottomTabsHeight(boolean animated) {
        return bottomSheetTabs == null ? 0 : bottomSheetTabs.getHeight(animated);
    }

    private void relayout() {
        requestLayout();
        containerView.requestLayout();
        containerViewBack.requestLayout();
    }

    private void dispatchApplyWindowInsetsInternal(View child, WindowInsetsCompat insets) {
        if (child == null || !(child.getLayoutParams() instanceof MarginLayoutParams)) {
            return;
        }
        Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        Insets systemWithImeInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                        | WindowInsetsCompat.Type.ime()
                        | WindowInsetsCompat.Type.displayCutout()
        );
        MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        if (child == bottomSheetTabs) {
            if (lp.bottomMargin != systemInsets.bottom) {
                lp.bottomMargin = systemInsets.bottom;
                child.requestLayout();
            }
            return;
        }
        if (child instanceof BaseFragment.AttachedSheetWindow) {
            if (lp.bottomMargin != 0) {
                lp.bottomMargin = 0;
                child.requestLayout();
            }
            ViewCompat.dispatchApplyWindowInsets(child, insets);
            return;
        }
        if (child instanceof LayoutContainer) {
            LayoutContainer layoutContainer = (LayoutContainer) child;
            int bottomTabsHeight = getBottomTabsHeight(false);
            int tabsInset = bottomTabsHeight > 0 ? systemInsets.bottom + bottomTabsHeight : 0;
            if (layoutContainer.isSupportEdgeToEdge) {
                if (lp.bottomMargin != tabsInset) {
                    lp.bottomMargin = tabsInset;
                    child.requestLayout();
                }
                WindowInsetsCompat consumed = insets.inset(0, 0, 0, lp.bottomMargin);
                ViewCompat.dispatchApplyWindowInsets(child, consumed);
            } else {
                int legacySystemInset = Math.max(tabsInset, systemWithImeInsets.bottom);
                if (lp.bottomMargin != legacySystemInset) {
                    lp.bottomMargin = legacySystemInset;
                    child.requestLayout();
                }
                ViewCompat.dispatchApplyWindowInsets(child, WindowInsetsCompat.CONSUMED);
            }
        }
    }

    private WindowInsetsCompat onApplyWindowInsets(View view, WindowInsetsCompat insets) {
        lastWindowInsetsCompat = insets;
        isKeyboardVisible = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0;
        for (int i = 0, count = getChildCount(); i < count; i++) {
            dispatchApplyWindowInsetsInternal(getChildAt(i), insets);
        }
        return WindowInsetsCompat.CONSUMED;
    }

    @Keep
    public void setThemeAnimationValue(float value) {
        themeAnimationValue = value;
        for (int j = 0, N = themeAnimatorDescriptions.size(); j < N; j++) {
            ArrayList<ThemeDescription> descriptions = themeAnimatorDescriptions.get(j);
            int[] startColors = animateStartColors.get(j);
            int[] endColors = animateEndColors.get(j);
            int rE, gE, bE, aE, rS, gS, bS, aS, a, r, g, b;
            for (int i = 0, N2 = descriptions.size(); i < N2; i++) {
                rE = Color.red(endColors[i]);
                gE = Color.green(endColors[i]);
                bE = Color.blue(endColors[i]);
                aE = Color.alpha(endColors[i]);

                rS = Color.red(startColors[i]);
                gS = Color.green(startColors[i]);
                bS = Color.blue(startColors[i]);
                aS = Color.alpha(startColors[i]);

                a = Math.min(255, (int) (aS + (aE - aS) * value));
                r = Math.min(255, (int) (rS + (rE - rS) * value));
                g = Math.min(255, (int) (gS + (gE - gS) * value));
                b = Math.min(255, (int) (bS + (bE - bS) * value));
                int color = Color.argb(a, r, g, b);
                ThemeDescription description = descriptions.get(i);
                description.setAnimatedColor(color);
                description.setColor(color, false, false);
            }
        }
        for (int j = 0, N = themeAnimatorDelegate.size(); j < N; j++) {
            ThemeDescription.ThemeDescriptionDelegate delegate = themeAnimatorDelegate.get(j);
            if (delegate != null) {
                delegate.didSetColor();
                delegate.onAnimationProgress(value);
            }
        }
        if (presentingFragmentDescriptions != null) {
            for (int i = 0, N = presentingFragmentDescriptions.size(); i < N; i++) {
                ThemeDescription description = presentingFragmentDescriptions.get(i);
                int key = description.getCurrentKey();
                description.setColor(Theme.getColor(key, description.resourcesProvider), false, false);
            }
        }
        if (animationProgressListener != null) {
            animationProgressListener.setProgress(value);
        }
        globallyUpdateColors(this);
    }

    @Keep
    @Override
    public float getThemeAnimationValue() {
        return themeAnimationValue;
    }

    private void addStartDescriptions(ArrayList<ThemeDescription> descriptions) {
        if (descriptions == null) {
            return;
        }
        themeAnimatorDescriptions.add(descriptions);
        int[] startColors = new int[descriptions.size()];
        animateStartColors.add(startColors);
        for (int a = 0, N = descriptions.size(); a < N; a++) {
            ThemeDescription description = descriptions.get(a);
            startColors[a] = description.getSetColor();
            ThemeDescription.ThemeDescriptionDelegate delegate = description.setDelegateDisabled();
            if (delegate != null && !themeAnimatorDelegate.contains(delegate)) {
                themeAnimatorDelegate.add(delegate);
            }
        }
    }

    private void addEndDescriptions(ArrayList<ThemeDescription> descriptions) {
        if (descriptions == null) {
            return;
        }
        int[] endColors = new int[descriptions.size()];
        animateEndColors.add(endColors);
        for (int a = 0, N = descriptions.size(); a < N; a++) {
            endColors[a] = descriptions.get(a).getSetColor();
        }
    }

    @Override
    public void animateThemedValues(ThemeAnimationSettings settings, Runnable onDone) {
        if (animationInProgress || startedTracking) {
            animateThemeAfterAnimation = true;
            animateSetThemeAfterAnimation = settings.theme;
            animateSetThemeNightAfterAnimation = settings.nightTheme;
            animateSetThemeAccentIdAfterAnimation = settings.accentId;
            animateSetThemeAfterAnimationApply = settings.applyTrulyTheme;
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        if (themeAnimatorSet != null) {
            themeAnimatorSet.cancel();
            themeAnimatorSet = null;
        }
        final int fragmentCount = settings.onlyTopFragment ? 1 : fragmentsStack.size();
        Runnable next = () -> {
            boolean startAnimation = false;
            for (int i = 0; i < fragmentCount; i++) {
                BaseFragment fragment;
                if (i == 0) {
                    fragment = getLastFragment();
                } else {
                    continue;
                }
                if (fragment != null) {
                    startAnimation = true;
                    if (settings.resourcesProvider != null) {
                        if (messageDrawableOutStart == null) {
                            messageDrawableOutStart = new MessageDrawable(MessageDrawable.TYPE_TEXT, true, false, startColorsProvider);
                            messageDrawableOutStart.isCrossfadeBackground = true;
                            messageDrawableOutMediaStart = new MessageDrawable(MessageDrawable.TYPE_MEDIA, true, false, startColorsProvider);
                            messageDrawableOutMediaStart.isCrossfadeBackground = true;
                        }
                        startColorsProvider.saveColors(settings.resourcesProvider);
                    }
                    ArrayList<ThemeDescription> descriptions = fragment.getThemeDescriptions();
                    addStartDescriptions(descriptions);
                    if (fragment.visibleDialog instanceof BottomSheet) {
                        addStartDescriptions(((BottomSheet) fragment.visibleDialog).getThemeDescriptions());
                    } else if (fragment.visibleDialog instanceof AlertDialog) {
                        addStartDescriptions(((AlertDialog) fragment.visibleDialog).getThemeDescriptions());
                    }
                    if (i == 0 && settings.afterStartDescriptionsAddedRunnable != null) {
                        settings.afterStartDescriptionsAddedRunnable.run();
                    }
                    addEndDescriptions(descriptions);
                    if (fragment.visibleDialog instanceof BottomSheet) {
                        addEndDescriptions(((BottomSheet) fragment.visibleDialog).getThemeDescriptions());
                    } else if (fragment.visibleDialog instanceof AlertDialog) {
                        addEndDescriptions(((AlertDialog) fragment.visibleDialog).getThemeDescriptions());
                    }
                }
            }
            if (startAnimation) {
                if (!settings.onlyTopFragment) {
                    int count = fragmentsStack.size() - 1;
                    for (int a = 0; a < count; a++) {
                        BaseFragment fragment = fragmentsStack.get(a);
                        fragment.clearViews();
                        fragment.setParentLayout(this);
                    }
                }
                if (settings.instant) {
                    setThemeAnimationValue(1.0f);
                    clearThemeAnimationState();
                    if (settings.afterAnimationRunnable != null) {
                        settings.afterAnimationRunnable.run();
                    }
                    updateShellBackgroundColor();
                    updateSystemBars();
                    if (onDone != null) {
                        onDone.run();
                    }
                    return;
                }
                Theme.setAnimatingColor(true);
                setThemeAnimationValue(0f);
                if (settings.beforeAnimationRunnable != null) {
                    settings.beforeAnimationRunnable.run();
                }
                animationProgressListener = settings.animationProgress;
                if (animationProgressListener != null) {
                    animationProgressListener.setProgress(0);
                }
                notificationsLocker.lock();
                themeAnimatorSet = new AnimatorSet();
                themeAnimatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        notificationsLocker.unlock();
                        if (animation.equals(themeAnimatorSet)) {
                            clearThemeAnimationState();
                            Theme.setAnimatingColor(false);
                            themeAnimatorSet = null;
                            updateShellBackgroundColor();
                            updateSystemBars();
                            if (settings.afterAnimationRunnable != null) {
                                settings.afterAnimationRunnable.run();
                            }
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (animation.equals(themeAnimatorSet)) {
                            clearThemeAnimationState();
                            Theme.setAnimatingColor(false);
                            themeAnimatorSet = null;
                            updateShellBackgroundColor();
                            updateSystemBars();
                            if (settings.afterAnimationRunnable != null) {
                                settings.afterAnimationRunnable.run();
                            }
                        }
                    }
                });
                themeAnimatorSet.playTogether(ObjectAnimator.ofFloat(this, "themeAnimationValue", 0.0f, 1.0f));
                themeAnimatorSet.setDuration(settings.duration);
                themeAnimatorSet.start();
            }
            if (onDone != null) {
                onDone.run();
            }
        };
        if (fragmentCount >= 1 && settings.applyTheme && settings.applyTrulyTheme) {
            if (settings.accentId != -1 && settings.theme != null) {
                settings.theme.setCurrentAccentId(settings.accentId);
                Theme.saveThemeAccents(settings.theme, true, false, true, false);
            }
            if (onDone == null) {
                Theme.applyTheme(settings.theme, settings.nightTheme);
                next.run();
            } else {
                Theme.applyThemeInBackground(settings.theme, settings.nightTheme, () -> AndroidUtilities.runOnUIThread(next));
            }
        } else {
            next.run();
        }
    }

    private void clearThemeAnimationState() {
        themeAnimatorDescriptions.clear();
        animateStartColors.clear();
        animateEndColors.clear();
        themeAnimatorDelegate.clear();
        presentingFragmentDescriptions = null;
        animationProgressListener = null;
    }

    private void globallyUpdateColors(ViewGroup parent) {
        if (parent == null) return;
        for (int i = 0; i < parent.getChildCount(); ++i) {
            View child = parent.getChildAt(i);
            if (child instanceof Theme.Colorable) {
                ((Theme.Colorable) child).updateColors();
            }
            if (child instanceof ViewGroup) {
                globallyUpdateColors((ViewGroup) child);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateShellBackgroundColor();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        maybeStartTracking = false;
        startedTracking = false;
        startedTrackingPointerId = -1;
        beginTrackingSent = false;
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void updateShellBackgroundColor() {
        int shellColor = Theme.getColor(Theme.key_iv_background);
        setBackgroundColor(shellColor);
        if (bottomSheetTabs != null) {
            bottomSheetTabs.setNavigationBarColor(shellColor, false);
        }
    }

    public void updateSystemBars() {
        Window window = attachedWindow != null ? attachedWindow : getContext() instanceof Activity ? ((Activity) getContext()).getWindow() : null;
        if (window == null) {
            return;
        }
        BaseFragment fragment = getLastFragment();
        boolean lightStatusBar = fragment == null || fragment.isLightStatusBar();
        boolean forceTransparentStatusBar = fragment != null && fragment.hasShownSheet();
        int navigationBarColor = fragment == null ? Theme.getColor(Theme.key_windowBackgroundGray) : fragment.getNavigationBarColor();
        AndroidUtilities.setLightStatusBar(window, lightStatusBar, forceTransparentStatusBar);
        AndroidUtilities.setLightNavigationBar(window, AndroidUtilities.computePerceivedBrightness(navigationBarColor) >= .721f);
        setNavigationBarColor(navigationBarColor);
    }

    private void bringBrowserTabViewsToFront() {
        if (bottomSheetTabsOverlay != null) {
            bringChildToFront(bottomSheetTabsOverlay);
        }
    }

    private void startLayoutAnimation(final boolean open, final boolean first) {
        if (first) {
            animationProgress = 0.0f;
            lastFrameTime = System.nanoTime() / 1000000;
        }
        AndroidUtilities.runOnUIThread(animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (animationRunnable != this) {
                    return;
                }
                animationRunnable = null;
                long newTime = System.nanoTime() / 1000000;
                long dt = newTime - lastFrameTime;
                if (dt > 40 && first) {
                    dt = 0;
                } else if (dt > 18) {
                    dt = 18;
                }
                lastFrameTime = newTime;
                animationProgress += dt / 150.0f;
                if (animationProgress > 1.0f) {
                    animationProgress = 1.0f;
                }
                float interpolated = decelerateInterpolator.getInterpolation(animationProgress);
                if (open) {
                    float clampedInterpolated = clamp01(interpolated);
                    containerView.setAlpha(clampedInterpolated);
                    containerView.setTranslationX(AndroidUtilities.dp(48) * (1.0f - interpolated));
                } else {
                    float clampedReverseInterpolated = clamp01(1.0f - interpolated);
                    containerViewBack.setAlpha(clampedReverseInterpolated);
                    containerViewBack.setTranslationX(AndroidUtilities.dp(48) * interpolated);
                }
                if (animationProgress < 1.0f) {
                    startLayoutAnimation(open, false);
                } else {
                    onAnimationEndCheck(open);
                }
            }
        });
    }

    private void onAnimationEndCheck(boolean open) {
        AndroidUtilities.cancelRunOnUIThread(animationRunnable);
        animationRunnable = null;
        if (open) {
            containerView.setAlpha(1.0f);
            containerView.setTranslationX(0.0f);
            containerViewBack.removeAllViews();
            containerViewBack.setVisibility(INVISIBLE);
            if (newFragment != null) {
                newFragment.onResume();
            }
        } else {
            if (oldFragment != null && fragmentsStack.size() > 1) {
                fragmentsStack.remove(fragmentsStack.size() - 1);
                oldFragment.onPause();
                oldFragment.onFragmentDestroy();
            }
            containerViewBack.removeAllViews();
            containerViewBack.setAlpha(1.0f);
            containerViewBack.setTranslationX(0.0f);
            containerViewBack.setVisibility(INVISIBLE);
            containerViewBack.setLayerType(LAYER_TYPE_NONE, null);
            startedTracking = false;
            maybeStartTracking = false;
            beginTrackingSent = false;
            layoutToIgnore = null;
            setInnerTranslationX(0);
        }
        oldFragment = null;
        newFragment = null;
        animationInProgress = false;
        runDeferredThemeAnimation();
        updateSystemBars();
        onFragmentStackChanged();
    }

    private void onSlideAnimationEnd(boolean backAnimation) {
        if (!backAnimation) {
            if (fragmentsStack.size() >= 2) {
                BaseFragment currentFragment = fragmentsStack.remove(fragmentsStack.size() - 1);
                BaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                currentFragment.onPause();
                currentFragment.onFragmentDestroy();

                LayoutContainer temp = containerView;
                containerView = containerViewBack;
                containerViewBack = temp;
                bringChildToFront(containerView);
                previousFragment.onResume();
            }
        } else if (fragmentsStack.size() >= 2) {
            BaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 2);
            previousFragment.onPause();
            BaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            currentFragment.onResume();
        }
        containerViewBack.removeAllViews();
        containerViewBack.setVisibility(INVISIBLE);
        startedTracking = false;
        animationInProgress = false;
        runDeferredThemeAnimation();
        containerView.setTranslationX(0);
        containerViewBack.setTranslationX(0);
        containerView.setLayerType(LAYER_TYPE_NONE, null);
        setInnerTranslationX(0);
        predictiveInput = false;
        predictiveBackInProgress = false;
        predictiveBackHasProgress = false;
        layoutToIgnore = null;
        updateSystemBars();
        onFragmentStackChanged();
    }

    private void runDeferredThemeAnimation() {
        if (!animateThemeAfterAnimation) {
            return;
        }
        ThemeAnimationSettings settings = new ThemeAnimationSettings(
                animateSetThemeAfterAnimation,
                animateSetThemeAccentIdAfterAnimation,
                animateSetThemeNightAfterAnimation,
                false
        );
        settings.applyTheme = settings.applyTrulyTheme = animateSetThemeAfterAnimationApply;
        animateSetThemeAfterAnimation = null;
        animateThemeAfterAnimation = false;
        animateThemedValues(settings, null);
    }

    private void attachView(BaseFragment fragment) {
        attachViewTo(fragment, containerView);
    }

    private void attachViewTo(BaseFragment fragment, FrameLayout container) {
        fragment.setParentLayout(this);
        View contentView = fragment.getFragmentView();
        if (contentView == null) {
            contentView = fragment.createView(getContext());
            fragment.setFragmentView(contentView);
        }
        if (contentView != null && fragment.isSupportEdgeToEdge() && fragment.drawEdgeNavigationBar()) {
            ViewCompat.setOnApplyWindowInsetsListener(contentView, fragment::onInsetsInternal);
        }
        removeFromParent(contentView);

        ActionBar actionBar = fragment.getActionBar();
        if (actionBar != null && actionBar.shouldAddToContainer()) {
            removeFromParent(actionBar);
        }

        FrameLayout wrapper = new FrameLayout(getContext());
        wrapper.addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START));
        // setPhotoViewerScaleView(wrapper) is used by hosts as PlaceProviderObject.parentView
        // (= clip target). LayoutContainer positions the wrapper below
        // the ActionBar, so its getLocationInWindow correctly excludes the ActionBar
        // strip from the morph view's clip — preventing the closing image from
        // painting over the host ActionBar (user complaint #2).
        // For host zoom (PlaceProviderObject.parentScaleView) the host should instead
        // pass the LayoutContainer / fragment chrome that includes the ActionBar so
        // the ActionBar visually follows the open/close zoom (user complaint #3).
        fragment.setPhotoViewerScaleView(wrapper);

        FrameLayout.LayoutParams contentLayoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.START);
        container.addView(wrapper, contentLayoutParams);
        if (container instanceof LayoutContainer) {
            ((LayoutContainer) container).setShouldHandleBottomInsets(fragment.isSupportEdgeToEdge());
        }
        if (actionBar != null && actionBar.shouldAddToContainer()) {
            container.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START));
        }
    }

    private void removeFromParent(View view) {
        if (view == null) {
            return;
        }
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private boolean newBackTransitions() {
        return predictiveBackInProgress && predictiveBackHasProgress;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp01(float value) {
        return clamp(value, 0f, 1f);
    }

    private float lerp(float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    private View findScrollingChild(ViewGroup parent, float x, float y) {
        int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            if (child == layoutToIgnore || child.getVisibility() != View.VISIBLE) {
                continue;
            }
            child.getHitRect(rect);
            if (rect.contains((int) x, (int) y)) {
                if (child.canScrollHorizontally(-1)) {
                    return child;
                } else if (child instanceof ViewGroup) {
                    View scrollingChild = findScrollingChild((ViewGroup) child, x - rect.left, y - rect.top);
                    if (scrollingChild != null) {
                        return scrollingChild;
                    }
                }
            }
        }
        return null;
    }
}
