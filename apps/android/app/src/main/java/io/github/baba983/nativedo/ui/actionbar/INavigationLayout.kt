/*
 * This file is part of NativeDo.
 *
 * Derived in part from Telegram for Android's
 * org.telegram.ui.ActionBar.INavigationLayout.
 *
 * Source reference:
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/INavigationLayout.java
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
package io.github.baba983.nativedo.ui.actionbar

interface INavigationLayoutDelegate {
    fun onFragmentStackChanged(stack: List<BaseFragment>) = Unit

    fun onVisibleFragmentChanged(fragment: BaseFragment?) = Unit
}

interface INavigationLayout {
    fun presentFragment(fragment: BaseFragment, animated: Boolean = true): Boolean

    fun closeLastFragment(animated: Boolean = true): Boolean

    fun getFragmentStack(): List<BaseFragment>

    fun addFragmentToStack(fragment: BaseFragment, position: Int): Boolean

    fun removeFragmentFromStack(fragment: BaseFragment): Boolean

    fun setDelegate(delegate: INavigationLayoutDelegate?)

    fun onResume()

    fun onPause()

    fun onBackPressed(): Boolean

    fun getLastFragment(): BaseFragment? = getFragmentStack().lastOrNull()
}
