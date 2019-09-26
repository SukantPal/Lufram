package com.zexfer.lufram


import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment

/**
 * A simple [Fragment] subclass.
 */
class WhatsThisTimelineDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(ContextThemeWrapper(context!!, R.style.LuframTheme_PreferenceDialog))
            .setTitle("What's This")
            .setMessage(R.string.whats_this_timeline_options)
            .setPositiveButton("Okay") { dialog, _ -> dialog.dismiss() }
            .create()
}
