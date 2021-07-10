package com.hanafey.android.wol

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.hanafey.android.wol.databinding.DialogErrorReportBinding

class ErrorReportFragment : Fragment() {

    private val mvm: MainViewModel by activityViewModels()

    private var _binding: DialogErrorReportBinding? = null
    private val ui: DialogErrorReportBinding
        get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = DialogErrorReportBinding.inflate(inflater, container, false)
        // SEE: https://stackoverflow.com/questions/57449900/letting-webview-on-android-work-with-prefers-color-scheme-dark
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                WebSettingsCompat.setForceDark(ui.errorReport.settings, WebSettingsCompat.FORCE_DARK_ON)
            }
        }
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val report = arguments?.getString("error_report") ?: "Error, on error report. No error_report argument was provided!"
        ui.errorReport.loadDataWithBaseURL(null, report, "text/html", "utf-8", null)

        ui.upButton.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}