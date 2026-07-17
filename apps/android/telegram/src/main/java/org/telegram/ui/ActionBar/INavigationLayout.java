package org.telegram.ui.ActionBar;

import java.util.List;

public interface INavigationLayout {

    boolean presentFragment(NavigationParams params);

    boolean addFragmentToStack(BaseFragment fragment, int position);

    void closeLastFragment(boolean animated, boolean forceNoAnimation);

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
}
