package com.lightningkite.kotlin.anko.viewcontrollers.implementations

import android.view.View
import android.view.ViewGroup
import com.lightningkite.kotlin.anko.animation.AnimationSet
import com.lightningkite.kotlin.anko.getActivity
import com.lightningkite.kotlin.anko.viewcontrollers.ViewController
import com.lightningkite.kotlin.anko.viewcontrollers.containers.VCContainer
import com.lightningkite.kotlin.anko.viewcontrollers.containers.VCTabs

/**
 * Embeds the given view container in the given view, transitioning new views in and out as needed.
 *
 * Created by joseph on 11/7/16.
 */
class VCContainerEmbedder(val root: ViewGroup, val container: VCContainer, val makeLayoutParams: () -> ViewGroup.LayoutParams) {

    var defaultAnimation: AnimationSet? = AnimationSet.fade
    val views: ArrayList<View> = arrayListOf()

    var wholeViewAnimatingIn: Boolean = false
    var killViewAnimateOutCalled: Boolean = false

    val activity: VCActivity get() = root.getActivity() as? VCActivity ?: throw IllegalArgumentException("Root view must belong to a VCActivity")

    var current: ViewController? = null
    var currentView: View? = null
    val swap = fun(new: ViewController, preferredAnimation: AnimationSet?, onFinish: () -> Unit) {
        val oldView = currentView
        val old = current
        val animation = preferredAnimation ?: defaultAnimation

        current = new

        try {
            // If container is VCTabs we handle views and view controllers in a specific way.
            container as VCTabs

            // Reuse existing views for tabs
            val newView = views[container.viewControllers.indexOf(new)]

            currentView = newView
            if (old != null && oldView != null) {
                // Do not unmake old view when switching to the new one to avoid reloading views
                if (animation == null) {
                    old.animateOutStart(activity, oldView)
                    setView(newView)
                    onFinish()
                    new.animateInComplete(activity, newView)
                } else {
                    val animateOut = animation.animateOut
                    old.animateOutStart(activity, oldView)
                    oldView.animateOut(root).withEndAction {
                        setView(newView)
                        onFinish()
                    }.start()
                    val animateIn = animation.animateIn
                    newView.animateIn(root).withEndAction {
                        new.animateInComplete(activity, newView)
                    }.start()
                }
            } else {
                if (!wholeViewAnimatingIn) {
                    setView(newView)
                    new.animateInComplete(activity, newView)
                }
            }
        } catch (e: Exception) {
            val newView = new.make(activity)

            root.addView(newView, makeLayoutParams())
            currentView = newView
            if (old != null && oldView != null) {
                if (animation == null) {
                    old.animateOutStart(activity, oldView)
                    old.unmake(oldView)
                    root.removeView(oldView)
                    onFinish()
                    new.animateInComplete(activity, newView)
                } else {
                    val animateOut = animation.animateOut
                    old.animateOutStart(activity, oldView)
                    oldView.animateOut(root).withEndAction {
                        old.unmake(oldView)
                        root.removeView(oldView)
                        onFinish()
                    }.start()
                    val animateIn = animation.animateIn
                    newView.animateIn(root).withEndAction {
                        new.animateInComplete(activity, newView)
                    }.start()
                }
            } else {
                if (!wholeViewAnimatingIn) {
                    new.animateInComplete(activity, newView)
                }
            }
        }

        killViewAnimateOutCalled = false
    }

    init {
        try {
            // Make views and save them into `views` array if container is VCTabs
            // View for each tab is made only once, so now it is possible to:
            // - Avoid several bugs with Map tab annotations
            // - Show tab data instantly instead of loading it each time user selects new tab
            // - Stop showing white screen when switching tabs too quickly
            container as VCTabs
            container.viewControllers.forEachIndexed { index, viewController ->
                views.add(index, viewController.make(activity))
            }
        } catch (e: Exception) {
            // Do nothing otherwise
        }
        container.swapListener = swap
        swap(container.current, null) {}
    }

    fun animateInComplete(activity: VCActivity, view: View) {
        current?.animateInComplete(activity, currentView!!)
    }

    fun animateOutStart(activity: VCActivity, view: View) {
        killViewAnimateOutCalled = true
        current?.animateOutStart(activity, currentView!!)
    }

    fun unmake() {
        if (!killViewAnimateOutCalled) {
            current?.animateOutStart(activity, currentView!!)
            killViewAnimateOutCalled = true
        }
        current?.unmake(currentView!!)
        if (currentView != null) {
            root.removeView(currentView)
        }
        current = null
        currentView = null
    }

    private fun setView(view: View) {
        root.removeAllViews()
        root.addView(view, makeLayoutParams())
    }
}