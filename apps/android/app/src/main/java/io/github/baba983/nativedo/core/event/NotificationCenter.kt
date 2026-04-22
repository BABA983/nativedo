/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org.telegram.messenger.NotificationCenter.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/messenger/NotificationCenter.java
 *
 * Copyright (C) Telegram FZ-LLC
 * Copyright (C) 2026 NativeDo contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under terms of GNU General Public License version 2 as published by
 * Free Software Foundation.
 *
 * SPDX-License-Identifier: GPL-2.0-only
 */
package io.github.baba983.nativedo.core.event

import android.os.Handler
import android.os.Looper
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

interface NotificationCenterDelegate {
    fun didReceivedNotification(id: Int, vararg args: Any?)
}

/**
 * Global cross-cutting event bus for application-scoped concerns only.
 *
 * Screen-local UI state should stay inside the owning fragment/controller and must not be modeled as
 * NotificationCenter events.
 */
object NotificationCenter {
    const val topicsDidLoad: Int = 1
    const val topicDetailDidLoad: Int = 2
    const val sessionDidChange: Int = 3
    const val sessionDidExpire: Int = 4
    const val csrfTokenDidRefresh: Int = 5
    const val themeDidChange: Int = 6
    const val categoryDataDidLoad: Int = 7
    const val searchResultsDidLoad: Int = 8
    const val profileDidLoad: Int = 9
    const val cloudflareChallenge: Int = 10
    const val appForegroundStateChanged: Int = 11
    const val networkStateChanged: Int = 12

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val observersByEventId = mutableMapOf<Int, LinkedHashSet<NotificationCenterDelegate>>()
    private val eventIdsByObserver = IdentityHashMap<NotificationCenterDelegate, LinkedHashSet<Int>>()
    private val allowedEventIds = setOf(
        topicsDidLoad,
        topicDetailDidLoad,
        sessionDidChange,
        sessionDidExpire,
        csrfTokenDidRefresh,
        themeDidChange,
        categoryDataDidLoad,
        searchResultsDidLoad,
        profileDidLoad,
        cloudflareChallenge,
        appForegroundStateChanged,
        networkStateChanged,
    )

    fun addObserver(observer: NotificationCenterDelegate, eventId: Int) {
        requireKnownEvent(eventId)
        synchronized(lock) {
            observersByEventId.getOrPut(eventId) { linkedSetOf() }.add(observer)
            eventIdsByObserver.getOrPut(observer) { linkedSetOf() }.add(eventId)
        }
    }

    fun removeObserver(observer: NotificationCenterDelegate, eventId: Int) {
        requireKnownEvent(eventId)
        synchronized(lock) {
            observersByEventId[eventId]?.remove(observer)
            if (observersByEventId[eventId].isNullOrEmpty()) {
                observersByEventId.remove(eventId)
            }

            eventIdsByObserver[observer]?.remove(eventId)
            if (eventIdsByObserver[observer].isNullOrEmpty()) {
                eventIdsByObserver.remove(observer)
            }
        }
    }

    fun removeObserver(observer: NotificationCenterDelegate) {
        synchronized(lock) {
            val eventIds = eventIdsByObserver.remove(observer)?.toList().orEmpty()
            eventIds.forEach { eventId ->
                observersByEventId[eventId]?.remove(observer)
                if (observersByEventId[eventId].isNullOrEmpty()) {
                    observersByEventId.remove(eventId)
                }
            }
        }
    }

    fun postNotificationName(id: Int, vararg args: Any?) {
        requireKnownEvent(id)
        runOnMainThread {
            val observers = synchronized(lock) {
                observersByEventId[id]?.toList().orEmpty()
            }
            observers.forEach { observer ->
                observer.didReceivedNotification(id, *args)
            }
        }
    }

    internal fun resetForTesting() {
        synchronized(lock) {
            observersByEventId.clear()
            eventIdsByObserver.clear()
        }
    }

    private fun requireKnownEvent(eventId: Int) {
        require(eventId in allowedEventIds) {
            "NotificationCenter only supports predefined global event ids"
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == mainHandler.looper) {
            block()
            return
        }

        val failure = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                block()
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                latch.countDown()
            }
        }
        check(latch.await(5, TimeUnit.SECONDS)) {
            "NotificationCenter main-thread dispatch timed out"
        }
        failure.get()?.let { throw it }
    }
}
