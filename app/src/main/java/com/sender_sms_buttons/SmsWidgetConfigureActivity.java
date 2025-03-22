package com.sender_sms_buttons;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Bundle;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class SmsWidgetConfigureActivity extends AppCompatActivity {
    private int mAppWidgetId;
    private Spinner mSimSpinner;
    private EditText mWidgetNameEdit;
    private EditText mNumberEdit;
    private EditText mMessageEdit;
    private List<SubscriptionInfo> mSubscriptions;
    private static final int PICK_CONTACT_REQUEST = 1001;
    private int selectedBgColor = Color.BLUE;
    private int selectedTextColor = Color.WHITE;
    @Nullable
    private Button btnBgColor;
    private Button btnTextColor;
    private String selectedSize;

    private SharedPreferences prefs;
    private SharedPreferences.Editor prefsEditor;
    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final int PERM_PHONE_STATE = 102;
    private int mSavedSubId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_configure);

        // Получаем ID виджета
        Intent intent = getIntent();
        mAppWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
        );

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        prefs = getSharedPreferences(SmsWidget.PREFS_NAME, MODE_PRIVATE);
        prefsEditor = prefs.edit();
        mSimSpinner = findViewById(R.id.sim_spinner);
        mWidgetNameEdit = findViewById(R.id.widget_name_edit);
        mNumberEdit = findViewById(R.id.number_edit);
        mMessageEdit = findViewById(R.id.message_edit);
        CheckBox cbName = findViewById(R.id.cb_show_name);
        CheckBox cbPhone = findViewById(R.id.cb_show_phone);
        CheckBox cbMessage = findViewById(R.id.cb_show_message);
        Button saveButton = findViewById(R.id.save_button);
        Button btnPickContact = findViewById(R.id.btn_pick_contact);
        btnPickContact.setOnClickListener(v -> pickContact());
        mSavedSubId = prefs.getInt("subId_" + mAppWidgetId, -1);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.widget_sizes, android.R.layout.simple_spinner_item);

        btnBgColor = findViewById(R.id.btn_bg_color);
        btnTextColor = findViewById(R.id.btn_text_color);

        btnBgColor.setOnClickListener(v -> showColorPicker(true));
        btnTextColor.setOnClickListener(v -> showColorPicker(false));
        btnBgColor.setBackgroundColor(selectedBgColor);
        btnTextColor.setBackgroundColor(selectedTextColor);
        Button btnSyncTime = findViewById(R.id.btn_sync_time);
        btnSyncTime.setOnClickListener(v -> syncTime());

        if (btnBgColor == null || btnTextColor == null) {
            throw new IllegalStateException("Кнопки цвета должны быть объявлены в XML");
        }

        mAppWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) finish();


        saveButton.setOnClickListener(v -> saveConfig());

        checkPermissions();

        String savedName = prefs.getString("name_" + mAppWidgetId, "");
        String savedNumber = prefs.getString("number_" + mAppWidgetId, "");
        String savedMessage = prefs.getString("message_" + mAppWidgetId, "");
        cbName.setChecked(prefs.getBoolean("show_name_" + mAppWidgetId, true));
        cbPhone.setChecked(prefs.getBoolean("show_phone_" + mAppWidgetId, true));
        cbMessage.setChecked(prefs.getBoolean("show_message_" + mAppWidgetId, false));
        selectedBgColor = prefs.getInt("bg_color_" + mAppWidgetId, ContextCompat.getColor(this, R.color.blue));
        selectedTextColor = prefs.getInt("text_color_" + mAppWidgetId, Color.WHITE);
        int savedSubId = prefs.getInt("subId_" + mAppWidgetId, -1);

        // Поиск позиции SIM по subId
        if (savedSubId != -1 && mSubscriptions != null) {
            for (int i = 0; i < mSubscriptions.size(); i++) {
                if (mSubscriptions.get(i).getSubscriptionId() == savedSubId) {
                    mSimSpinner.setSelection(i);
                    break;
                }
            }
        }

        updateColorButtons();
        mWidgetNameEdit.setText(savedName);
        mNumberEdit.setText(savedNumber);
        mMessageEdit.setText(savedMessage);

        loadSubscriptions();

    }

    private void syncTime() {
        if (!validateInput()) return;

        String message = generateTimeSyncMessage();
        sendSyncSMS(message);
    }

    private boolean validateInput() {
        if (mSubscriptions == null || mSubscriptions.isEmpty()) {
            Toast.makeText(this, "Выберите SIM-карту!", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (mNumberEdit.getText().toString().isEmpty()) {
            Toast.makeText(this, "Введите номер!", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private String generateTimeSyncMessage() {
        Calendar calendar = Calendar.getInstance();

        // Получаем компоненты даты
        int year = calendar.get(Calendar.YEAR) % 100; // Последние две цифры года
        int month = calendar.get(Calendar.MONTH) + 1; // Месяц от 1 до 12
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        // Корректировка дня недели: 1-Пн ... 7-Вс
        int rawDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int correctedDay = (rawDayOfWeek == Calendar.SUNDAY) ? 7 : rawDayOfWeek - 1;

        // Форматируем компоненты с ведущими нулями
        String formattedDate = String.format(Locale.US,
                "%02d%02d%02d%02d%02d%d",
                year, month, day, hour, minute, correctedDay
        );

        return "#153#" + formattedDate + "#";
    }

    private void sendSyncSMS(String message) {
        int subId = mSubscriptions.get(mSimSpinner.getSelectedItemPosition()).getSubscriptionId();
        String number = mNumberEdit.getText().toString();

        try {
            SmsManager.getSmsManagerForSubscriptionId(subId)
                    .sendTextMessage(number, null, message, null, null);
            Toast.makeText(this, "Текущее время отправлено!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка отправки: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateColorButtons() {
        btnBgColor.setBackgroundTintList(ColorStateList.valueOf(selectedBgColor)); // Используем Tint для Material Design
        btnTextColor.setBackgroundTintList(ColorStateList.valueOf(selectedTextColor));
    }

    private void showColorPicker(boolean isBackgroundColor) {
        int currentColor = isBackgroundColor ? selectedBgColor : selectedTextColor;
        new ColorPickerDialog(this, currentColor, color -> {
            if (isBackgroundColor) {
                selectedBgColor = color; // Обновляем цвет фона
                btnBgColor.setBackgroundColor(color);
                prefsEditor.putInt("bg_color_" + mAppWidgetId, color);
            } else {
                selectedTextColor = color; // Обновляем цвет текста
                btnTextColor.setBackgroundColor(color);
                prefsEditor.putInt("text_color_" + mAppWidgetId, color);
            }
            prefsEditor.apply(); // Сохраняем изменения
            updateColorButtons();

            // Немедленное обновление виджета
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
            SmsWidget.updateAppWidget(this, appWidgetManager, mAppWidgetId);
        }).show();
    }

    private void checkPermissions() {
        // Для Android 6.0+
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.SEND_SMS},
                    PERMISSION_REQUEST_CODE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.READ_PHONE_STATE,
                                Manifest.permission.READ_PHONE_NUMBERS // Для API 26+
                        },
                        PERM_PHONE_STATE
                );
            } else {
                loadSubscriptions(); // Загрузить если разрешение уже есть
            }
        } else {
            loadSubscriptions(); // Для версий ниже 6.0
        }
    }

    private void loadSubscriptions() {
        SubscriptionManager subManager = (SubscriptionManager) getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE);
        if (subManager == null) {
            Toast.makeText(this, "Ошибка доступа к SIM-картам", Toast.LENGTH_SHORT).show();
            return;
        }

        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, PERMISSION_REQUEST_CODE);
            return;
        }

        if (mSavedSubId != -1) {
            new Handler().post(() -> { // Отложенная установка после обновления адаптера
                for (int i = 0; i < mSubscriptions.size(); i++) {
                    if (mSubscriptions.get(i).getSubscriptionId() == mSavedSubId) {
                        mSimSpinner.setSelection(i);
                        break;
                    }
                }
            });
        }

        List<SubscriptionInfo> subs = subManager.getActiveSubscriptionInfoList();
        if (subs != null && !subs.isEmpty()) {
            mSubscriptions = subs;
            mSimSpinner.setAdapter(new SimAdapter(this, mSubscriptions));

            // Восстановление выбора после загрузки данных
            new Handler().post(() -> {
                if (mSavedSubId != -1) {
                    for (int i = 0; i < mSubscriptions.size(); i++) {
                        if (mSubscriptions.get(i).getSubscriptionId() == mSavedSubId) {
                            mSimSpinner.setSelection(i);
                            break;
                        }
                    }
                }
            });
        }
    }

    public class SimAdapter extends ArrayAdapter<SubscriptionInfo> {
        private final Context context;
        private final List<SubscriptionInfo> subscriptions;

        public SimAdapter(Context context, List<SubscriptionInfo> subs) {
            super(context, R.layout.sim_spinner_item, subs);
            this.context = context;
            this.subscriptions = subs;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return createView(position, convertView, parent);
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            return createView(position, convertView, parent);
        }

        private View createView(int position, View convertView, ViewGroup parent) {
            View view = convertView != null
                    ? convertView
                    : LayoutInflater.from(context).inflate(R.layout.sim_spinner_item, parent, false);

            SubscriptionInfo info = subscriptions.get(position);

            TextView slot = view.findViewById(R.id.sim_slot);
            StringBuilder slotText = new StringBuilder();
            slotText.append("Слот ").append(info.getSimSlotIndex() + 1);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                slotText.append(info.isEmbedded() ? " (eSIM)" : " (Физическая)");
            }

            slot.setText(slotText.toString());
            TextView title = view.findViewById(R.id.sim_title);
            TextView number = view.findViewById(R.id.sim_number);
            ImageView icon = view.findViewById(R.id.sim_icon);



            // Название оператора
            title.setText(info.getCarrierName());

            // Номер телефона (если доступен)
            String phoneNumber = getFormattedNumber(subscriptions.get(position));
            number.setText(phoneNumber != null
                    ? phoneNumber
                    : "Номер недоступен");

            // Иконка оператора (API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                icon.setImageBitmap(info.createIconBitmap(context));
            } else {
                icon.setImageResource(R.drawable.ic_sim_card);
            }

            return view;
        }

        private String getFormattedNumber(SubscriptionInfo info) {
            String number = "";
            Context context = getContext();

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    number = info.getNumber();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        if (info.isEmbedded()) {
                            return number + " (eSIM)";
                        }
                    }
                } else {
                    TelephonyManager tm = (TelephonyManager)
                            context.getSystemService(Context.TELEPHONY_SERVICE);
                    if (tm != null) {
                        number = tm.getLine1Number();
                    }
                }
            } catch (SecurityException e) {
                Log.e("SIM", "Permission error: " + e.getMessage());
            }

            return (number == null || number.isEmpty())
                    ? "Номер недоступен"
                    : PhoneNumberUtils.formatNumber(number, "RU");
        }
    }

    private void saveConfig() {
        CheckBox cbName = findViewById(R.id.cb_show_name);
        CheckBox cbPhone = findViewById(R.id.cb_show_phone);
        CheckBox cbMessage = findViewById(R.id.cb_show_message);
        String name = mWidgetNameEdit.getText().toString();
        int bgColor = selectedBgColor;
        if (mSubscriptions == null || mSubscriptions.isEmpty()) {
            Toast.makeText(this, "SIM-карты не загружены", Toast.LENGTH_SHORT).show();
            return;
        }
        int selectedSim = mSimSpinner.getSelectedItemPosition();
        if (selectedSim >= 0 && selectedSim < mSubscriptions.size()) {
            int subId = mSubscriptions.get(selectedSim).getSubscriptionId();
            prefsEditor.putInt("subId_" + mAppWidgetId, subId);
        }

        String number = mNumberEdit.getText().toString();
        String message = mMessageEdit.getText().toString();

        prefsEditor.putBoolean("show_name_" + mAppWidgetId, cbName.isChecked());
        prefsEditor.putBoolean("show_phone_" + mAppWidgetId, cbPhone.isChecked());
        prefsEditor.putBoolean("show_message_" + mAppWidgetId, cbMessage.isChecked());
        prefsEditor.putString("name_" + mAppWidgetId, name);
        prefsEditor.putString("number_" + mAppWidgetId, number);
        prefsEditor.putString("message_" + mAppWidgetId, message);
        prefsEditor.putInt("bg_color_" + mAppWidgetId, selectedBgColor);
        prefsEditor.putInt("text_color_" + mAppWidgetId, selectedTextColor);
        prefsEditor.apply();

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        int[] ids = {mAppWidgetId};
        Intent updateIntent = new Intent(this, SmsWidget.class);
        updateIntent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(updateIntent);
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);

        SmsWidget.updateAppWidget(this, appWidgetManager, mAppWidgetId);
        finish();
    }

    private void pickContact() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    PICK_CONTACT_REQUEST);
            return;
        }

        if (!hasContacts()) {
            Toast.makeText(this, "Нет контактов для выбора", Toast.LENGTH_SHORT).show();
            return;
        }

        startContactPicker();
    }
    private boolean hasContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PERMISSION_GRANTED) {
            return false;
        }

        ContentResolver resolver = getContentResolver();
        try (Cursor cursor = resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts._ID},
                null, null, null)) {
            return cursor != null && cursor.getCount() > 0;
        } catch (SecurityException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void startContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                ContactsContract.Contacts.CONTENT_URI);
        startActivityForResult(intent, PICK_CONTACT_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED) {
                loadSubscriptions(); // Перезагрузка после получения разрешения
            } else {
                Toast.makeText(this, "Для работы нужны разрешения", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CONTACT_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri contactUri = data.getData();
            String phoneNumber = getContactPhoneNumber(contactUri);

            if (phoneNumber == null) {
                Toast.makeText(this,
                        "У выбранного контакта нет номера телефона",
                        Toast.LENGTH_LONG).show();
                return;
            }

            mNumberEdit.setText(phoneNumber);
        }
    }

    private String getContactPhoneNumber(Uri contactUri) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PERMISSION_GRANTED) {
            return null;
        }
        String phoneNumber = null;
        ContentResolver resolver = getContentResolver();

        try (Cursor cursor = resolver.query(contactUri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow(
                        ContactsContract.Contacts._ID));

                try (Cursor phoneCursor = resolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{id},
                        null)) {

                    if (phoneCursor != null && phoneCursor.moveToFirst()) {
                        phoneNumber = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                        phoneNumber = normalizePhoneNumber(phoneNumber); // Фильтрация номера
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return phoneNumber;
    }

    // Фильтрация нечисловых символов
    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return null;
        return phoneNumber.replaceAll("[^+\\d]", "");
    }
}