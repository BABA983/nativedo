package org.telegram.ui.ActionBar;

import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.List;

public interface INavigationLayout {

    boolean presentFragment(NavigationParams params);

    boolean addFragmentToStack(BaseFragment fragment, int position);

    void closeLastFragment(boolean animated, boolean forceNoAnimation);

    void animateThemedValues(ThemeAnimationSettings settings, Runnable onDone);

    float getThemeAnimationValue();

    List<BaseFragment> getFragmentStack();

    default boolean addFragmentToStack(BaseFragment fragment) {
        return addFragmentToStack(fragment, -1);
    }

    default boolean presentFragment(BaseFragment fragment) {
        return presentFragment(new NavigationParams(fragment));
    }

    default void closeLastFragment(boolean animated) {
        closeLastFragment(animated, false);
    }

    default void animateThemedValues(Theme.ThemeInfo theme, int accentId, boolean nightTheme, boolean instant) {
        animateThemedValues(new ThemeAnimationSettings(theme, accentId, nightTheme, instant), null);
    }

    default void animateThemedValues(Theme.ThemeInfo theme, int accentId, boolean nightTheme, boolean instant, Runnable onDone) {
        animateThemedValues(new ThemeAnimationSettings(theme, accentId, nightTheme, instant), onDone);
    }

    default int getBottomTabsHeight(boolean animated) {
        return 0;
    }

    default android.widget.FrameLayout getOverlayContainerView() {
        return null;
    }

    default boolean checkTransitionAnimation() {
        return false;
    }

    default boolean storyViewerAttached() {
        return false;
    }

    enum BackButtonState {
        BACK,
        MENU
    }

    interface IBackButtonDrawable {
        BackButtonState getBackButtonState();
    }

    class NavigationParams {
        public BaseFragment fragment;
        public boolean removeLast;
        public boolean noAnimation;
        public boolean checkPresentFromDelegate = true;
        public boolean preview;
        public boolean needDelayWithoutAnimation;

        public boolean isFromDelay;
        public boolean delayDone;

        public NavigationParams(BaseFragment fragment) {
            this.fragment = fragment;
        }

        public NavigationParams setRemoveLast(boolean removeLast) {
            this.removeLast = removeLast;
            return this;
        }

        public NavigationParams setNoAnimation(boolean noAnimation) {
            this.noAnimation = noAnimation;
            return this;
        }

        public NavigationParams setCheckPresentFromDelegate(boolean checkPresentFromDelegate) {
            this.checkPresentFromDelegate = checkPresentFromDelegate;
            return this;
        }

        public NavigationParams setPreview(boolean preview) {
            this.preview = preview;
            return this;
        }

        public NavigationParams setNeedDelayWithoutAnimation(boolean needDelayWithoutAnimation) {
            this.needDelayWithoutAnimation = needDelayWithoutAnimation;
            return this;
        }
    }

    class ThemeAnimationSettings {
        public final Theme.ThemeInfo theme;
        public final int accentId;
        public final boolean nightTheme;
        public final boolean instant;
        public boolean onlyTopFragment;
        public boolean applyTheme = true;
        public boolean applyTrulyTheme = true;
        public Runnable afterStartDescriptionsAddedRunnable;
        public Runnable beforeAnimationRunnable;
        public Runnable afterAnimationRunnable;
        public onAnimationProgress animationProgress;
        public long duration = 200;
        public Theme.ResourcesProvider resourcesProvider;

        public ThemeAnimationSettings(Theme.ThemeInfo theme, int accentId, boolean nightTheme, boolean instant) {
            this.theme = theme;
            this.accentId = accentId;
            this.nightTheme = nightTheme;
            this.instant = instant;
        }

        public interface onAnimationProgress {
            void setProgress(float p);
        }
    }

    class StartColorsProvider implements Theme.ResourcesProvider {
        SparseIntArray colors = new SparseIntArray();
        int[] keysToSave = new int[] {
                Theme.key_chat_outBubble,
                Theme.key_chat_outBubbleGradient1,
                Theme.key_chat_outBubbleGradient2,
                Theme.key_chat_outBubbleGradient3,
                Theme.key_chat_outBubbleGradientAnimated,
                Theme.key_chat_outBubbleShadow
        };

        @Override
        public int getColor(int key) {
            int index = colors.indexOfKey(key);
            if (index >= 0) {
                return colors.valueAt(index);
            }
            return Theme.getColor(key);
        }

        @Override
        public int getCurrentColor(int key) {
            return colors.get(key);
        }

        public void saveColors(Theme.ResourcesProvider fragmentResourceProvider) {
            colors.clear();
            for (int key : keysToSave) {
                colors.put(key, fragmentResourceProvider.getCurrentColor(key));
            }
        }
    }
}
