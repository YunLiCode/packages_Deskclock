package com.android.yun.deskclock;

import com.android.deskclock.*;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.database.Cursor;
import android.os.Parcel;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {
	private final static int STALE_WINDOW = 30 * 60 * 1000;

	@SuppressLint("NewApi")
	@Override
	public void onReceive(Context context, Intent intent) {
		if (Alarms.ALARM_KILLED.equals(intent.getAction())) {
			updateNotification(context,
					(Alarm) intent
							.getParcelableExtra(Alarms.ALARM_INTENT_EXTRA),
					intent.getIntExtra(Alarms.ALARM_KILLED_TIMEOUT, -1));
			return;
		} else if (Alarms.CANCEL_SNOOZE.equals(intent.getAction())) {
			Alarms.saveSnoozeAlert(context, -1, -1);
			return;
		} else if (!Alarms.ALARM_ALERT_ACTION.equals(intent.getAction())) {
			return;
		}

		Alarm alarm = null;
		final byte[] data = intent.getByteArrayExtra(Alarms.ALARM_RAW_DATA);
		if (data != null) {
			Parcel in = Parcel.obtain();
			in.unmarshall(data, 0, data.length);
			in.setDataPosition(0);
			alarm = Alarm.CREATOR.createFromParcel(in);
		}

		if (alarm == null) {
			Alarms.setNextAlert(context);
			return;
		}
		Alarms.disableSnoozeAlert(context, alarm.id);
		if (!alarm.daysOfWeek.isRepeatSet()) {
			Alarms.enableAlarm(context, alarm.id, false);
		} else {
			Alarms.setNextAlert(context);
		}
		long now = System.currentTimeMillis();
		if (now > alarm.time + STALE_WINDOW) {
			Log.v("wangxianming", "Ignoring stale alarm");
			return;
		}
		AlarmAlertWakeLock.acquireCpuWakeLock(context);
		Intent closeDialogs = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
		context.sendBroadcast(closeDialogs);
		Class c = AlarmAlert.class;
		KeyguardManager km = (KeyguardManager) context
				.getSystemService(Context.KEYGUARD_SERVICE);
		if (km.inKeyguardRestrictedInputMode()) {
			c = AlarmAlertFullScreen.class;
		}
		Intent playAlarm = new Intent(Alarms.ALARM_ALERT_ACTION);
		playAlarm.putExtra(Alarms.ALARM_INTENT_EXTRA, alarm);
		context.startService(playAlarm);
		Intent notify = new Intent(context, AlarmAlert.class);
		notify.putExtra(Alarms.ALARM_INTENT_EXTRA, alarm);
		PendingIntent pendingNotify = PendingIntent.getActivity(context,
				alarm.id, notify, 0);
		String label = alarm.getLabelOrDefault(context);
		Notification n = new Notification(R.drawable.stat_notify_alarm, label,
				alarm.time);
		n.setLatestEventInfo(context, label,
				context.getString(R.string.alarm_notify_text), pendingNotify);
		n.flags |= Notification.FLAG_SHOW_LIGHTS
				| Notification.FLAG_ONGOING_EVENT;
		n.defaults |= Notification.DEFAULT_LIGHTS;
		Intent alarmAlert = new Intent(context, c);
		alarmAlert.putExtra(Alarms.ALARM_INTENT_EXTRA, alarm);
		alarmAlert.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
				| Intent.FLAG_ACTIVITY_NO_USER_ACTION);
		n.fullScreenIntent = PendingIntent.getActivity(context, alarm.id,
				alarmAlert, 0);
		NotificationManager nm = getNotificationManager(context);
		nm.notify(alarm.id, n);
	}

	private NotificationManager getNotificationManager(Context context) {
		return (NotificationManager) context
				.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	private void updateNotification(Context context, Alarm alarm, int timeout) {
		NotificationManager nm = getNotificationManager(context);
		if (alarm == null) {
			if (true) {
			}
			return;
		}
		Intent viewAlarm = new Intent(context, SetAlarm.class);
		viewAlarm.putExtra(Alarms.ALARM_ID, alarm.id);
		PendingIntent intent = PendingIntent.getActivity(context, alarm.id,
				viewAlarm, 0);
		String label = alarm.getLabelOrDefault(context);
		Notification n = new Notification(R.drawable.stat_notify_alarm, label,
				alarm.time);
		n.setLatestEventInfo(
				context,
				label,
				context.getString(R.string.alarm_alert_alert_silenced, timeout),
				intent);
		n.flags |= Notification.FLAG_AUTO_CANCEL;
		nm.cancel(alarm.id);
		nm.notify(alarm.id, n);
	}
}
