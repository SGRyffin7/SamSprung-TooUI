package com.sec.android.app.shealth

import android.annotation.SuppressLint
import android.service.notification.StatusBarNotification
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.*

class NotificationsAdapter(
    private val listener: OnNoticeClickListener
) : RecyclerView.Adapter<NotificationsAdapter.NoticeViewHolder>() {
    override fun getItemCount(): Int {
        return SamSprung.statuses.size
    }

    override fun getItemId(i: Int): Long {
        return i.toLong()
    }

    private fun getItem(i: Int): StatusBarNotification {
        return SamSprung.statuses[i]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoticeViewHolder {
        return SimpleViewHolder(parent, listener)
    }

    override fun onBindViewHolder(holder: NoticeViewHolder, position: Int) {
        holder.itemView.setOnClickListener {
            if (null != holder.listener) holder.listener.onNoticeClicked(
                holder.notice!!,
                position
            )
        }
        holder.iconView.setOnClickListener {
            if (null != holder.listener) {
                holder.listener.onNoticeClicked(holder.notice!!, position)
            }
        }
        holder.bind(getItem(position))
    }

    abstract class NoticeViewHolder(
        itemView: View, val listener: OnNoticeClickListener?
    ) : RecyclerView.ViewHolder(itemView) {
        val iconView: ImageView = itemView.findViewById(R.id.icon)
        private val tickerText: TextView =  itemView.findViewById(R.id.ticker)
        private val linesText: TextView =  itemView.findViewById(R.id.lines)
        var notice: StatusBarNotification? = null
        @SuppressLint("SetTextI18n")
        fun bind(notice: StatusBarNotification) {
            this.notice = notice
            iconView.setImageDrawable(
                notice.notification.smallIcon.loadDrawable(itemView.context))
            tickerText.text = notice.notification.tickerText
            if (null != notice.notification.extras) {
                if (notice.notification.extras.getCharSequenceArray(
                        NotificationCompat.EXTRA_TEXT_LINES) != null) {
                    val content = Arrays.toString(
                        notice.notification.extras.getCharSequenceArray(
                            NotificationCompat.EXTRA_TEXT_LINES)
                    )
                    if (notice.notification.tickerText == content)
                        tickerText.visibility = View.GONE
                    linesText.text = content
                }

            }
        }
    }

    internal class SimpleViewHolder(
        parent: ViewGroup,
        listener: OnNoticeClickListener?
    ) : NoticeViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.notification_card,
            parent, false
        ), listener
    )

    interface OnNoticeClickListener {
        fun onNoticeClicked(notice: StatusBarNotification, position: Int)
    }
}