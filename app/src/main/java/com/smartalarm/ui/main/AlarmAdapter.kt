package com.smartalarm.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartalarm.R
import com.smartalarm.data.model.Alarm
import com.smartalarm.utils.AlarmScheduler

class AlarmAdapter(
    private val alarms: MutableList<Alarm>,
    private val onToggle: (Alarm, Boolean) -> Unit,
    private val onEdit: (Alarm) -> Unit,
    private val onDelete: (Alarm) -> Unit,
    private val onSetTarget: (Alarm) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    inner class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvAlarmTime)
        val tvLabel: TextView = view.findViewById(R.id.tvAlarmLabel)
        val tvRepeat: TextView = view.findViewById(R.id.tvAlarmRepeat)
        val tvTimeUntil: TextView = view.findViewById(R.id.tvTimeUntil)
        val swEnabled: Switch = view.findViewById(R.id.swAlarmEnabled)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditAlarm)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteAlarm)
        val btnSetTarget: ImageButton = view.findViewById(R.id.btnSetTarget)
        val ivTargetSet: ImageView = view.findViewById(R.id.ivTargetSet)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarms[position]

        holder.tvTime.text = alarm.getTimeString()
        holder.tvLabel.text = alarm.label.ifEmpty { "منبه" }
        holder.tvRepeat.text = alarm.getRepeatText()
        holder.tvTimeUntil.text = if (alarm.isEnabled) AlarmScheduler.getTimeUntilAlarm(alarm) else ""
        holder.swEnabled.isChecked = alarm.isEnabled

        // Target indicator
        holder.ivTargetSet.visibility = if (alarm.hasTarget()) View.VISIBLE else View.GONE

        holder.swEnabled.setOnCheckedChangeListener(null)
        holder.swEnabled.isChecked = alarm.isEnabled
        holder.swEnabled.setOnCheckedChangeListener { _, checked ->
            onToggle(alarm, checked)
            holder.tvTimeUntil.text = if (checked) AlarmScheduler.getTimeUntilAlarm(alarm) else ""
        }

        holder.btnEdit.setOnClickListener { onEdit(alarm) }
        holder.btnDelete.setOnClickListener { onDelete(alarm) }
        holder.btnSetTarget.setOnClickListener { onSetTarget(alarm) }

        // Dim card if disabled
        holder.itemView.alpha = if (alarm.isEnabled) 1.0f else 0.55f
    }

    override fun getItemCount() = alarms.size
}
