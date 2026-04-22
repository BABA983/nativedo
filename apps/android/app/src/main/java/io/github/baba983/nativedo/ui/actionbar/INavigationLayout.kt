package io.github.baba983.nativedo.ui.actionbar

fun interface INavigationLayoutDelegate {
    fun onFragmentStackChanged(stack: List<BaseFragment>)
}

interface INavigationLayout {
    fun presentFragment(fragment: BaseFragment, animated: Boolean = true): Boolean

    fun closeLastFragment(animated: Boolean = true): Boolean

    fun getFragmentStack(): List<BaseFragment>

    fun addFragmentToStack(fragment: BaseFragment, position: Int): Boolean

    fun removeFragmentFromStack(fragment: BaseFragment): Boolean

    fun setDelegate(delegate: INavigationLayoutDelegate?)
}
