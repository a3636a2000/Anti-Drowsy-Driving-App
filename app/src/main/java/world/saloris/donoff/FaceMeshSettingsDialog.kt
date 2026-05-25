package world.saloris.donoff

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import world.saloris.donoff.databinding.DialogFaceMeshSettingsBinding

class FaceMeshSettingsDialog : DialogFragment() {
    /* View */
    private lateinit var binding: DialogFaceMeshSettingsBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = DialogFaceMeshSettingsBinding.inflate(layoutInflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return binding.root
    }

    // Check 확인
    private fun saveFaceMeshExpress() {
        prefs.edit().putBoolean("eye", binding.checkEye.isChecked).apply()
        prefs.edit().putBoolean("eyeBrow", binding.checkEyebrow.isChecked).apply()
        prefs.edit().putBoolean("eyePupil", binding.checkPupil.isChecked).apply()
        prefs.edit().putBoolean("lip", binding.checkLip.isChecked).apply()
        prefs.edit().putBoolean("faceMesh", binding.checkFaceMesh.isChecked).apply()
        prefs.edit().putBoolean("faceLine", binding.checkFaceLine.isChecked).apply()
    }

    private fun colorCheck(name: String, defaultInt: Int, textView: TextView) {

        binding.eye.setTypeface(null, Typeface.NORMAL)
        binding.eyeBrow.setTypeface(null, Typeface.NORMAL)
        binding.eyePupil.setTypeface(null, Typeface.NORMAL)
        binding.lip.setTypeface(null, Typeface.NORMAL)
        binding.faceMesh.setTypeface(null, Typeface.NORMAL)
        binding.faceLine.setTypeface(null, Typeface.NORMAL)

        binding.white.foreground = null
        binding.orange.foreground = null
        binding.blue.foreground = null
        binding.red.foreground = null
        binding.green.foreground = null
        binding.black.foreground = null

        when (prefs.getInt(name, defaultInt)) {
            1 -> binding.white.foreground =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_checked)
            2 -> binding.orange.foreground =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_checked)
            3 -> binding.blue.foreground =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_checked)
            4 -> binding.red.foreground =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_checked)
            5 -> binding.green.foreground =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_checked)
            else -> binding.black.foreground =
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_checked)
        }
        textView.setTypeface(null, Typeface.BOLD_ITALIC)
    }

    private fun colorClick(name: String, checkbox: CheckBox, textView: TextView, defaultInt: Int) {
        binding.colorBoard.visibility =
            if (checkbox.isChecked) View.VISIBLE
            else View.INVISIBLE
        colorCheck(name, defaultInt, textView)

        // black : 0, white : 1, orange : 2, blue : 3, red : 4, green : 5
        binding.black.setOnClickListener {
            prefs.edit().putInt(name, 0).apply()
            colorCheck(name, defaultInt, textView)
        }
        binding.white.setOnClickListener {
            prefs.edit().putInt(name, 1).apply()
            colorCheck(name, defaultInt, textView)
        }
        binding.orange.setOnClickListener {
            prefs.edit().putInt(name, 2).apply()
            colorCheck(name, defaultInt, textView)
        }
        binding.blue.setOnClickListener {
            prefs.edit().putInt(name, 3).apply()
            colorCheck(name, defaultInt, textView)
        }
        binding.red.setOnClickListener {
            prefs.edit().putInt(name, 4).apply()
            colorCheck(name, defaultInt, textView)
        }
        binding.green.setOnClickListener {
            prefs.edit().putInt(name, 5).apply()
            colorCheck(name, defaultInt, textView)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // faceMesh Check

        // 초기화
        prefs = context?.getSharedPreferences("faceSetting", MODE_PRIVATE)!!
        binding.checkEye.isChecked = prefs.getBoolean("eye", false)
        binding.checkEyebrow.isChecked = prefs.getBoolean("eyeBrow", false)
        binding.checkPupil.isChecked = prefs.getBoolean("eyePupil", false)
        binding.checkLip.isChecked = prefs.getBoolean("lip", false)
        binding.checkFaceMesh.isChecked = prefs.getBoolean("faceMesh", false)
        binding.checkFaceLine.isChecked = prefs.getBoolean("faceLine", true)

        binding.eye.setOnClickListener { colorClick("eyeColor", binding.checkEye, binding.eye, 5) }
        binding.eyeBrow.setOnClickListener {
            colorClick("eyeBrowColor", binding.checkEyebrow, binding.eyeBrow, 4)
        }
        binding.eyePupil.setOnClickListener {
            colorClick("eyePupilColor", binding.checkPupil, binding.eyePupil, 1)
        }
        binding.lip.setOnClickListener { colorClick("lipColor", binding.checkLip, binding.lip, 3) }
        binding.faceMesh.setOnClickListener {
            colorClick("faceMeshColor", binding.checkFaceMesh, binding.faceMesh, 1)
        }
        binding.faceLine.setOnClickListener {
            colorClick("faceLineColor", binding.checkFaceLine, binding.faceLine, 1)
        }

        binding.btnOkay.setOnClickListener {
            saveFaceMeshExpress()
            dismiss()
        }
    }
}