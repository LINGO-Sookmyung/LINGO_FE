package com.example.lingo.ui.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.example.lingo.R
import com.google.android.material.appbar.MaterialToolbar

abstract class BaseActivity : AppCompatActivity() {

    private var toolbar: MaterialToolbar? = null

    override fun onContentChanged() {
        super.onContentChanged()
        toolbar = findViewById(R.id.topToolbar)
    }

    protected fun setAppBar(title: String?, showBack: Boolean = true) {
        if (toolbar == null) toolbar = findViewById(R.id.topToolbar)

        toolbar?.title = title ?: ""

        if (showBack) {
            toolbar?.navigationIcon = AppCompatResources.getDrawable(this, R.drawable.ic_back)
            toolbar?.setNavigationOnClickListener { finish() }
        } else {
            toolbar?.navigationIcon = null
            toolbar?.setNavigationOnClickListener(null)
        }
    }

    protected fun updateTitle(newTitle: String) {
        toolbar?.title = newTitle
    }
}