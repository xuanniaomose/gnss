package xuanniao.map.gnss.ui

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import xuanniao.map.gnss.R
import java.util.*


class RecordListAdapter(records: MutableList<MovementRecord>):
    RecyclerView.Adapter<RecordListAdapter.ViewHolder?>() {

    private val tag = "循环列表适配器"
    var isEditing = false
    private val recordList: MutableList<MovementRecord> = records.toMutableList()
    val checkedList: MutableList<Boolean> = MutableList(recordList.size) { false }
    private var onItemClickListener: OnItemClickListener? = null
    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.onItemClickListener = listener
    }
    private var deleteClickListener: OnItemDeleteClickListener? = null
    fun setOnItemDeleteClickListener(listener: OnItemDeleteClickListener) {
        this.deleteClickListener = listener
    }

    override fun getItemCount(): Int { return recordList.size }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = recordList[position]
        if (isEditing) {
            holder.ivTrackIcon.visibility = View.GONE
            holder.checkbox.visibility = View.VISIBLE
            holder.btnClose.visibility = View.VISIBLE
            if (!checkedList.isEmpty()) {
                holder.checkbox.isChecked = checkedList[position]
            }
        } else {
            holder.checkbox.visibility = View.GONE
            holder.btnClose.visibility = View.GONE
            holder.ivTrackIcon.visibility = View.VISIBLE
        }
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (!checkedList.isEmpty())
            checkedList[position] = isChecked
        }
        if (position == 0 && record.startLocation.provider == "noRecord") {
            holder.tvDateStart.visibility = View.GONE
            holder.tvTimeStart.visibility = View.GONE
            holder.tvTimeTo.visibility = View.GONE
            holder.tvDateEnd.visibility = View.GONE
            holder.tvTimeEnd.visibility = View.GONE
            holder.tvRegionStart.visibility = View.GONE
            holder.tvRegionTo.visibility = View.GONE
            holder.tvRegionEnd.visibility = View.GONE
            holder.tvNoData.text = "没有数据"
            holder.tvNoData.visibility = View.VISIBLE
            return
        }
        val startDate = record.getFormattedStartDate()
        holder.tvDateStart.text = startDate
        holder.tvTimeStart.text = record.getFormattedStartTime()
        val endDate = record.getFormattedEndDate()
        if (startDate == endDate) {
            holder.tvDateEnd.visibility = View.GONE
        } else {
            holder.tvDateEnd.text = record.getFormattedEndDate()
        }
        holder.tvTimeEnd.text = record.getFormattedEndTime()
        record.startAdminArea?.let {
            holder.tvRegionStart.text = it
            if (record.startAdminArea == record.endAdminArea) {
                holder.tvRegionTo.visibility = View.GONE
                holder.tvRegionEnd.visibility = View.GONE
            } else {
                holder.tvRegionEnd.text = record.endAdminArea
            }
        }
        val tran = record.transportation.name
        if (tran == "STATIONARY") {
            holder.tvTransportation.visibility = View.GONE
        } else {
            holder.tvTransportation.text = tran
        }
        holder.btnClose.setOnClickListener {
            if (isEditing) {
                deleteClickListener?.onDeleteClick(position, record)
                notifyItemRemoved(position)
            }
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivTrackIcon: ImageView = itemView.findViewById(R.id.iv_track_icon)
        val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        val tvDateStart: TextView = itemView.findViewById(R.id.tv_date_start)
        val tvTimeStart: TextView = itemView.findViewById(R.id.tv_time_start)
        val tvTimeTo: TextView = itemView.findViewById(R.id.tv_time_to)
        val tvDateEnd: TextView = itemView.findViewById(R.id.tv_date_end)
        val tvTimeEnd: TextView = itemView.findViewById(R.id.tv_time_end)
        val tvRegionStart: TextView = itemView.findViewById(R.id.tv_region_start)
        val tvRegionTo: TextView = itemView.findViewById(R.id.tv_region_to)
        val tvRegionEnd: TextView = itemView.findViewById(R.id.tv_region_end)
        val tvTransportation: TextView = itemView.findViewById(R.id.tv_transportation)
        val tvNoData: TextView = itemView.findViewById(R.id.tv_no_data)
        val btnClose: ImageButton = itemView.findViewById(R.id.btn_close)

        init {
            // 为整个 itemView 设置点击监听
            itemView.setOnClickListener {
                // 获取当前点击位置的数据，用 bindingAdapterPosition 替代 adapterPosition
                val position = bindingAdapterPosition
                // 防止点击无效位置（如 item 已被删除）
                if (position != RecyclerView.NO_POSITION) {
                    val record = recordList[position]
                    // 通过接口回调传递点击事件和对应数据
                    onItemClickListener?.onClick(position, record)
                }
            }
        }
    }

    fun turnEditing(b: Boolean) {
        this.isEditing = b
        Log.d(tag, "turnEditing()$isEditing")
        notifyDataSetChanged()
    }

    fun checkAll(b: Boolean) {
        for (i in 0 until recordList.size) {
            checkedList[i] = b
        }
        notifyDataSetChanged()
    }

    /**
     * 从 MutableList 中删除指定索引列表对应的元素
     * @param deleteList 要删除的元素索引列表
     */
    @SuppressLint("NotifyDataSetChanged")
    fun cleanCheckedRecords(deleteList: ArrayList<Int>) {
        // 1. 去重 + 降序排序 + 过滤无效索引（避免越界）
        val validIndices = deleteList
            .distinct() // 去重，避免重复删除同一索引
            .filter { it in 0 until recordList.size } // 过滤掉超出列表范围的索引
            .sortedDescending() // 降序排序，从大索引开始删
        // 2. 遍历删除对应元素
        validIndices.forEach { index ->
            recordList.removeAt(index)
        }
        notifyDataSetChanged()
    }

    interface OnItemDeleteClickListener {
        fun onDeleteClick(position: Int, item: MovementRecord)
    }

    interface OnItemClickListener {
        fun onClick(position: Int, record: MovementRecord)
    }
}