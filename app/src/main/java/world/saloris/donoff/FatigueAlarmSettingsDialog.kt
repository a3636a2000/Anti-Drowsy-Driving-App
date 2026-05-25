package world.saloris.donoff

import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import world.saloris.donoff.databinding.DialogFatigueAlarmSettingsBinding
import world.saloris.donoff.util.KEY_FATIGUE_ALARM_SCREEN_BLINK
import world.saloris.donoff.util.KEY_FATIGUE_ALARM_SOUND
import world.saloris.donoff.util.KEY_FATIGUE_ALARM_VIBRATION
import world.saloris.donoff.util.PREF_FATIGUE_ALARM

class FatigueAlarmSettingsDialog : DialogFragment() {

    private lateinit var binding: DialogFatigueAlarmSettingsBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DialogFatigueAlarmSettingsBinding.inflate(layoutInflater, container, false)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return binding.root
    }

    private fun savePrefs() {
        prefs.edit()
            .putBoolean(KEY_FATIGUE_ALARM_SCREEN_BLINK, binding.checkScreenBlink.isChecked)
            .putBoolean(KEY_FATIGUE_ALARM_VIBRATION, binding.checkVibration.isChecked)
            .putBoolean(KEY_FATIGUE_ALARM_SOUND, binding.checkSound.isChecked)
            .apply()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = requireContext().getSharedPreferences(PREF_FATIGUE_ALARM, MODE_PRIVATE)
        binding.checkScreenBlink.isChecked =
            prefs.getBoolean(KEY_FATIGUE_ALARM_SCREEN_BLINK, true)
        binding.checkVibration.isChecked =
            prefs.getBoolean(KEY_FATIGUE_ALARM_VIBRATION, true)
        binding.checkSound.isChecked =
            prefs.getBoolean(KEY_FATIGUE_ALARM_SOUND, true)

        binding.btnOkay.setOnClickListener {
            savePrefs()
            dismiss()
        }
    }
}
