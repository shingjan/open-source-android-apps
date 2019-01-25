package com.fsck.k9.ui.settings

import com.fsck.k9.R
import com.xwray.groupie.kotlinandroidextensions.Item
import com.xwray.groupie.kotlinandroidextensions.ViewHolder
import kotlinx.android.synthetic.main.text_list_item.*

internal class SettingsActionItem(val text: String, val action: SettingsAction) : Item() {

    override fun getLayout(): Int = R.layout.text_list_item

    override fun bind(viewHolder: ViewHolder, position: Int) {
        viewHolder.text.text = text
    }
}
