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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.Locale;

public final class NativeUi {
    public static final int BG = Color.rgb(5, 5, 6);
    public static final int SURFACE = Color.rgb(17, 17, 19);
    public static final int SURFACE_2 = Color.rgb(25, 25, 28);
    public static final int LINE = Color.rgb(61, 61, 66);
    public static final int RED = Color.rgb(235, 30, 36);
    public static final int RED_DARK = Color.rgb(100, 8, 12);
    public static final int WHITE = Color.rgb(248, 248, 249);
    public static final int TEXT = WHITE;
    public static final int MUTED = Color.rgb(164, 164, 170);
    public static final int GREEN = Color.rgb(58, 210, 127);
    public static final int AMBER = Color.rgb(244, 160, 57);
    public static final int BLUE = Color.rgb(84, 160, 255);
    public static final int YELLOW = Color.rgb(255, 215, 0);

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

    public static GradientDrawable gradient(Context c, int[] colors, int radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setCornerRadius(dp(c, radius));
        if (strokeColor != 0) d.setStroke(dp(c, 1), strokeColor);
        return d;
    }

    public static TextView text(Context c, String value, float sp, boolean bold) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTextColor(TEXT);
        t.setTextSize(sp);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.create("sans", Typeface.BOLD));
        return t;
    }

    public static TextView muted(Context c, String value, float sp) {
        TextView t = text(c, value, sp, false);
        t.setTextColor(MUTED);
        return t;
    }

    public static TextView label(Context c, String value) {
        TextView t = text(c, value, 12, true);
        t.setTextColor(Color.rgb(205, 205, 210));
        return t;
    }

    public static TextView section(Context c, String value) {
        TextView t = text(c, value, 22, true);
        t.setPadding(0, dp(c, 2), 0, dp(c, 4));
        return t;
    }

    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(Color.rgb(112, 112, 118));
        e.setTextColor(TEXT);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setPadding(dp(c, 15), dp(c, 13), dp(c, 15), dp(c, 13));
        e.setBackground(gradient(c,
                new int[]{Color.rgb(20,20,22), Color.rgb(13,13,15)}, 13, LINE));
        return e;
    }

    public static EditText moneyInput(Context c, String hint) {
        EditText e = input(c, hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setBackground(gradient(c,
                new int[]{Color.rgb(55,12,15), Color.rgb(19,15,17)}, 13, RED));
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
        b.setTypeface(Typeface.create("sans", Typeface.BOLD));
        b.setTextColor(WHITE);
        if (primary) {
            b.setBackground(gradient(c,
                    new int[]{Color.rgb(246,34,40), Color.rgb(176,8,14)}, 13, Color.rgb(255,75,80)));
        } else {
            b.setBackground(gradient(c,
                    new int[]{Color.rgb(28,28,31), Color.rgb(15,15,17)}, 13, LINE));
        }
        b.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
        return b;
    }

    public static Button dangerButton(Context c, String label) {
        Button b = button(c, label, false);
        b.setTextColor(Color.rgb(255, 105, 108));
        b.setBackground(gradient(c,
                new int[]{Color.rgb(47,14,17), Color.rgb(20,13,14)}, 13, RED_DARK));
        return b;
    }

    public static Button textButton(Context c, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(RED);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setPadding(dp(c, 6), dp(c, 6), dp(c, 6), dp(c, 6));
        return b;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        l.setBackground(gradient(c,
                new int[]{Color.rgb(31,18,20), Color.rgb(15,15,17), Color.rgb(10,10,11)}, 18,
                Color.rgb(103,35,39)));
        return l;
    }

    public static LinearLayout redCard(Context c) {
        LinearLayout l = card(c);
        l.setBackground(gradient(c,
                new int[]{Color.rgb(80,15,19), Color.rgb(22,16,18), Color.rgb(11,11,12)}, 18, RED));
        return l;
    }

    public static LinearLayout miniCard(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 13), dp(c, 13), dp(c, 13), dp(c, 13));
        l.setBackground(gradient(c,
                new int[]{Color.rgb(29,20,22), Color.rgb(13,13,15)}, 15, Color.rgb(88,35,39)));
        return l;
    }

    public static TextView chip(Context c, String value, int accent) {
        TextView t = text(c, value, 11, true);
        t.setTextColor(accent);
        t.setGravity(Gravity.CENTER);
        int dark = Color.argb(255,
                Math.max(10, Color.red(accent) / 5),
                Math.max(10, Color.green(accent) / 5),
                Math.max(10, Color.blue(accent) / 5));
        t.setBackground(bg(c, dark, 999, accent));
        t.setPadding(dp(c, 11), dp(c, 6), dp(c, 11), dp(c, 6));
        return t;
    }

    public static Spinner spinner(Context c) {
        Spinner s = new Spinner(c);
        s.setPadding(dp(c, 10), dp(c, 7), dp(c, 10), dp(c, 7));
        s.setBackground(gradient(c,
                new int[]{Color.rgb(22,22,24), Color.rgb(13,13,15)}, 13, LINE));
        return s;
    }

    public static LinearLayout page(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 18), dp(c, 16), dp(c, 18), dp(c, 34));
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

    public static LinearLayout.LayoutParams weight(Context c, int heightDp, float weight) {
        return new LinearLayout.LayoutParams(0, heightDp <= 0 ? ViewGroup.LayoutParams.WRAP_CONTENT : dp(c, heightDp), weight);
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
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("id", "ID"));
        return "Rp" + nf.format(amount);
    }

    public static String paymentStatus(String status) {
        if ("paid".equals(status)) return "Lunas";
        if ("partial".equals(status)) return "DP";
        return "Belum Dibayar";
    }

    public static String workStatus(String status) {
        if ("completed".equals(status)) return "Pengerjaan Selesai";
        if ("in_progress".equals(status)) return "Proses Pengerjaan";
        return "Belum Dikerjakan";
    }

    public static String categoryLabel(String value) {
        if (value == null) return "-";
        switch (value) {
            case "small": return "Small";
            case "medium": return "Medium";
            case "large": return "Large";
            case "big_bike": return "Big Bike";
            case "luxury": return "Luxury";
            default: return value;
        }
    }

    public static String methodLabel(String value) {
        if ("cash".equals(value)) return "Cash";
        if ("qris_bri_manual".equals(value)) return "QRIS BRI";
        if ("bank_transfer_bri".equals(value)) return "Transfer BRI";
        return value == null ? "-" : value;
    }

    public static String errorText(String code) {
        if (code == null) return "Terjadi kesalahan.";
        String clean = code;
        int colon = clean.indexOf(':');
        if (colon > 0) clean = clean.substring(0, colon);
        switch (clean) {
            case "INVALID_CREDENTIALS": return "Email atau password salah.";
            case "INVALID_EMAIL": return "Format email tidak valid.";
            case "WEAK_PASSWORD": return "Password minimal 8 karakter, berisi huruf dan angka.";
            case "STAFF_NOT_ALLOWED": return "Email belum terdaftar untuk akses internal.";
            case "WRONG_STAFF_PORTAL": return "Role akun tidak sesuai.";
            case "ACCOUNT_EXISTS": return "Akun sudah aktif. Gunakan Masuk.";
            case "ACTIVATION_FAILED": return "Aktivasi akun gagal.";
            case "STAFF_PROFILE_INACTIVE": return "Akun staff tidak aktif.";
            case "SESSION_CREATE_FAILED": return "Sesi login gagal dibuat. Coba lagi.";
            case "TOO_MANY_REQUESTS": return "Terlalu banyak percobaan. Coba lagi nanti.";
            case "SERVER_BUSY": return "Server sedang sibuk. Coba lagi.";
            case "STAFF_REQUIRED": return "Akses staff diperlukan.";
            case "OWNER_REQUIRED": return "Akses Owner/Admin diperlukan.";
            case "MOTOR_NAME_REQUIRED": return "Nama motor wajib diisi.";
            case "SERVICE_REQUIRED": return "Pilih minimal satu layanan.";
            case "SERVICE_NOT_FOUND": return "Layanan tidak ditemukan.";
            case "MEMBER_QR_INVALID": return "QR Member tidak valid.";
            case "MEMBER_NOT_FOUND": return "Member tidak ditemukan.";
            case "INVALID_FINAL_TOTAL": return "Nominal harga tidak valid.";
            case "INVALID_PAYMENT_AMOUNT": return "Nominal pembayaran tidak valid.";
            case "PAYMENT_EXCEEDS_BALANCE": return "Pembayaran melebihi sisa tagihan.";
            case "TRANSACTION_ALREADY_PAID": return "Transaksi sudah lunas.";
            case "BOOKING_ALREADY_PAID": return "Booking sudah lunas.";
            case "TRANSACTION_NOT_FOUND": return "Transaksi tidak ditemukan.";
            case "BOOKING_NOT_FOUND": return "Booking tidak ditemukan.";
            case "INVALID_WORK_STATUS": return "Status pengerjaan tidak valid.";
            case "INVALID_STATUS_TRANSITION": return "Urutan status pengerjaan tidak valid.";
            case "PAYMENT_ALREADY_WAITING_VERIFICATION": return "Masih ada pembayaran menunggu verifikasi.";
            case "TOTAL_BELOW_AMOUNT_PAID": return "Total tidak boleh lebih kecil dari pembayaran yang sudah diterima.";
            case "CHECKIN_MINIMUM_INTERVAL": return "Check-in terlalu dekat dari check-in sebelumnya.";
            case "CHECKIN_TRANSACTION_REQUIRED": return "Check-in membutuhkan transaksi terkait.";
            case "HTTP_401": return "Sesi berakhir. Silakan masuk kembali.";
            default: return clean.replace('_', ' ');
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