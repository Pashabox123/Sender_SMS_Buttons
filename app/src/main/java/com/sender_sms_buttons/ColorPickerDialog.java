// ColorPickerDialog.java - полный исправленный код
package com.sender_sms_buttons;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

public class ColorPickerDialog extends Dialog {
    private OnColorSelectedListener listener;
    private int initialColor;

    public ColorPickerDialog(@NonNull Context context, int initialColor, OnColorSelectedListener listener) {
        super(context, R.style.ColorPickerDialog);
        this.initialColor = initialColor;
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_color_picker);

        LinearLayout colorList = findViewById(R.id.color_list);
        int itemHeight = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                56, // Высота строки 56dp (~8mm)
                getContext().getResources().getDisplayMetrics()
        );
        Button customColorBtn = findViewById(R.id.custom_color_btn);

        int[] colors = {
                // Основные цвета и градиенты
                Color.BLACK, Color.WHITE,
                Color.rgb(255, 0, 0),     // Красный
                Color.rgb(0, 255, 0),     // Зеленый
                Color.rgb(0, 0, 255),     // Синий

                // Градиенты красного
                Color.rgb(139, 0, 0),     // Темно-красный
                Color.rgb(255, 99, 71),   // Томатный
                Color.rgb(220, 20, 60),   // Малиновый

                // Градиенты синего
                Color.rgb(0, 191, 255),   // Глубокий небесный
                Color.rgb(30, 144, 255),   // Защитно-синий
                Color.rgb(70, 130, 180),   // Стальной синий

                // Градиенты зеленого
                Color.rgb(34, 139, 34),    // Лесной зеленый
                Color.rgb(50, 205, 50),    // Лаймовый
                Color.rgb(152, 251, 152),  // Бледно-зеленый

                // Фиолетовые оттенки
                Color.rgb(128, 0, 128),    // Пурпурный
                Color.rgb(147, 112, 219),  // Средний фиолетовый
                Color.rgb(138, 43, 226),   // Сине-фиолетовый

                // Оранжевые оттенки
                Color.rgb(255, 165, 0),    // Оранжевый
                Color.rgb(255, 140, 0),    // Темно-оранжевый
                Color.rgb(255, 69, 0),     // Красно-оранжевый

                // Желтые оттенки
                Color.rgb(255, 215, 0),    // Золотой
                Color.rgb(255, 255, 0),    // Ярко-желтый
                Color.rgb(240, 230, 140),  // Хаки

                // Розовые оттенки
                Color.rgb(255, 192, 203),  // Розовый
                Color.rgb(255, 105, 180),  // Ярко-розовый
                Color.rgb(219, 112, 147),  // Бледно-фиолетовый красный

                // Коричневые оттенки
                Color.rgb(165, 42, 42),    // Коричневый
                Color.rgb(139, 69, 19),    // Седло-коричневый
                Color.rgb(210, 105, 30),   // Шоколадный

                // Серые оттенки
                Color.rgb(105, 105, 105),  // Темно-серый
                Color.rgb(169, 169, 169),  // Темно-серый (web)
                Color.rgb(192, 192, 192),  // Серебряный

                // Неоновые цвета
                Color.rgb(224, 255, 255),  // Неоновый голубой
                Color.rgb(255, 228, 181),  // Неоновый желтый
                Color.rgb(255, 182, 193),  // Неоновый розовый

                // Дополнительные цвета
                Color.rgb(0, 255, 255),    // Циан
                Color.rgb(255, 0, 255),    // Маджента
                Color.rgb(127, 255, 212),  // Аквамарин
                Color.rgb(240, 128, 128),  // Светло-коралловый
                Color.rgb(218, 165, 32),   // Золотистый
                Color.rgb(106, 90, 205),   // Сланцево-синий
                Color.rgb(123, 104, 238),  // Средний сланцево-синий
                Color.rgb(135, 206, 235),  // Голубой
                Color.rgb(176, 196, 222),  // Светло-стальной синий
                Color.rgb(95, 158, 160),   // Кадетский синий
                Color.rgb(255, 228, 196),  // Бисквитный
                Color.rgb(255, 222, 173),  // Навахо-белый
                Color.rgb(210, 180, 140)   // Тан
        };

        for (int color : colors) {
            View colorItem = new View(getContext());
            colorItem.setBackgroundColor(color);

            // Параметры элемента
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    itemHeight
            );
            params.setMargins(0, 4, 0, 4); // Вертикальные отступы

            // Индикатор выбора
            colorItem.setBackground(new GradientDrawable() {{
                setColor(color);
                setStroke(2, Color.BLACK);
                setCornerRadius(8);
            }});

            colorItem.setLayoutParams(params);
            colorItem.setOnClickListener(v -> {
                if (listener != null) listener.onColorSelected(color);
                dismiss();
            });

            colorList.addView(colorItem);
        }

        // Убираем кнопку кастомного цвета
        customColorBtn.setVisibility(View.GONE);
    }

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }
}