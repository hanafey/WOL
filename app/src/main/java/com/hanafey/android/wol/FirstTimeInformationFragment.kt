package com.hanafey.android.wol

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.hanafey.android.wol.databinding.FragmentFirstTimeInformationBinding


class FirstTimeInformationFragment : Fragment() {
    private val ltag = "FirstTimeInformationFragment"
    private val mvm: MainViewModel by activityViewModels()

    private var _binding: FragmentFirstTimeInformationBinding? = null
    private val ui: FragmentFirstTimeInformationBinding
        get() = _binding!!


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        _binding = FragmentFirstTimeInformationBinding.inflate(inflater, container, false)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ui.visitWebSiteButton.setOnClickListener {
            openWebPageIntent("https://wol-bliss.hanafey.com")
        }

        ui.visitReleaseNotesButton.setOnClickListener {
            openWebPageIntent("https://wol-bliss.hanafey.com/release-notes")
        }

        ui.visitHostSetupButton.setOnClickListener {
            openWebPageIntent("https://wol-bliss.hanafey.com/host-setup")
        }

        ui.showNextTimeCheckbox.setOnClickListener {
            if (it is CheckBox) {
                if (it.isChecked) {
                    mvm.settingsData.writeVersionAcknowledged(BuildConfig.VERSION_CODE)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openWebPageIntent(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            val report = getString(R.string.error_doc_site, e.localizedMessage)
            val bundle = Bundle().apply {
                putString("error_report", report)
            }
            findNavController().navigate(R.id.ErrorReportFragment, bundle)
        }
    }

}