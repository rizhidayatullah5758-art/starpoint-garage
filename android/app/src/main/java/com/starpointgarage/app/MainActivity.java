package com.starpointgarage.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private String mode = "login";
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NativeUi.BG);
        getWindow().setNavigationBarColor(NativeUi.BG);
        if (AppSession.exists(this)) {
            route();
            return;
        }
        render();
    }

    private void route() {
        startActivity(new Intent(this, InternalActivity.class));
        finish();
    }

    private void render() {
        LinearLayout page = NativeUi.page(this);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        NativeUi.gap(this, page, 12);

        page.addView(BrandLogo.view(this, 320), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, NativeUi.dp(this, 105)));
        NativeUi.gap(this, page, 8);

        TextView internal = NativeUi.center(this, "APLIKASI INTERNAL", 11, true);
        internal.setTextColor(NativeUi.RED);
        internal.setLetterSpacing(.18f);
        page.addView(internal, NativeUi.match(this));
        NativeUi.gap(this, page, 24);

        LinearLayout card = NativeUi.redCard(this);
        card.setPadding(NativeUi.dp(this, 20), NativeUi.dp(this, 22), NativeUi.dp(this, 20), NativeUi.dp(this, 22));

        String title = "login".equals(mode) ? "Masuk" : "activate".equals(mode) ? "Aktivasi" : "Lupa Password";
        TextView h = NativeUi.text(this, title, 30, true);
        card.addView(h);
        NativeUi.gap(this, card, 20);

        EditText email = NativeUi.input(this, "Email");
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        card.addView(email, NativeUi.match(this));

        EditText password = null;
        if (!"forgot_password".equals(mode)) {
            NativeUi.gap(this, card, 12);
            password = NativeUi.password(this, "Password");
            card.addView(password, NativeUi.match(this));
        }

        NativeUi.gap(this, card, 18);
        Button submit = NativeUi.button(this,
                "login".equals(mode) ? "Masuk" : "activate".equals(mode) ? "Aktifkan Akun" : "Kirim Link Reset", true);
        card.addView(submit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, NativeUi.dp(this, 54)));

        status = NativeUi.muted(this, "", 12);
        status.setGravity(Gravity.CENTER);
        NativeUi.margin(this, status, 12);
        card.addView(status);

        NativeUi.gap(this, card, 6);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        Button login = NativeUi.textButton(this, "Masuk");
        Button activate = NativeUi.textButton(this, "Aktivasi");
        Button forgot = NativeUi.textButton(this, "Lupa Password");
        actions.addView(login, NativeUi.weight(this, 42, 1));
        actions.addView(activate, NativeUi.weight(this, 42, 1));
        actions.addView(forgot, NativeUi.weight(this, 42, 1));
        card.addView(actions, NativeUi.match(this));

        page.addView(card, NativeUi.match(this));
        NativeUi.gap(this, page, 18);
        TextView role = NativeUi.chip(this, "OWNER / ADMIN  •  TEKNISI", NativeUi.RED);
        role.setGravity(Gravity.CENTER);
        page.addView(role, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText p = password;
        submit.setOnClickListener(v -> {
            String e = email.getText().toString().trim().toLowerCase(Locale.ROOT);
            String pass = p == null ? "" : p.getText().toString();
            if (e.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
                status.setText("Email tidak valid.");
                return;
            }
            if (!"forgot_password".equals(mode) && pass.isEmpty()) {
                status.setText("Password wajib diisi.");
                return;
            }
            authenticate(submit, e, pass);
        });

        login.setOnClickListener(v -> { mode = "login"; render(); });
        activate.setOnClickListener(v -> { mode = "activate"; render(); });
        forgot.setOnClickListener(v -> { mode = "forgot_password"; render(); });

        setContentView(NativeUi.scroll(this, page));
    }

    private void authenticate(Button submit, String email, String password) {
        submit.setEnabled(false);
        submit.setAlpha(.6f);
        status.setText("Memproses…");
        final String action = mode;
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("action", action);
                body.put("email", email);
                if (!"forgot_password".equals(action)) body.put("password", password);

                JSONObject out = ApiClient.asObject(ApiClient.function("staff-auth", body));
                if ("forgot_password".equals(action)) {
                    runOnUiThread(() -> {
                        submit.setEnabled(true);
                        submit.setAlpha(1f);
                        status.setText(out.optString("message", "Jika akun terdaftar, link reset akan dikirim."));
                    });
                    return;
                }

                JSONObject session = out.getJSONObject("session");
                JSONObject staff = out.optJSONObject("staff");
                if (staff == null) throw new Exception("STAFF_PROFILE_INACTIVE");
                String role = staff.optString("role", "");
                if (!"owner_admin".equals(role) && !"cashier_technician".equals(role)) {
                    throw new Exception("STAFF_PROFILE_INACTIVE");
                }
                staff.put("email", email);
                String type = "owner_admin".equals(role) ? "owner" : "technician";
                AppSession.save(this, type,
                        session.getString("access_token"), session.optString("refresh_token", ""), staff);
                runOnUiThread(this::route);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    submit.setEnabled(true);
                    submit.setAlpha(1f);
                    status.setText(NativeUi.errorText(e.getMessage()));
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}