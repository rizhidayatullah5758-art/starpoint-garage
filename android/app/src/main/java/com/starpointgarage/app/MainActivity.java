package com.starpointgarage.app;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private String portal = "member";
    private String action = "login";
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NativeUi.BG);
        getWindow().setNavigationBarColor(NativeUi.BG);

        if (AppSession.exists(this)) {
            routeExisting();
            return;
        }
        render();
    }

    private void routeExisting() {
        Class<?> cls = "staff".equals(AppSession.type(this)) ? StaffActivity.class : MemberActivity.class;
        startActivity(new Intent(this, cls));
        finish();
    }

    private void render() {
        root = NativeUi.page(this);
        root.addView(NativeUi.text(this, "STARPOINT GARAGE", 24, true));
        root.addView(NativeUi.muted(this, "Native Android App · Surabaya", 13));
        NativeUi.gap(this, root, 22);

        LinearLayout portals = new LinearLayout(this);
        portals.setOrientation(LinearLayout.HORIZONTAL);
        Button member = NativeUi.button(this, "Member", "member".equals(portal));
        Button staff = NativeUi.button(this, "Staff / Owner", "staff".equals(portal));
        portals.addView(member, new LinearLayout.LayoutParams(0, NativeUi.dp(this,48), 1));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(NativeUi.dp(this,8), 1);
        View spacer = new View(this); portals.addView(spacer, sp);
        portals.addView(staff, new LinearLayout.LayoutParams(0, NativeUi.dp(this,48), 1));
        member.setOnClickListener(v -> { portal = "member"; action = "login"; render(); });
        staff.setOnClickListener(v -> { portal = "staff"; action = "login"; render(); });
        root.addView(portals, NativeUi.match(this));
        NativeUi.gap(this, root, 18);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        String[] acts = "member".equals(portal)
                ? new String[]{"login","register","forgot_password"}
                : new String[]{"login","activate","forgot_password"};
        String[] names = "member".equals(portal)
                ? new String[]{"Login","Daftar","Lupa PW"}
                : new String[]{"Login","Aktivasi","Lupa PW"};
        for (int i=0;i<acts.length;i++) {
            Button b = NativeUi.button(this, names[i], acts[i].equals(action));
            String a = acts[i];
            b.setOnClickListener(v -> { action = a; render(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, NativeUi.dp(this,44),1);
            if (i>0) lp.leftMargin = NativeUi.dp(this,6);
            tabs.addView(b, lp);
        }
        root.addView(tabs, NativeUi.match(this));
        NativeUi.gap(this, root, 20);

        LinearLayout form = NativeUi.card(this);
        TextView title = NativeUi.text(this, titleText(), 20, true);
        form.addView(title);
        form.addView(NativeUi.muted(this, subtitleText(), 13));
        NativeUi.gap(this, form, 16);

        if ("member".equals(portal)) renderMemberForm(form); else renderStaffForm(form);

        status = NativeUi.muted(this, "", 13);
        NativeUi.margin(this, status, 14);
        form.addView(status);
        root.addView(form, NativeUi.match(this));

        NativeUi.gap(this, root, 18);
        TextView footer = NativeUi.muted(this,
                "Aplikasi native ini terhubung langsung ke backend Starpoint Garage. Tidak menggunakan WebView.", 12);
        root.addView(footer);

        setContentView(NativeUi.scroll(this, root));
    }

    private String titleText() {
        if ("member".equals(portal)) {
            if ("register".equals(action)) return "Daftar Member";
            if ("forgot_password".equals(action)) return "Reset Password";
            return "Login Member";
        }
        if ("activate".equals(action)) return "Aktivasi Staff";
        if ("forgot_password".equals(action)) return "Reset Password Staff";
        return "Login Staff / Owner";
    }

    private String subtitleText() {
        return "member".equals(portal)
                ? "Gunakan akun Starpoint Garage."
                : "Akses operasional Teknisi dan Owner/Admin.";
    }

    private void renderMemberForm(LinearLayout form) {
        if ("forgot_password".equals(action)) {
            EditText email = NativeUi.input(this, "Email");
            form.addView(email, NativeUi.match(this));
            NativeUi.gap(this, form, 10);
            Button submit = NativeUi.button(this, "Kirim link reset", true);
            form.addView(submit, NativeUi.match(this));
            submit.setOnClickListener(v -> authMember(submit, new JSONObjectBuilder()
                    .put("action","forgot_password").put("businessCode","starpoint_garage")
                    .put("email",email.getText().toString().trim()).build()));
            return;
        }

        EditText fullName = null, email = null, birth = null, phone = null, referral = null;
        if ("register".equals(action)) {
            fullName = NativeUi.input(this, "Nama lengkap");
            email = NativeUi.input(this, "Email");
            email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            birth = NativeUi.input(this, "Tanggal lahir");
            birth.setFocusable(false);
            birth.setClickable(true);
            birth.setOnClickListener(v -> showBirthDatePicker(birth));
            phone = NativeUi.input(this, "No. WhatsApp (opsional)");
            phone.setInputType(InputType.TYPE_CLASS_PHONE);
            referral = NativeUi.input(this, "Kode referral (opsional)");
            form.addView(fullName, NativeUi.match(this)); NativeUi.gap(this,form,8);
        }

        EditText username = NativeUi.input(this, "Username");
        username.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        form.addView(username, NativeUi.match(this));
        if ("register".equals(action)) {
            form.addView(NativeUi.muted(this, "4–20 karakter: huruf, angka, titik, atau underscore.", 11));
        }
        NativeUi.gap(this,form,8);

        if ("register".equals(action)) {
            form.addView(email, NativeUi.match(this)); NativeUi.gap(this,form,8);
        }

        EditText password = NativeUi.password(this, "Password");
        form.addView(password, NativeUi.match(this));
        if ("register".equals(action)) {
            form.addView(NativeUi.muted(this, "Minimal 8 karakter dan wajib berisi huruf + angka.", 11));
        }

        if ("register".equals(action)) {
            NativeUi.gap(this,form,8); form.addView(birth, NativeUi.match(this));
            form.addView(NativeUi.muted(this, "Ketuk untuk memilih tanggal lahir.", 11));
            NativeUi.gap(this,form,8); form.addView(phone, NativeUi.match(this));
            NativeUi.gap(this,form,8); form.addView(referral, NativeUi.match(this));
        }

        NativeUi.gap(this, form, 12);
        Button submit = NativeUi.button(this, "register".equals(action) ? "Buat akun" : "Masuk", true);
        form.addView(submit, NativeUi.match(this));

        EditText fFullName = fullName, fEmail = email, fBirth = birth, fPhone = phone, fReferral = referral;
        submit.setOnClickListener(v -> {
            String usernameValue = username.getText().toString().trim().toLowerCase(Locale.ROOT);
            String passwordValue = password.getText().toString();

            if ("register".equals(action)) {
                String validation = validateMemberRegistration(
                        fFullName.getText().toString().trim(),
                        usernameValue,
                        fEmail.getText().toString().trim(),
                        passwordValue,
                        fBirth.getText().toString().trim()
                );
                if (validation != null) {
                    status.setText(validation);
                    return;
                }
            }

            JSONObjectBuilder b = new JSONObjectBuilder().put("action",action).put("businessCode","starpoint_garage")
                    .put("username",usernameValue)
                    .put("password",passwordValue);
            if ("register".equals(action)) {
                b.put("fullName",fFullName.getText().toString().trim())
                        .put("email",fEmail.getText().toString().trim().toLowerCase(Locale.ROOT))
                        .put("birthDate",fBirth.getText().toString().trim())
                        .put("phone",fPhone.getText().toString().trim())
                        .put("referralCode",fReferral.getText().toString().trim());
            }
            authMember(submit, b.build());
        });
    }

    private String validateMemberRegistration(String fullName, String username, String email, String password, String birthDate) {
        if (fullName.isEmpty()) return "Nama lengkap wajib diisi.";
        if (!username.matches("^[a-z0-9._]{4,20}$")) {
            return "Username harus 4–20 karakter dan hanya boleh huruf, angka, titik, atau underscore.";
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return "Format email tidak valid.";
        }
        if (password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            return "Password minimal 8 karakter dan harus berisi huruf + angka.";
        }
        if (!birthDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return "Pilih tanggal lahir terlebih dahulu.";
        }
        return null;
    }

    private void showBirthDatePicker(EditText target) {
        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR) - 20;
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            target.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d));
        }, year, month, day);
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void renderStaffForm(LinearLayout form) {
        EditText email = NativeUi.input(this, "Email staff");
        form.addView(email, NativeUi.match(this));

        if ("forgot_password".equals(action)) {
            NativeUi.gap(this, form, 10);
            Button submit = NativeUi.button(this, "Kirim link reset", true);
            form.addView(submit, NativeUi.match(this));
            submit.setOnClickListener(v -> authStaff(submit, new JSONObjectBuilder()
                    .put("action","forgot_password").put("email",email.getText().toString().trim()).build()));
            return;
        }

        NativeUi.gap(this, form, 8);
        EditText password = NativeUi.password(this, "Password");
        form.addView(password, NativeUi.match(this));
        NativeUi.gap(this, form, 12);
        Button submit = NativeUi.button(this, "activate".equals(action) ? "Aktifkan akun" : "Masuk", true);
        form.addView(submit, NativeUi.match(this));
        submit.setOnClickListener(v -> authStaff(submit, new JSONObjectBuilder()
                .put("action", action)
                .put("email", email.getText().toString().trim())
                .put("password", password.getText().toString()).build()));
    }

    private void authMember(Button submit, JSONObject body) {
        busy(submit,true);
        io.execute(() -> {
            try {
                JSONObject out = ApiClient.asObject(ApiClient.function("member-auth", body));
                if ("forgot_password".equals(action)) {
                    runOnUiThread(() -> {
                        busy(submit,false);
                        status.setText(out.optString("message","Jika email terdaftar, link reset akan dikirim."));
                    });
                    return;
                }
                JSONObject session = out.getJSONObject("session");
                JSONObject member = out.optJSONObject("member");
                AppSession.save(this,"member",session.getString("access_token"),session.optString("refresh_token",""),
                        member == null ? new JSONObject() : member);
                runOnUiThread(this::routeExisting);
            } catch (Exception e) {
                runOnUiThread(() -> { busy(submit,false); status.setText(NativeUi.errorText(e.getMessage())); });
            }
        });
    }

    private void authStaff(Button submit, JSONObject body) {
        busy(submit,true);
        io.execute(() -> {
            try {
                JSONObject out = ApiClient.asObject(ApiClient.function("staff-auth", body));
                if ("forgot_password".equals(action)) {
                    runOnUiThread(() -> {
                        busy(submit,false);
                        status.setText(out.optString("message","Jika akun terdaftar, link reset akan dikirim."));
                    });
                    return;
                }
                JSONObject session = out.getJSONObject("session");
                JSONObject staff = out.optJSONObject("staff");
                AppSession.save(this,"staff",session.getString("access_token"),session.optString("refresh_token",""),
                        staff == null ? new JSONObject() : staff);
                runOnUiThread(this::routeExisting);
            } catch (Exception e) {
                runOnUiThread(() -> { busy(submit,false); status.setText(NativeUi.errorText(e.getMessage())); });
            }
        });
    }

    private void busy(Button b, boolean on) {
        b.setEnabled(!on);
        b.setAlpha(on ? .55f : 1f);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    static class JSONObjectBuilder {
        final JSONObject o = new JSONObject();
        JSONObjectBuilder put(String k, Object v) {
            try { o.put(k, v == null ? JSONObject.NULL : v); } catch (Exception ignored) {}
            return this;
        }
        JSONObject build() { return o; }
    }
}
