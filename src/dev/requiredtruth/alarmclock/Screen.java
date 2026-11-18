package dev.requiredtruth.alarmclock;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

abstract class Screen extends Activity {
    final int background = Color.rgb(16, 24, 38);
    final int surface = Color.rgb(28, 42, 60);
    final int mint = Color.rgb(154, 230, 207);
    final int muted = Color.rgb(179, 197, 214);
    final int ink = Color.rgb(237, 244, 252);

    int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    LinearLayout root() {
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        LinearLayout layout = column();
        layout.setPadding(dp(22), dp(20), dp(22), dp(16));
        layout.setBackgroundColor(background);
        return layout;
    }

    TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    Button button(String label, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? background : ink);
        button.setMinHeight(dp(52));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(0x403C6E65), rounded(primary ? mint : surface, 18), null));
        button.setOnClickListener(listener);
        return button;
    }

    void space(LinearLayout layout, int height) {
        layout.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }
}
