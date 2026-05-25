package world.saloris.donoff

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    /* User Authentication */
    private lateinit var auth: FirebaseAuth

    private fun loadAutoLoginInfo(): Pair<String?, String?> {
        val autoLoginPref = getSharedPreferences("autoLogin", Activity.MODE_PRIVATE)
        val username = autoLoginPref.getString("username", null)
        val password = autoLoginPref.getString("password", null)
        return username to password
    }

    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        /* Status Bar & Navigation Bar */
        val barColor = ContextCompat.getColor(this, R.color.primary)
        with(window) {
            statusBarColor = barColor
            navigationBarColor = barColor
        }
        with(WindowInsetsControllerCompat(window, window.decorView)) {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        /* User Authentication */
        auth = Firebase.auth
        auth.signOut()
        val (username, password) = loadAutoLoginInfo()
        if (username == null || password == null) {
            Handler(Looper.getMainLooper()).postDelayed({ goToMainActivity() }, 1000)
        } else {
            auth.signInWithEmailAndPassword(username, password)
                .addOnCompleteListener {
                    goToMainActivity()
                }
        }
    }
}