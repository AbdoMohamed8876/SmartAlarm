package com.smartalarm.ui.main

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.smartalarm.R
import com.smartalarm.data.model.Alarm
import com.smartalarm.data.prefs.AlarmPreferences
import com.smartalarm.databinding.ActivityMainBinding
import com.smartalarm.ui.settings.SettingsActivity
import com.smartalarm.ui.setup.SetupTargetActivity
import com.smartalarm.utils.AlarmScheduler
import com.smartalarm.utils.NotificationHelper
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: AlarmAdapter
    private val alarms = mutableListOf<Alarm>()

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(this, "بعض الأذونات مرفوضة - قد لا يعمل التطبيق بشكل صحيح", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        NotificationHelper.createChannels(this)
        requestRequiredPermissions()
        setupRecyclerView()
        setupFab()
        loadAlarms()
    }

    override fun onResume() {
        super.onResume()
        loadAlarms()
    }

    private fun requestRequiredPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())

        // Exact alarm permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                AlertDialog.Builder(this)
                    .setTitle("إذن المنبه الدقيق")
                    .setMessage("لكي يعمل المنبه في الوقت المحدد تماماً، تحتاج إلى منح إذن المنبه الدقيق")
                    .setPositiveButton("منح الإذن") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    }
                    .setNegativeButton("لاحقاً", null)
                    .show()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = AlarmAdapter(
            alarms,
            onToggle = { alarm, enabled ->
                alarm.isEnabled = enabled
                AlarmPreferences.saveAlarm(this, alarm)
                if (enabled) {
                    AlarmScheduler.scheduleAlarm(this, alarm)
                    val msg = "المنبه مضبوط ${AlarmScheduler.getTimeUntilAlarm(alarm)}"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                } else {
                    AlarmScheduler.cancelAlarm(this, alarm)
                }
            },
            onEdit = { alarm -> openEditDialog(alarm) },
            onDelete = { alarm -> confirmDelete(alarm) },
            onSetTarget = { alarm ->
                startActivity(Intent(this, SetupTargetActivity::class.java).apply {
                    putExtra("alarm_id", alarm.id)
                })
            }
        )
        binding.recyclerAlarms.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupFab() {
        binding.fabAddAlarm.setOnClickListener { showAddAlarmDialog() }
    }

    private fun loadAlarms() {
        alarms.clear()
        alarms.addAll(AlarmPreferences.loadAlarms(this).sortedWith(
            compareBy({ it.hour }, { it.minute })
        ))
        adapter.notifyDataSetChanged()
        binding.tvEmpty.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE
        binding.recyclerAlarms.visibility = if (alarms.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showAddAlarmDialog() {
        val cal = Calendar.getInstance()
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(cal.get(Calendar.HOUR_OF_DAY))
            .setMinute(cal.get(Calendar.MINUTE))
            .setTitleText("اختر وقت المنبه")
            .build()

        picker.addOnPositiveButtonClickListener {
            val alarm = Alarm(
                id = AlarmPreferences.getNextId(this),
                hour = picker.hour,
                minute = picker.minute,
                label = "منبه"
            )
            AlarmPreferences.saveAlarm(this, alarm)
            AlarmScheduler.scheduleAlarm(this, alarm)
            val msg = "تم ضبط المنبه ${AlarmScheduler.getTimeUntilAlarm(alarm)}"
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            loadAlarms()
        }
        picker.show(supportFragmentManager, "time_picker")
    }

    private fun openEditDialog(alarm: Alarm) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(alarm.hour)
            .setMinute(alarm.minute)
            .setTitleText("تعديل المنبه")
            .build()

        picker.addOnPositiveButtonClickListener {
            alarm.hour = picker.hour
            alarm.minute = picker.minute
            AlarmPreferences.saveAlarm(this, alarm)
            if (alarm.isEnabled) {
                AlarmScheduler.cancelAlarm(this, alarm)
                AlarmScheduler.scheduleAlarm(this, alarm)
            }
            loadAlarms()
        }
        picker.show(supportFragmentManager, "edit_picker")
    }

    private fun confirmDelete(alarm: Alarm) {
        AlertDialog.Builder(this)
            .setTitle("حذف المنبه")
            .setMessage("هل تريد حذف هذا المنبه؟")
            .setPositiveButton("حذف") { _, _ ->
                AlarmScheduler.cancelAlarm(this, alarm)
                AlarmPreferences.deleteAlarm(this, alarm.id)
                loadAlarms()
                Toast.makeText(this, "تم حذف المنبه", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
