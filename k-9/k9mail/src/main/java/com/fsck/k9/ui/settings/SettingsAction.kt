package com.fsck.k9.ui.settings

import android.app.Activity
import com.fsck.k9.activity.setup.AccountSetupBasics
import com.fsck.k9.activity.setup.Prefs

internal enum class SettingsAction {
    GENERAL_SETTINGS {
        override fun execute(activity: Activity) {
            Prefs.actionPrefs(activity)
        }
    },
    ADD_ACCOUNT {
        override fun execute(activity: Activity) {
            AccountSetupBasics.actionNewAccount(activity)
        }
    };

    abstract fun execute(activity: Activity)
}
