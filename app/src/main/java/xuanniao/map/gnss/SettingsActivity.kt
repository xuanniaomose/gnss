package xuanniao.map.gnss

import android.R
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.content, SettingsFragment())
            .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true) // 显示返回箭头
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed() // 点击返回箭头时返回
        return true
    }
}