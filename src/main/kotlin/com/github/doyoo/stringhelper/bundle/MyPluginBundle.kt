package com.github.doyoo.stringhelper.bundle

import com.intellij.AbstractBundle
import org.jetbrains.annotations.PropertyKey

/**
 * @Author: Aaron
 * @Date: 2026/04/07 13:40:15
 */
private const val BUNDLE = "messages.MyBundle"

object MyPluginBundle : AbstractBundle(BUNDLE) {
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any) =
        getMessage(key, *params)
}