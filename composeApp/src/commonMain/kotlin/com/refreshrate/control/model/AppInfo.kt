package com.refreshrate.control.model

data class AppInfo(
    val name: String,
    val packageName: String,
    val systemApp: Boolean = false,
    val userId: Int = 0
) {
    val effectivePkg: String
        get() = if (userId > 0) "$packageName:u$userId" else packageName
}
