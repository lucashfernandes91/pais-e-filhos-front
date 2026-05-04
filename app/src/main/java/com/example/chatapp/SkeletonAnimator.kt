package com.example.chatapp

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup

/**
 * Utilitário para aplicar efeito shimmer (pulse) em skeleton views.
 * Aplica animação de alpha pulsante em todas as Views filhas.
 */
object SkeletonAnimator {

    fun startShimmer(container: View) {
        if (container is ViewGroup) {
            for (i in 0 until container.childCount) {
                startShimmer(container.getChildAt(i))
            }
        }
        val animator = ObjectAnimator.ofFloat(container, "alpha", 1f, 0.4f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        container.tag = animator
        animator.start()
    }

    fun stopShimmer(container: View) {
        val animator = container.tag as? ObjectAnimator
        animator?.cancel()
        container.alpha = 1f
        if (container is ViewGroup) {
            for (i in 0 until container.childCount) {
                stopShimmer(container.getChildAt(i))
            }
        }
    }
}
