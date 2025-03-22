package com.sender_sms_buttons;

import android.Manifest;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

public class SmsWidget extends AppWidgetProvider {
    public static final String ACTION_SEND_SMS = "ACTION_SEND_SMS";
    public static final String PREFS_NAME = "SmsWidgetPrefs";

    // Исправленный метод (объединены оба варианта)
    private static String getContactName(Context context, String phoneNumber) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED || phoneNumber.isEmpty()) {
            return phoneNumber;
        }

        Uri uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber.replaceAll("[^+\\d]", "")) // Очистка номера
        );

        String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};

        try (Cursor cursor = context.getContentResolver().query(
                uri,
                projection,
                null,
                null,
                null)) {

            return (cursor != null && cursor.moveToFirst())
                    ? cursor.getString(0)
                    : phoneNumber;

        } catch (SecurityException e) {
            Log.e("CONTACTS", "Permission error: " + e.getMessage());
            return phoneNumber;
        }

    }

    private static String buildButtonText(Context context, SharedPreferences prefs, int appWidgetId) {
        StringBuilder sb = new StringBuilder();

        if(prefs.getBoolean("show_name_" + appWidgetId, true)) {
            sb.append(prefs.getString("name_" + appWidgetId, ""));
        }
        if(prefs.getBoolean("show_phone_" + appWidgetId, true)) {
            if(sb.length() > 0) sb.append("\n");
            sb.append(getContactName(context, prefs.getString("number_" + appWidgetId, "")));
        }
        if(prefs.getBoolean("show_message_" + appWidgetId, false)) {
            if(sb.length() > 0) sb.append("\n");
            sb.append(prefs.getString("message_" + appWidgetId, ""));
        }

        return sb.toString();
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        prefs.getAll();
        int bgColor = prefs.getInt("bg_color_" + appWidgetId, ContextCompat.getColor(context, R.color.blue));
        int textColor = prefs.getInt("text_color_" + appWidgetId, Color.WHITE);
        String widgetName = prefs.getString("name_" + appWidgetId, "Send SMS");
        String number = prefs.getString("number_" + appWidgetId, "");
        String displayText = TextUtils.isEmpty(widgetName) ? "Send SMS" : widgetName;
        int subId = prefs.getInt("subId_" + appWidgetId, -1);
        if (subId == -1 || !isValidSubId(context, subId)) {
            subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        // Исправленный вызов метода с передачей context
        String displayNumber = getContactName(context, number);
        String buttonText = buildButtonText(context, prefs, appWidgetId);

        // Получаем сохраненный размер
        String size = prefs.getString("size_" + appWidgetId, "2x1");
        int layoutRes;

        // Выбираем layout в зависимости от размера
        switch(size) {
            case "1x1": layoutRes = R.layout.widget_1x1; break;
            case "2x1": layoutRes = R.layout.widget_2x1; break;
            case "2x2": layoutRes = R.layout.widget_2x2; break;
            default: layoutRes = R.layout.widget_2x1;
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), layoutRes);
        views.setTextViewText(R.id.widget_button, buttonText);
        views.setInt(R.id.widget_button, "setBackgroundColor", bgColor);
        views.setTextColor(R.id.widget_button, textColor);
        Log.d("WIDGET_COLORS", "BG Color: " + bgColor + " Text Color: " + textColor);
        Intent intent = new Intent(context, SmsWidget.class);
        intent.setAction(ACTION_SEND_SMS);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_button, pendingIntent);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static boolean isValidSubId(Context context, int subId) {
        SubscriptionManager sm = (SubscriptionManager)
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        return sm.getActiveSubscriptionInfo(subId) != null;
    }

    private static void saveWidgetSize(Context context, int appWidgetId, String size) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putString("size_" + appWidgetId, size).apply();
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context,
                                          AppWidgetManager appWidgetManager,
                                          int appWidgetId,
                                          Bundle newOptions) {
        // Конвертация пикселей в dp
        float density = context.getResources().getDisplayMetrics().density;
        int minWidthPx = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int minHeightPx = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);

        int minWidthDp = (int)(minWidthPx / density);
        int minHeightDp = (int)(minHeightPx / density);

        String newSize = calculateSize(minWidthDp, minHeightDp);
        saveWidgetSize(context, appWidgetId, newSize);

        // Принудительное обновление
        updateAppWidget(context, appWidgetManager, appWidgetId);
    }

    private static String calculateSize(int widthDp, int heightDp) {
        if(widthDp >= 250 && heightDp >= 250) return "2x2";
        if(widthDp >= 250 && heightDp >= 110) return "2x1";
        if(widthDp >= 110 && heightDp >= 110) return "1x1";
        return "custom";
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_SEND_SMS.equals(intent.getAction())) {
            int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            if (widgetId != -1) {
                sendSms(context, widgetId);
            }
        }
    }

    private void sendSms(Context context, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int subId = prefs.getInt("subId_" + widgetId, -1);
        String number = prefs.getString("number_" + widgetId, "");
        String message = prefs.getString("message_" + widgetId, "");

        new Thread(() -> {
            try {
                SmsManager smsManager = SmsManager.getSmsManagerForSubscriptionId(subId);
                smsManager.sendTextMessage(number, null, message, null, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}