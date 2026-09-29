package com.starpointgarage.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.Locale;

public final class NativeUi {
    public static final int BG = Color.rgb(8,8,8);
    public static final int SURFACE = Color.rgb(20,20,20);
    public static final int LINE = Color.rgb(48,48,48);
    public static final int TEXT = Color.rgb(245,245,245);
    public static final int MUTED = Color.rgb(155,155,155);

    private NativeUi() {}

    public static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable bg(Context c, int color, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radius));
        if (strokeColor != 0) d.setStroke(dp(c, 1), strokeColor);
        return d;
    }

    public static TextView text(Context c, String value, float sp, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextColor(TEXT);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    public static TextView muted(Context c, String value, float sp) {
        TextView t = text(c, value, sp, false);
        t.setTextColor(MUTED);
        return t;
    }

    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(105,105,105));
        e.setTextColor(TEXT);
        e.setTextSize(15);
        e.setPadding(dp(c,14), dp(c,12), dp(c,14), dp(c,12));
        e.setSingleLine(true);
        e.setBackground(bg(c, Color.rgb(16,16,16), 12, LINE));
        return e;
    }

    public static EditText password(Context c, String hint) {
        EditText e = input(c, hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    public static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? Color.BLACK : TEXT);
        b.setBackground(bg(c, primary ? Color.WHITE : Color.rgb(25,25,25), 12, primary ? 0 : LINE));
        b.setPadding(dp(c,12), dp(c,10), dp(c,12), dp(c,10));
        return b;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c,16), dp(c,16), dp(c,16), dp(c,16));
        l.setBackground(bg(c, SURFACE, 16, LINE));
        return l;
    }

    public static LinearLayout page(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c,18), dp(c,18), dp(c,18), dp(c,32));
        l.setBackgroundColor(BG);
        return l;
    }

    public static void gap(Context c, LinearLayout l, int dp) {
        View v = new View(c);
        l.addView(v, new LinearLayout.LayoutParams(1, NativeUi.dp(c, dp)));
    }

    public static LinearLayout.LayoutParams match(Context c) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public static void margin(Context c, View v, int top) {
        LinearLayout.LayoutParams p = match(c);
        p.topMargin = dp(c, top);
        v.setLayoutParams(p);
    }

    public static ScrollView scroll(Activity a, LinearLayout child) {
        ScrollView s = new ScrollView(a);
        s.setFillViewport(true);
        s.setBackgroundColor(BG);
        s.addView(child, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    public static String rupiah(int amount) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("id","ID"));
        return "Rp" + nf.format(amount);
    }

    public static String errorText(String code) {
        if (code == null) return "Terjadi kesalahan.";
        switch (code) {
            case "INVALID_CREDENTIALS": return "Username/email atau password salah.";
            case "ACCOUNT_SUSPENDED": return "Akun sedang disuspend.";
            case "WEAK_PASSWORD": return "Password minimal 8 karakter, berisi huruf dan angka.";
            case "USERNAME_TAKEN": return "Username sudah dipakai.";
            case "EMAIL_TAKEN": return "Email sudah terdaftar.";
            case "STAFF_NOT_ALLOWED": return "Email ini tidak terdaftar sebagai staff.";
            case "ACCOUNT_EXISTS": return "Akun staff sudah aktif. Gunakan Login.";
            case "PROFILE_INCOMPLETE": return "Lengkapi profil dan avatar sebelum booking.";
            case "PAYMENT_ALREADY_WAITING_VERIFICATION": return "Masih ada pembayaran yang menunggu verifikasi.";
            case "CHECKIN_MINIMUM_INTERVAL": return "Check-in terlalu dekat dari check-in sebelumnya.";
            case "CHECKIN_TRANSACTION_REQUIRED": return "Check-in ini membutuhkan booking/payment terkait.";
            default: return code.replace('_',' ');
        }
    }

    public static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_LONG).show();
    }

    public static TextView center(Context c, String text, float sp, boolean bold) {
        TextView t = NativeUi.text(c, text, sp, bold);
        t.setGravity(Gravity.CENTER);
        return t;
    }
}
