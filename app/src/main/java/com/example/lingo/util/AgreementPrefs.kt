package com.example.lingo.util

import android.content.Context
import androidx.core.content.edit

object AgreementPrefs {
    private const val PREF = "agreement_prefs"
    private const val KEY_VERSION = "agreement_version"
    private const val KEY_ACCEPTED = "agreement_accepted"

    // 약관/개인정보 버전을 올리면 강제 재동의
    const val AGREEMENT_VERSION = 1

    fun isAccepted(context: Context): Boolean {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val ver = sp.getInt(KEY_VERSION, 0)
        val accepted = sp.getBoolean(KEY_ACCEPTED, false)
        return (ver == AGREEMENT_VERSION) && accepted
    }

    fun setAccepted(context: Context, accepted: Boolean) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.edit {
            putInt(KEY_VERSION, AGREEMENT_VERSION)
            putBoolean(KEY_ACCEPTED, accepted)
        }
    }

    fun reset(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.edit {
            remove(KEY_VERSION)
            remove(KEY_ACCEPTED)
        }
    }
}
