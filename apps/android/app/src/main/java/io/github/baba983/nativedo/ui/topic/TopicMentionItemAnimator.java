package io.github.baba983.nativedo.ui.topic;

import android.view.animation.Interpolator;

import androidx.recyclerview.widget.DefaultItemAnimator;

import org.telegram.ui.Components.CubicBezierInterpolator;

import java.lang.reflect.Method;

final class TopicMentionItemAnimator extends DefaultItemAnimator {
    TopicMentionItemAnimator() {
        setAddDuration(150);
        setMoveDuration(150);
        setChangeDuration(150);
        setRemoveDuration(150);
        invokeTelegramExtension("setTranslationInterpolator", Interpolator.class, CubicBezierInterpolator.DEFAULT);
        invokeTelegramExtension("setDelayAnimations", boolean.class, false);
    }

    private void invokeTelegramExtension(String name, Class<?> parameterType, Object value) {
        try {
            Method method = DefaultItemAnimator.class.getMethod(name, parameterType);
            method.invoke(this, value);
        } catch (ReflectiveOperationException ignored) {
            // The app compile classpath exposes AndroidX's base API; the vendored
            // Telegram implementation supplies these methods at runtime.
        }
    }
}
