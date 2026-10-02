package com.starpointgarage.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.codescanner.GmsBarcodeScanner;
import com.google.android.gms.codescanner.GmsBarcodeScannerOptions;
import com.google.android.gms.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InternalActivity extends Activity {
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LinearLayout shell;
    private LinearLayout content;
    private LinearLayout nav;
    private JSONObject profile;
    private String role;
    private String currentScreen = "home";

    private JSONArray serviceCatalog = new JSONArray();
    private JSONArray priceCatalog = new JSONArray();

    private static final String[] CATEGORY_LABELS = {"Small", "Medium", "Large", "Big Bike", "Luxury"};
    private static final String[] CATEGORY_VALUES = {"small", "medium", "large", "big_bike", "luxury"};
    private static final String[] METHOD_LABELS = {"Cash", "QRIS BRI", "Transfer BRI"};
    private static final String[] METHOD_VALUES = {"cash", "qris_bri_manual", "bank_transfer_bri"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NativeUi.BG);
        getWindow().setNavigationBarColor(NativeUi.BG);

        if (!AppSession.exists(this)) {
            logout();
            return;
        }

        profile = AppSession.profile(this);
        role = profile.optString("role", "");
        if (!"owner_admin".equals(role) && !"cashier_technician".equals(role)) {
            logout();
            return;
        }

        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 21);
        }

        renderShell();
        if (isOwner()) showOwnerDashboard(); else showTechnicianHome();
        handler.postDelayed(notificationPoll, 5000);
    }

    private boolean isOwner() { return "owner_admin".equals(role); }

    private String token() throws Exception { return AppSession.ensureFresh(this); }

    private Object rpc(String name, JSONObject body) throws Exception {
        return ApiClient.rpc(name, body == null ? new JSONObject() : body, token());
    }

    private JSONArray get(String path) throws Exception { return ApiClient.getArray(path, token()); }

    private void renderShell() {
        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(NativeUi.BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(NativeUi.dp(this, 16), NativeUi.dp(this, 10), NativeUi.dp(this, 16), NativeUi.dp(this, 8));
        top.setBackground(NativeUi.gradient(this,
                new int[]{Color.rgb(20,7,9), Color.rgb(5,5,6)}, 0, 0));
        ImageView logo = BrandLogo.view(this, 190);
        top.addView(logo, new LinearLayout.LayoutParams(0, NativeUi.dp(this, 62), 1));
        TextView badge = NativeUi.chip(this, isOwner() ? "OWNER / ADMIN" : "TEKNISI", NativeUi.RED);
        top.addView(badge);
        shell.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, NativeUi.dp(this, 80)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = NativeUi.page(this);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(NativeUi.dp(this, 6), NativeUi.dp(this, 7), NativeUi.dp(this, 6), NativeUi.dp(this, 8));
        nav.setBackground(NativeUi.gradient(this,
                new int[]{Color.rgb(31,14,16), Color.rgb(8,8,9)}, 22, Color.rgb(73,31,34)));
        shell.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, NativeUi.dp(this, 70)));
        setContentView(shell);
    }

    private void buildNav(String active) {
        nav.removeAllViews();
        if (isOwner()) {
            navItem("Dashboard", "home", active, this::showOwnerDashboard);
            navItem("Transaksi", "transactions", active, () -> showOwnerTransactions(null));
            navItem("Member", "members", active, this::showOwnerMembers);
            navItem("Laporan", "reports", active, this::showOwnerReports);
            navItem("Akun", "account", active, this::showAccount);
        } else {
            navItem("Beranda", "home", active, this::showTechnicianHome);
            navItem("Transaksi", "new", active, () -> showNewTransaction(false));
            navItem("Pekerjaan", "work", active, () -> showWork(null));
            navItem("Booking", "booking", active, this::showBookings);
            navItem("Akun", "account", active, this::showAccount);
        }
    }

    private void navItem(String label, String key, String active, Runnable action) {
        Button b = NativeUi.button(this, label, key.equals(active));
        b.setTextSize(10);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        if (nav.getChildCount() > 0) lp.leftMargin = NativeUi.dp(this, 3);
        nav.addView(b, lp);
    }

    private void clear(String title, String screen) {
        currentScreen = screen;
        content.removeAllViews();
        content.addView(NativeUi.section(this, title));
        View line = new View(this);
        line.setBackgroundColor(NativeUi.RED);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(NativeUi.dp(this, 46), NativeUi.dp(this, 3));
        lp.topMargin = NativeUi.dp(this, 6);
        lp.bottomMargin = NativeUi.dp(this, 16);
        content.addView(line, lp);
        buildNav(screen);
    }

    private void addLoading(String text) {
        TextView t = NativeUi.muted(this, text, 13);
        t.setTag("loading");
        content.addView(t);
    }

    private void replaceLoadingWithError(Exception e) {
        View v = content.findViewWithTag("loading");
        if (v instanceof TextView) ((TextView) v).setText(NativeUi.errorText(e.getMessage()));
    }

    private void addStat(String label, String value, int accent) {
        LinearLayout c = NativeUi.miniCard(this);
        TextView dot = NativeUi.text(this, "●", 15, true);
        dot.setTextColor(accent);
        c.addView(dot);
        NativeUi.gap(this, c, 4);
        c.addView(NativeUi.muted(this, label, 11));
        TextView v = NativeUi.text(this, value, 22, true);
        c.addView(v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = NativeUi.dp(this, 4);
        lp.rightMargin = NativeUi.dp(this, 4);
        c.setLayoutParams(lp);
    }

    private LinearLayout statCard(String label, String value, int accent) {
        LinearLayout c = NativeUi.miniCard(this);
        TextView dot = NativeUi.text(this, "●", 13, true);
        dot.setTextColor(accent);
        c.addView(dot);
        c.addView(NativeUi.muted(this, label, 11));
        c.addView(NativeUi.text(this, value, 22, true));
        return c;
    }

    private void addStatGrid(JSONObject s, boolean owner) {
        String[] labels;
        String[] values;
        int[] accents = {NativeUi.RED, NativeUi.AMBER, NativeUi.GREEN, NativeUi.BLUE};
        if (owner) {
            labels = new String[]{"Omzet Hari Ini", "Transaksi", "Booking Aktif", "Belum Lunas"};
            values = new String[]{
                    NativeUi.rupiah(s.optInt("revenue_today")),
                    String.valueOf(s.optInt("transactions_today")),
                    String.valueOf(s.optInt("booking_active")),
                    NativeUi.rupiah(s.optInt("unpaid_total"))
            };
        } else {
            labels = new String[]{"Belum Dikerjakan", "Proses", "Selesai", "Booking Baru"};
            values = new String[]{
                    String.valueOf(s.optInt("pending_work")),
                    String.valueOf(s.optInt("in_progress")),
                    String.valueOf(s.optInt("completed_today")),
                    String.valueOf(s.optInt("new_bookings"))
            };
        }
        for (int row=0; row<2; row++) {
            LinearLayout r = new LinearLayout(this);
            for (int col=0; col<2; col++) {
                int i=row*2+col;
                LinearLayout card = statCard(labels[i], values[i], accents[i]);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                if (col>0) lp.leftMargin = NativeUi.dp(this, 8);
                r.addView(card, lp);
            }
            content.addView(r, NativeUi.match(this));
            if (row==0) NativeUi.gap(this, content, 8);
        }
    }

    private Button actionButton(String label, Runnable action) {
        Button b = NativeUi.button(this, label + "  ›", false);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void addQuickGrid(String[] labels, Runnable[] actions) {
        for (int i=0;i<labels.length;i+=2) {
            LinearLayout row = new LinearLayout(this);
            Button left = actionButton(labels[i], actions[i]);
            row.addView(left, new LinearLayout.LayoutParams(0, NativeUi.dp(this, 58), 1));
            if (i+1<labels.length) {
                Button right = actionButton(labels[i+1], actions[i+1]);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, NativeUi.dp(this, 58), 1);
                rp.leftMargin = NativeUi.dp(this, 8);
                row.addView(right, rp);
            }
            content.addView(row, NativeUi.match(this));
            NativeUi.gap(this, content, 8);
        }
    }

    // ---------------------- TECHNICIAN ----------------------

    private void showTechnicianHome() {
        clear("Beranda", "home");
        addLoading("Memuat…");
        io.execute(() -> {
            try {
                JSONObject summary = ApiClient.asObject(rpc("internal_staff_dashboard_summary", new JSONObject()));
                JSONArray active = ApiClient.asArray(rpc("internal_active_transactions", new JSONObject()));
                runOnUiThread(() -> {
                    content.removeAllViews();
                    content.addView(NativeUi.section(this, "Beranda"));
                    NativeUi.gap(this, content, 14);
                    addStatGrid(summary, false);
                    NativeUi.gap(this, content, 22);
                    content.addView(NativeUi.text(this, "Akses Cepat", 19, true));
                    NativeUi.gap(this, content, 10);
                    addQuickGrid(
                            new String[]{"Transaksi Baru", "Scan QR", "Pekerjaan", "Pembayaran", "Booking", "Notifikasi"},
                            new Runnable[]{
                                    () -> showNewTransaction(false),
                                    () -> showNewTransaction(true),
                                    () -> showWork(null),
                                    () -> showWork("payment"),
                                    this::showBookings,
                                    this::showNotifications
                            }
                    );
                    NativeUi.gap(this, content, 14);
                    content.addView(NativeUi.text(this, "Booking Masuk", 19, true));
                    NativeUi.gap(this, content, 8);
                    int shown=0;
                    for(int i=0;i<active.length() && shown<3;i++){
                        JSONObject t=active.optJSONObject(i);
                        if(t!=null && "booking".equals(t.optString("source"))){
                            addTransactionCard(t, true, false);
                            shown++;
                        }
                    }
                    if(shown==0) content.addView(NativeUi.muted(this,"Tidak ada booking aktif.",13));
                    buildNav("home");
                });
            } catch (Exception e) {
                runOnUiThread(() -> replaceLoadingWithError(e));
            }
        });
    }

    private void ensureServiceCatalog(Runnable next) {
        if (serviceCatalog.length()>0) { next.run(); return; }
        io.execute(() -> {
            try {
                JSONArray services = get("services?select=id,name_id,sort_order&active=eq.true&order=sort_order.asc");
                JSONArray prices = get("service_prices?select=service_id,vehicle_category,amount,active&active=eq.true");
                serviceCatalog = services;
                priceCatalog = prices;
                runOnUiThread(next);
            } catch(Exception e) {
                runOnUiThread(() -> NativeUi.toast(this, NativeUi.errorText(e.getMessage())));
            }
        });
    }

    private ArrayAdapter<String> adapter(String[] values) {
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private int standardPrice(String serviceId, String category) {
        Integer fallback = null;
        for(int i=0;i<priceCatalog.length();i++){
            JSONObject p=priceCatalog.optJSONObject(i);
            if(p==null || !serviceId.equals(p.optString("service_id"))) continue;
            if(p.isNull("amount")) continue;
            String cat=p.isNull("vehicle_category") ? null : p.optString("vehicle_category",null);
            if(category.equals(cat)) return p.optInt("amount",0);
            if(cat==null) fallback=p.optInt("amount",0);
        }
        return fallback==null?0:fallback;
    }

    private void showNewTransaction(boolean scanImmediately) {
        clear("Transaksi Baru", "new");
        addLoading("Memuat layanan…");
        ensureServiceCatalog(() -> renderNewTransaction(scanImmediately));
    }

    private void renderNewTransaction(boolean scanImmediately) {
        content.removeAllViews();
        content.addView(NativeUi.section(this, "Transaksi Baru"));
        NativeUi.gap(this, content, 12);

        final boolean[] memberMode={true};
        final String[] memberQr={""};
        final String[] memberName={"Belum ada Member"};
        final Set<String> selected = new LinkedHashSet<>();

        LinearLayout selector=new LinearLayout(this);
        Button scan=NativeUi.button(this,"Scan Member",true);
        Button guest=NativeUi.button(this,"Guest",false);
        selector.addView(scan,new LinearLayout.LayoutParams(0,NativeUi.dp(this,50),1));
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(0,NativeUi.dp(this,50),1);gp.leftMargin=NativeUi.dp(this,8);selector.addView(guest,gp);
        content.addView(selector,NativeUi.match(this));
        NativeUi.gap(this, content, 10);

        TextView memberInfo=NativeUi.muted(this,"Scan QR Member untuk menautkan transaksi.",12);
        content.addView(memberInfo);

        LinearLayout guestBox=NativeUi.card(this);
        EditText guestName=NativeUi.input(this,"Nama Guest (opsional)");
        EditText guestPhone=NativeUi.input(this,"WhatsApp (opsional)");
        guestPhone.setInputType(InputType.TYPE_CLASS_PHONE);
        guestBox.addView(guestName,NativeUi.match(this));NativeUi.gap(this,guestBox,8);guestBox.addView(guestPhone,NativeUi.match(this));
        guestBox.setVisibility(View.GONE);
        NativeUi.margin(this,guestBox,10);content.addView(guestBox);

        LinearLayout form=NativeUi.redCard(this);
        EditText motor=NativeUi.input(this,"Nama Motor");
        Spinner category=NativeUi.spinner(this);category.setAdapter(adapter(CATEGORY_LABELS));
        Button services=NativeUi.button(this,"Pilih Layanan",false);
        TextView standard=NativeUi.muted(this,"Harga Standar: Rp0",13);
        EditText actual=NativeUi.moneyInput(this,"Harga Aktual");
        Spinner method=NativeUi.spinner(this);method.setAdapter(adapter(METHOD_LABELS));
        String[] payLabels={"Belum Dibayar","DP","Lunas"};
        Spinner payStatus=NativeUi.spinner(this);payStatus.setAdapter(adapter(payLabels));
        EditText payAmount=NativeUi.moneyInput(this,"Nominal Dibayar");
        payAmount.setText("0");

        form.addView(NativeUi.label(this,"Nama Motor"));NativeUi.gap(this,form,6);form.addView(motor,NativeUi.match(this));
        NativeUi.gap(this,form,12);form.addView(NativeUi.label(this,"Kategori"));NativeUi.gap(this,form,6);form.addView(category,NativeUi.match(this));
        NativeUi.gap(this,form,12);form.addView(NativeUi.label(this,"Layanan"));NativeUi.gap(this,form,6);form.addView(services,NativeUi.match(this));
        NativeUi.gap(this,form,12);form.addView(standard);
        NativeUi.gap(this,form,8);form.addView(NativeUi.label(this,"Harga Aktual"));NativeUi.gap(this,form,6);form.addView(actual,NativeUi.match(this));
        NativeUi.gap(this,form,12);form.addView(NativeUi.label(this,"Metode Pembayaran"));NativeUi.gap(this,form,6);form.addView(method,NativeUi.match(this));
        NativeUi.gap(this,form,12);form.addView(NativeUi.label(this,"Status Pembayaran"));NativeUi.gap(this,form,6);form.addView(payStatus,NativeUi.match(this));
        NativeUi.gap(this,form,12);form.addView(NativeUi.label(this,"Nominal Dibayar"));NativeUi.gap(this,form,6);form.addView(payAmount,NativeUi.match(this));
        NativeUi.gap(this,form,16);
        Button save=NativeUi.button(this,"Simpan Transaksi",true);form.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,NativeUi.dp(this,54)));
        NativeUi.margin(this,form,12);content.addView(form);

        Runnable recalc=()->{
            int total=0;
            String cat=CATEGORY_VALUES[category.getSelectedItemPosition()];
            for(String id:selected) total+=standardPrice(id,cat);
            standard.setText("Harga Standar: "+NativeUi.rupiah(total));
            actual.setText(String.valueOf(total));
            if(payStatus.getSelectedItemPosition()==2) payAmount.setText(String.valueOf(total));
        };

        services.setOnClickListener(v -> {
            String[] names=new String[serviceCatalog.length()];
            boolean[] checked=new boolean[serviceCatalog.length()];
            for(int i=0;i<serviceCatalog.length();i++){
                JSONObject s=serviceCatalog.optJSONObject(i);
                names[i]=s==null?"Layanan":s.optString("name_id","Layanan");
                checked[i]=s!=null && selected.contains(s.optString("id"));
            }
            new AlertDialog.Builder(this)
                    .setTitle("Pilih Layanan")
                    .setMultiChoiceItems(names,checked,(d,which,isChecked)->{
                        JSONObject s=serviceCatalog.optJSONObject(which);
                        if(s==null)return;
                        String id=s.optString("id");
                        if(isChecked)selected.add(id);else selected.remove(id);
                    })
                    .setNegativeButton("Batal",null)
                    .setPositiveButton("Selesai",(d,w)->{
                        List<String> chosen=new ArrayList<>();
                        for(int i=0;i<serviceCatalog.length();i++){
                            JSONObject s=serviceCatalog.optJSONObject(i);
                            if(s!=null && selected.contains(s.optString("id")))chosen.add(s.optString("name_id"));
                        }
                        services.setText(chosen.isEmpty()?"Pilih Layanan":joinLimited(chosen,3));
                        recalc.run();
                    }).show();
        });

        category.setOnItemSelectedListener(new SimpleItemListener(recalc));
        payStatus.setOnItemSelectedListener(new SimpleItemListener(()->{
            int pos=payStatus.getSelectedItemPosition();
            if(pos==0)payAmount.setText("0");
            else if(pos==2)payAmount.setText(String.valueOf(parseMoney(actual.getText().toString())));
        }));

        Runnable doScan=()->scanMemberQr((raw,found)->{
            if(found!=null){
                memberMode[0]=true;memberQr[0]=raw;memberName[0]=found.optString("full_name","Member");
                memberInfo.setText("Member: "+memberName[0]);
                guestBox.setVisibility(View.GONE);
                scan.setText("Member ✓");
            }
        });
        scan.setOnClickListener(v->{memberMode[0]=true;guestBox.setVisibility(View.GONE);doScan.run();});
        guest.setOnClickListener(v->{memberMode[0]=false;memberQr[0]="";memberInfo.setText("Transaksi Guest");guestBox.setVisibility(View.VISIBLE);});

        save.setOnClickListener(v->{
            if(motor.getText().toString().trim().isEmpty()){NativeUi.toast(this,"Nama motor wajib diisi.");return;}
            if(selected.isEmpty()){NativeUi.toast(this,"Pilih minimal satu layanan.");return;}
            if(memberMode[0] && memberQr[0].isEmpty()){NativeUi.toast(this,"Scan QR Member terlebih dahulu, atau pilih Guest.");return;}
            int total=parseMoney(actual.getText().toString());
            int amount=parseMoney(payAmount.getText().toString());
            int ps=payStatus.getSelectedItemPosition();
            if(ps==0)amount=0;
            if(ps==2)amount=total;
            if(total<0 || amount<0 || amount>total){NativeUi.toast(this,"Periksa nominal harga/pembayaran.");return;}

            save.setEnabled(false);save.setAlpha(.55f);
            int finalAmount=amount;
            io.execute(()->{
                try{
                    JSONObject b=new JSONObject();
                    b.put("p_member_qr",memberMode[0]?memberQr[0]:JSONObject.NULL);
                    b.put("p_guest_name",memberMode[0]?JSONObject.NULL:guestName.getText().toString().trim());
                    b.put("p_guest_phone",memberMode[0]?JSONObject.NULL:guestPhone.getText().toString().trim());
                    b.put("p_motor_name",motor.getText().toString().trim());
                    b.put("p_vehicle_category",CATEGORY_VALUES[category.getSelectedItemPosition()]);
                    JSONArray ids=new JSONArray();for(String id:selected)ids.put(id);b.put("p_service_ids",ids);
                    b.put("p_final_total",total);
                    b.put("p_payment_method",METHOD_VALUES[method.getSelectedItemPosition()]);
                    b.put("p_payment_amount",finalAmount);
                    b.put("p_idempotency_key",UUID.randomUUID().toString());
                    JSONObject out=ApiClient.asObject(rpc("staff_create_walkin_transaction",b));
                    runOnUiThread(()->showTransactionSuccess(out.optString("transaction_code","Transaksi")));
                }catch(Exception e){runOnUiThread(()->{save.setEnabled(true);save.setAlpha(1f);NativeUi.toast(this,NativeUi.errorText(e.getMessage()));});}
            });
        });

        buildNav("new");
        if(scanImmediately) handler.postDelayed(doScan,250);
    }

    private void showTransactionSuccess(String code){
        content.removeAllViews();
        NativeUi.gap(this,content,24);
        TextView check=NativeUi.center(this,"✓",64,true);check.setTextColor(NativeUi.GREEN);content.addView(check,NativeUi.match(this));
        content.addView(NativeUi.center(this,"Transaksi Tersimpan",26,true),NativeUi.match(this));
        NativeUi.gap(this,content,8);content.addView(NativeUi.center(this,code,16,true),NativeUi.match(this));
        NativeUi.gap(this,content,22);Button next=NativeUi.button(this,"Transaksi Baru",true);next.setOnClickListener(v->showNewTransaction(false));content.addView(next,NativeUi.match(this));
        NativeUi.gap(this,content,10);Button work=NativeUi.button(this,"Lihat Pekerjaan",false);work.setOnClickListener(v->showWork(null));content.addView(work,NativeUi.match(this));
        buildNav("new");
    }

    private interface ScanCallback { void done(String raw, JSONObject member); }

    private void scanMemberQr(ScanCallback cb){
        GmsBarcodeScannerOptions options=new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build();
        GmsBarcodeScanner scanner=GmsBarcodeScanning.getClient(this,options);
        scanner.startScan()
                .addOnSuccessListener(barcode->{
                    String raw=barcode.getRawValue();
                    if(raw==null||raw.trim().isEmpty())return;
                    io.execute(()->{
                        try{
                            JSONObject b=new JSONObject();b.put("p_qr_value",raw.trim());
                            JSONObject member=ApiClient.asObject(rpc("staff_lookup_member_by_qr",b));
                            runOnUiThread(()->cb.done(raw.trim(),member));
                        }catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
                    });
                })
                .addOnFailureListener(e->NativeUi.toast(this,"Scan QR dibatalkan/gagal."));
    }

    private void showWork(String mode){
        clear(mode==null?"Pekerjaan":"Pembayaran","work");
        addLoading("Memuat pekerjaan…");
        io.execute(()->{
            try{
                JSONArray rows=ApiClient.asArray(rpc("internal_active_transactions",new JSONObject()));
                runOnUiThread(()->{
                    content.removeAllViews();content.addView(NativeUi.section(this,mode==null?"Pekerjaan":"Pembayaran"));NativeUi.gap(this,content,12);
                    int shown=0;
                    for(int i=0;i<rows.length();i++){
                        JSONObject t=rows.optJSONObject(i);if(t==null)continue;
                        if("payment".equals(mode) && "paid".equals(t.optString("payment_status")) && t.optString("pending_payment_id").isEmpty())continue;
                        addTransactionCard(t,false,false);shown++;
                    }
                    if(shown==0)content.addView(NativeUi.muted(this,"Tidak ada pekerjaan aktif.",13));
                    buildNav("work");
                });
            }catch(Exception e){runOnUiThread(()->replaceLoadingWithError(e));}
        });
    }

    private void showBookings(){
        clear("Booking","booking");
        addLoading("Memuat booking…");
        io.execute(()->{
            try{
                JSONArray rows=ApiClient.asArray(rpc("internal_active_transactions",new JSONObject()));
                runOnUiThread(()->{
                    content.removeAllViews();content.addView(NativeUi.section(this,"Booking"));NativeUi.gap(this,content,12);
                    int shown=0;
                    for(int i=0;i<rows.length();i++){
                        JSONObject t=rows.optJSONObject(i);if(t!=null&&"booking".equals(t.optString("source"))){addTransactionCard(t,true,false);shown++;}
                    }
                    if(shown==0)content.addView(NativeUi.muted(this,"Tidak ada booking aktif.",13));
                    buildNav("booking");
                });
            }catch(Exception e){runOnUiThread(()->replaceLoadingWithError(e));}
        });
    }

    private String serviceNames(JSONObject t){
        JSONArray a=t.optJSONArray("services");if(a==null||a.length()==0)return "-";
        List<String> out=new ArrayList<>();
        for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x!=null)out.add(x.optString("name","Layanan"));}
        return joinLimited(out,3);
    }

    private void addTransactionCard(JSONObject t, boolean compact, boolean ownerMode){
        LinearLayout card=NativeUi.card(this);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);
        left.addView(NativeUi.text(this,t.optString("customer_name","Guest"),17,true));
        left.addView(NativeUi.muted(this,t.optString("transaction_code",""),11));
        top.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        int workAccent="completed".equals(t.optString("work_status"))?NativeUi.GREEN:"in_progress".equals(t.optString("work_status"))?NativeUi.AMBER:NativeUi.RED;
        top.addView(NativeUi.chip(this,NativeUi.workStatus(t.optString("work_status")),workAccent));
        card.addView(top);
        NativeUi.gap(this,card,10);
        card.addView(NativeUi.text(this,t.optString("motor_name","-")+"  •  "+NativeUi.categoryLabel(t.optString("vehicle_category")),14,true));
        card.addView(NativeUi.muted(this,serviceNames(t),12));
        card.addView(NativeUi.text(this,NativeUi.rupiah(t.optInt("final_total")),15,true));
        if(t.has("booking_at")&&!t.isNull("booking_at"))card.addView(NativeUi.muted(this,formatDateTime(t.optString("booking_at")),12));
        NativeUi.gap(this,card,10);
        LinearLayout states=new LinearLayout(this);states.setGravity(Gravity.CENTER_VERTICAL);
        int payAccent="paid".equals(t.optString("payment_status"))?NativeUi.GREEN:"partial".equals(t.optString("payment_status"))?NativeUi.AMBER:NativeUi.RED;
        states.addView(NativeUi.chip(this,NativeUi.paymentStatus(t.optString("payment_status")),payAccent));
        NativeUi.gap(this,states,8);
        TextView paid=NativeUi.muted(this,"Dibayar "+NativeUi.rupiah(t.optInt("amount_paid")),11);states.addView(paid);
        card.addView(states);

        if(!compact && !ownerMode){
            String pendingId=t.optString("pending_payment_id","");
            String proof=t.optString("pending_proof_path","");
            if(!pendingId.isEmpty()){
                NativeUi.gap(this,card,10);
                if(!proof.isEmpty()){
                    Button see=NativeUi.button(this,"Lihat Bukti Pembayaran",false);see.setOnClickListener(v->showProof(proof));card.addView(see,NativeUi.match(this));NativeUi.gap(this,card,8);
                }
                LinearLayout pActions=new LinearLayout(this);
                Button approve=NativeUi.button(this,"Konfirmasi",true);approve.setOnClickListener(v->verifyBookingPayment(pendingId,true));
                Button reject=NativeUi.dangerButton(this,"Tolak");reject.setOnClickListener(v->verifyBookingPayment(pendingId,false));
                pActions.addView(approve,new LinearLayout.LayoutParams(0,NativeUi.dp(this,46),1));
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,NativeUi.dp(this,46),1);rp.leftMargin=NativeUi.dp(this,8);pActions.addView(reject,rp);card.addView(pActions);
            }else if(!"paid".equals(t.optString("payment_status"))){
                NativeUi.gap(this,card,10);Button pay=NativeUi.button(this,"Konfirmasi Pembayaran",true);pay.setOnClickListener(v->paymentDialog(t));card.addView(pay,NativeUi.match(this));
            }
            if(!"completed".equals(t.optString("work_status"))){
                NativeUi.gap(this,card,8);String next="pending".equals(t.optString("work_status"))?"Mulai Pengerjaan":"Selesaikan Pengerjaan";
                Button work=NativeUi.button(this,next,false);work.setOnClickListener(v->updateWork(t,"pending".equals(t.optString("work_status"))?"in_progress":"completed"));card.addView(work,NativeUi.match(this));
            }
        }
        NativeUi.margin(this,card,9);content.addView(card);
    }

    private void paymentDialog(JSONObject t){
        LinearLayout box=NativeUi.page(this);
        Spinner method=NativeUi.spinner(this);method.setAdapter(adapter(METHOD_LABELS));
        EditText amount=NativeUi.moneyInput(this,"Nominal");amount.setText(String.valueOf(Math.max(0,t.optInt("final_total")-t.optInt("amount_paid"))));
        box.addView(NativeUi.label(this,"Metode"));NativeUi.gap(this,box,5);box.addView(method,NativeUi.match(this));NativeUi.gap(this,box,10);box.addView(NativeUi.label(this,"Nominal"));NativeUi.gap(this,box,5);box.addView(amount,NativeUi.match(this));
        new AlertDialog.Builder(this).setTitle("Konfirmasi Pembayaran").setView(box).setNegativeButton("Batal",null).setPositiveButton("Konfirmasi",(d,w)->{
            int val=parseMoney(amount.getText().toString());String meth=METHOD_VALUES[method.getSelectedItemPosition()];
            io.execute(()->{
                try{
                    JSONObject b=new JSONObject();
                    if("booking".equals(t.optString("source"))){b.put("p_booking_id",t.optString("booking_id"));b.put("p_method",meth);b.put("p_amount",val);rpc("staff_record_booking_payment",b);}
                    else{b.put("p_transaction_id",t.optString("id"));b.put("p_method",meth);b.put("p_amount",val);rpc("staff_confirm_transaction_payment",b);}
                    runOnUiThread(()->{NativeUi.toast(this,"Pembayaran dikonfirmasi.");showWork(null);});
                }catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
            });
        }).show();
    }

    private void verifyBookingPayment(String paymentId, boolean approve){
        io.execute(()->{
            try{
                JSONObject b=new JSONObject();b.put("p_payment_id",paymentId);b.put("p_approved",approve);b.put("p_notes",approve?"Dikonfirmasi Teknisi":"Ditolak Teknisi");rpc("staff_verify_manual_payment",b);
                runOnUiThread(()->{NativeUi.toast(this,approve?"Pembayaran dikonfirmasi.":"Pembayaran ditolak.");showWork(null);});
            }catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        });
    }

    private void updateWork(JSONObject t,String status){
        io.execute(()->{
            try{
                JSONObject b=new JSONObject();b.put("p_transaction_id",t.optString("id"));b.put("p_status",status);rpc("staff_update_transaction_work_status",b);
                runOnUiThread(()->{NativeUi.toast(this,"Status diperbarui.");showWork(null);});
            }catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        });
    }

    private void showProof(String path){
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Bukti Pembayaran").setMessage("Memuat…").setNegativeButton("Tutup",null).create();dialog.show();
        io.execute(()->{
            try{
                String url=ApiClient.createSignedUrl("payment-proofs",path,300,token());byte[] bytes=ApiClient.downloadBytes(url,null);
                runOnUiThread(()->{
                    ImageView img=new ImageView(this);img.setImageBitmap(BitmapFactory.decodeByteArray(bytes,0,bytes.length));img.setAdjustViewBounds(true);img.setPadding(20,20,20,20);
                    dialog.setMessage(null);dialog.setView(img);
                });
            }catch(Exception e){runOnUiThread(()->dialog.setMessage(NativeUi.errorText(e.getMessage())));}
        });
    }

    private void showNotifications(){
        clear("Notifikasi","home");
        addLoading("Memuat notifikasi…");
        io.execute(()->{
            try{
                String uid=AppSession.userId(this);
                JSONArray rows=get("notifications?select=id,kind,title,body,read_at,created_at&user_id=eq."+ApiClient.q(uid)+"&order=created_at.desc&limit=100");
                rpc("mark_my_notifications_read",new JSONObject());
                runOnUiThread(()->{
                    content.removeAllViews();content.addView(NativeUi.section(this,"Notifikasi"));NativeUi.gap(this,content,12);
                    if(rows.length()==0){content.addView(NativeUi.muted(this,"Belum ada notifikasi.",13));return;}
                    for(int i=0;i<rows.length();i++){
                        JSONObject n=rows.optJSONObject(i);if(n==null)continue;
                        LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,n.optString("title","Notifikasi"),15,true));c.addView(NativeUi.muted(this,n.optString("body",""),12));c.addView(NativeUi.muted(this,formatDateTime(n.optString("created_at")),10));NativeUi.margin(this,c,8);content.addView(c);
                    }
                    buildNav("home");
                });
            }catch(Exception e){runOnUiThread(()->replaceLoadingWithError(e));}
        });
    }

    // ---------------------- OWNER ----------------------

    private void showOwnerDashboard(){
        clear("Dashboard","home");addLoading("Memuat…");
        io.execute(()->{
            try{
                JSONObject s=ApiClient.asObject(rpc("internal_owner_dashboard_summary",new JSONObject()));
                runOnUiThread(()->{
                    content.removeAllViews();content.addView(NativeUi.section(this,"Dashboard"));NativeUi.gap(this,content,14);addStatGrid(s,true);
                    NativeUi.gap(this,content,20);content.addView(NativeUi.text(this,"Ringkasan",19,true));NativeUi.gap(this,content,8);
                    LinearLayout summary=NativeUi.card(this);
                    addKeyValue(summary,"Walk-in",String.valueOf(s.optInt("walkin_today")));
                    addKeyValue(summary,"Booking",String.valueOf(s.optInt("booking_today")));
                    addKeyValue(summary,"Cash",NativeUi.rupiah(s.optInt("cash_today")));
                    addKeyValue(summary,"QRIS / Transfer",NativeUi.rupiah(s.optInt("non_cash_today")));
                    content.addView(summary,NativeUi.match(this));
                    NativeUi.gap(this,content,20);content.addView(NativeUi.text(this,"Menu",19,true));NativeUi.gap(this,content,8);
                    addQuickGrid(new String[]{"Transaksi","Booking","Member","Layanan & Harga","Laporan","Audit Log","Teknisi","Pengaturan"},new Runnable[]{
                            ()->showOwnerTransactions(null),()->showOwnerTransactions("booking"),this::showOwnerMembers,this::showOwnerServices,this::showOwnerReports,this::showOwnerAudit,this::showOwnerStaff,this::showOwnerSettings
                    });
                    buildNav("home");
                });
            }catch(Exception e){runOnUiThread(()->replaceLoadingWithError(e));}
        });
    }

    private void addKeyValue(LinearLayout parent,String key,String value){
        LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.addView(NativeUi.muted(this,key,13),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));r.addView(NativeUi.text(this,value,14,true));parent.addView(r);NativeUi.gap(this,parent,9);
    }

    private void showOwnerTransactions(String sourceFilter){
        clear(sourceFilter==null?"Transaksi":"Booking","transactions");addLoading("Memuat transaksi…");
        io.execute(()->{
            try{
                JSONObject b=new JSONObject();b.put("p_limit",250);b.put("p_offset",0);JSONArray rows=ApiClient.asArray(rpc("owner_internal_transactions",b));
                runOnUiThread(()->{
                    content.removeAllViews();content.addView(NativeUi.section(this,sourceFilter==null?"Transaksi":"Booking"));NativeUi.gap(this,content,12);
                    int shown=0;
                    for(int i=0;i<rows.length();i++){
                        JSONObject t=rows.optJSONObject(i);if(t==null)continue;if(sourceFilter!=null&&!sourceFilter.equals(t.optString("source")))continue;addOwnerTransactionCard(t);shown++;
                    }
                    if(shown==0)content.addView(NativeUi.muted(this,"Belum ada transaksi.",13));buildNav("transactions");
                });
            }catch(Exception e){runOnUiThread(()->replaceLoadingWithError(e));}
        });
    }

    private void addOwnerTransactionCard(JSONObject t){
        LinearLayout c=NativeUi.card(this);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(NativeUi.text(this,t.optString("customer_name","Guest"),16,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));top.addView(NativeUi.chip(this,t.optString("source","walk_in").equals("booking")?"BOOKING":"WALK-IN",NativeUi.RED));c.addView(top);
        c.addView(NativeUi.muted(this,t.optString("transaction_code","")+"  •  "+formatDateTime(t.optString("created_at")),11));NativeUi.gap(this,c,8);
        c.addView(NativeUi.text(this,t.optString("motor_name","-")+"  •  "+NativeUi.categoryLabel(t.optString("vehicle_category")),13,true));c.addView(NativeUi.muted(this,serviceNames(t),12));
        c.addView(NativeUi.text(this,NativeUi.rupiah(t.optInt("final_total"))+"  •  "+NativeUi.paymentStatus(t.optString("payment_status")),14,true));c.addView(NativeUi.muted(this,NativeUi.workStatus(t.optString("work_status"))+"  •  "+t.optString("record_status","active"),11));
        NativeUi.gap(this,c,10);LinearLayout actions=new LinearLayout(this);Button edit=NativeUi.button(this,"Koreksi",false);Button del=NativeUi.dangerButton(this,"Hapus");edit.setOnClickListener(v->ownerEditTransaction(t));del.setOnClickListener(v->ownerDeleteTransaction(t));actions.addView(edit,new LinearLayout.LayoutParams(0,NativeUi.dp(this,44),1));LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(0,NativeUi.dp(this,44),1);dp.leftMargin=NativeUi.dp(this,8);actions.addView(del,dp);c.addView(actions);NativeUi.margin(this,c,8);content.addView(c);
    }

    private void ownerEditTransaction(JSONObject t){
        LinearLayout box=NativeUi.page(this);EditText motor=NativeUi.input(this,"Nama Motor");motor.setText(t.optString("motor_name"));Spinner cat=NativeUi.spinner(this);cat.setAdapter(adapter(CATEGORY_LABELS));cat.setSelection(categoryIndex(t.optString("vehicle_category")));EditText total=NativeUi.moneyInput(this,"Total");total.setText(String.valueOf(t.optInt("final_total")));EditText reason=NativeUi.input(this,"Alasan koreksi");
        box.addView(motor,NativeUi.match(this));NativeUi.gap(this,box,8);box.addView(cat,NativeUi.match(this));NativeUi.gap(this,box,8);box.addView(total,NativeUi.match(this));NativeUi.gap(this,box,8);box.addView(reason,NativeUi.match(this));
        new AlertDialog.Builder(this).setTitle("Koreksi Transaksi").setView(box).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->io.execute(()->{
            try{JSONObject b=new JSONObject();b.put("p_transaction_id",t.optString("id"));b.put("p_motor_name",motor.getText().toString().trim());b.put("p_vehicle_category",CATEGORY_VALUES[cat.getSelectedItemPosition()]);b.put("p_final_total",parseMoney(total.getText().toString()));b.put("p_reason",reason.getText().toString().trim());rpc("owner_update_service_transaction",b);runOnUiThread(()->{NativeUi.toast(this,"Transaksi diperbarui.");showOwnerTransactions(null);});}catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        })).show();
    }

    private void ownerDeleteTransaction(JSONObject t){
        EditText reason=NativeUi.input(this,"Alasan penghapusan");new AlertDialog.Builder(this).setTitle("Hapus "+t.optString("transaction_code")).setView(reason).setNegativeButton("Batal",null).setPositiveButton("Hapus",(d,w)->io.execute(()->{
            try{JSONObject b=new JSONObject();b.put("p_transaction_id",t.optString("id"));b.put("p_reason",reason.getText().toString().trim());rpc("owner_delete_service_transaction",b);runOnUiThread(()->{NativeUi.toast(this,"Transaksi dihapus.");showOwnerTransactions(null);});}catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        })).show();
    }

    private void showOwnerMembers(){
        clear("Member","members");addLoading("Memuat member…");
        io.execute(()->{
            try{JSONArray rows=get("member_profiles?select=user_id,full_name,username,status,phone,created_at&order=created_at.desc&limit=250");runOnUiThread(()->{
                content.removeAllViews();content.addView(NativeUi.section(this,"Member"));NativeUi.gap(this,content,12);
                for(int i=0;i<rows.length();i++){JSONObject m=rows.optJSONObject(i);if(m==null)continue;LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,m.optString("full_name","Member"),16,true));c.addView(NativeUi.muted(this,"@"+m.optString("username","")+"  •  "+m.optString("status",""),12));if(!m.optString("phone","").isEmpty())c.addView(NativeUi.muted(this,m.optString("phone"),11));NativeUi.gap(this,c,8);LinearLayout a=new LinearLayout(this);Button st=NativeUi.button(this,"Status",false);Button pt=NativeUi.button(this,"Point",false);st.setOnClickListener(v->ownerMemberStatus(m));pt.setOnClickListener(v->ownerMemberPoints(m));a.addView(st,new LinearLayout.LayoutParams(0,NativeUi.dp(this,42),1));LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,NativeUi.dp(this,42),1);pp.leftMargin=NativeUi.dp(this,8);a.addView(pt,pp);c.addView(a);NativeUi.margin(this,c,8);content.addView(c);}buildNav("members");
            });}catch(Exception e){runOnUiThread(()->replaceLoadingWithError(e));}
        });
    }

    private void ownerMemberStatus(JSONObject m){
        String[] labels={"Registered","Active","Suspended"};String[] vals={"registered","active","suspended"};Spinner s=NativeUi.spinner(this);s.setAdapter(adapter(labels));EditText reason=NativeUi.input(this,"Alasan");LinearLayout box=NativeUi.page(this);box.addView(s,NativeUi.match(this));NativeUi.gap(this,box,8);box.addView(reason,NativeUi.match(this));new AlertDialog.Builder(this).setTitle(m.optString("full_name")).setView(box).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->io.execute(()->{
            try{JSONObject b=new JSONObject();b.put("p_member_id",m.optString("user_id"));b.put("p_status",vals[s.getSelectedItemPosition()]);b.put("p_reason",reason.getText().toString().trim());rpc("owner_update_member_status",b);runOnUiThread(()->{NativeUi.toast(this,"Status diperbarui.");showOwnerMembers();});}catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        })).show();
    }

    private void ownerMemberPoints(JSONObject m){
        EditText delta=NativeUi.moneyInput(this,"Delta Point");EditText desc=NativeUi.input(this,"Keterangan");LinearLayout box=NativeUi.page(this);box.addView(delta,NativeUi.match(this));NativeUi.gap(this,box,8);box.addView(desc,NativeUi.match(this));new AlertDialog.Builder(this).setTitle("Point · "+m.optString("full_name")).setView(box).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->io.execute(()->{
            try{int x=Integer.parseInt(delta.getText().toString().trim());JSONObject b=new JSONObject();b.put("p_member_id",m.optString("user_id"));b.put("p_delta",x);b.put("p_description",desc.getText().toString().trim());rpc("owner_adjust_member_points",b);runOnUiThread(()->NativeUi.toast(this,"Point diperbarui."));}catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        })).show();
    }

    private void showOwnerReports(){
        clear("Laporan","reports");
        Spinner period=NativeUi.spinner(this);String[] labels={"Hari Ini","7 Hari","30 Hari","90 Hari"};int[] days={1,7,30,90};period.setAdapter(adapter(labels));content.addView(period,NativeUi.match(this));NativeUi.gap(this,content,12);LinearLayout result=NativeUi.card(this);content.addView(result,NativeUi.match(this));
        Runnable load=()->{result.removeAllViews();result.addView(NativeUi.muted(this,"Memuat…",13));int d=days[period.getSelectedItemPosition()];io.execute(()->{
            try{JSONObject b=new JSONObject();b.put("p_days",d);JSONObject r=ApiClient.asObject(rpc("owner_internal_report",b));runOnUiThread(()->{result.removeAllViews();addKeyValue(result,"Omzet",NativeUi.rupiah(r.optInt("revenue")));addKeyValue(result,"Transaksi",String.valueOf(r.optInt("transactions")));addKeyValue(result,"Walk-in",String.valueOf(r.optInt("walkin")));addKeyValue(result,"Booking",String.valueOf(r.optInt("booking")));addKeyValue(result,"Pengerjaan Selesai",String.valueOf(r.optInt("completed")));addKeyValue(result,"Belum Lunas",NativeUi.rupiah(r.optInt("unpaid_total")));});}catch(Exception e){runOnUiThread(()->{result.removeAllViews();result.addView(NativeUi.muted(this,NativeUi.errorText(e.getMessage()),13));});}
        });};
        period.setOnItemSelectedListener(new SimpleItemListener(load));load.run();buildNav("reports");
    }

    private void showOwnerServices(){
        clear("Layanan & Harga","home");addLoading("Memuat layanan…");
        io.execute(()->{
            try{JSONArray services=get("services?select=id,name_id,active&order=sort_order.asc");JSONArray prices=get("service_prices?select=id,service_id,vehicle_category,amount,price_label,active&order=created_at.asc");Map<String,String> names=new LinkedHashMap<>();for(int i=0;i<services.length();i++){JSONObject s=services.optJSONObject(i);if(s!=null)names.put(s.optString("id"),s.optString("name_id"));}
                runOnUiThread(()->{content.removeAllViews();content.addView(NativeUi.section(this,"Layanan & Harga"));NativeUi.gap(this,content,12);for(int i=0;i<prices.length();i++){JSONObject p=prices.optJSONObject(i);if(p==null)continue;LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,names.getOrDefault(p.optString("service_id"),"Layanan"),15,true));String cat=p.isNull("vehicle_category")?"Umum":NativeUi.categoryLabel(p.optString("vehicle_category"));c.addView(NativeUi.muted(this,cat,11));c.addView(NativeUi.text(this,p.isNull("amount")?p.optString("price_label","Konsultasi"):NativeUi.rupiah(p.optInt("amount")),15,true));Button edit=NativeUi.button(this,"Ubah Harga",false);edit.setOnClickListener(v->ownerEditPrice(p,names.getOrDefault(p.optString("service_id"),"Layanan")));NativeUi.gap(this,c,8);c.addView(edit,NativeUi.match(this));NativeUi.margin(this,c,8);content.addView(c);}buildNav("home");});
            }catch(Exception e){runOnUiThread(()->replaceLoadingWithError(e));}
        });
    }

    private void ownerEditPrice(JSONObject p,String name){
        EditText amount=NativeUi.moneyInput(this,"Harga");if(!p.isNull("amount"))amount.setText(String.valueOf(p.optInt("amount")));new AlertDialog.Builder(this).setTitle(name).setView(amount).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->io.execute(()->{
            try{JSONObject b=new JSONObject();b.put("p_service_id",p.optString("service_id"));b.put("p_vehicle_category",p.isNull("vehicle_category")?JSONObject.NULL:p.optString("vehicle_category"));b.put("p_amount",parseMoney(amount.getText().toString()));b.put("p_price_label",JSONObject.NULL);b.put("p_active",true);rpc("owner_update_service_price",b);runOnUiThread(()->{NativeUi.toast(this,"Harga diperbarui.");showOwnerServices();});}catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        })).show();
    }

    private void showOwnerAudit(){
        clear("Audit Log","home");addLoading("Memuat audit…");io.execute(()->{
            try{JSONArray rows=get("audit_logs?select=id,actor_label,action,entity_type,entity_id,created_at,metadata&order=created_at.desc&limit=150");runOnUiThread(()->{content.removeAllViews();content.addView(NativeUi.section(this,"Audit Log"));NativeUi.gap(this,content,12);for(int i=0;i<rows.length();i++){JSONObject a=rows.optJSONObject(i);if(a==null)continue;LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,a.optString("action","Aktivitas").replace('_',' '),13,true));c.addView(NativeUi.muted(this,a.optString("actor_label","Sistem")+"  •  "+a.optString("entity_type",""),11));c.addView(NativeUi.muted(this,formatDateTime(a.optString("created_at")),10));NativeUi.margin(this,c,7);content.addView(c);}buildNav("home");});}catch(Exception e){runOnUiThread(()->replaceLoadingWithError(e));}
        });
    }

    private void showOwnerStaff(){
        clear("Teknisi","home");Button add=NativeUi.button(this,"Tambah / Ubah Akses",true);add.setOnClickListener(v->ownerStaffDialog(null));content.addView(add,NativeUi.match(this));NativeUi.gap(this,content,12);addLoading("Memuat akun…");io.execute(()->{
            try{JSONArray rows=get("staff_access_allowlist?select=email,display_name,role,active&order=role.asc,email.asc");runOnUiThread(()->{View load=content.findViewWithTag("loading");if(load!=null)content.removeView(load);for(int i=0;i<rows.length();i++){JSONObject s=rows.optJSONObject(i);if(s==null)continue;LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,s.optString("display_name"),15,true));c.addView(NativeUi.muted(this,s.optString("email"),11));c.addView(NativeUi.chip(this,"owner_admin".equals(s.optString("role"))?"OWNER / ADMIN":"TEKNISI",s.optBoolean("active")?NativeUi.GREEN:NativeUi.MUTED));Button edit=NativeUi.button(this,"Ubah",false);edit.setOnClickListener(v->ownerStaffDialog(s));NativeUi.gap(this,c,8);c.addView(edit,NativeUi.match(this));NativeUi.margin(this,c,8);content.addView(c);}buildNav("home");});}catch(Exception e){runOnUiThread(()->replaceLoadingWithError(e));}
        });
    }

    private void ownerStaffDialog(JSONObject row){
        LinearLayout box=NativeUi.page(this);EditText email=NativeUi.input(this,"Email");EditText name=NativeUi.input(this,"Nama");Spinner roleSpin=NativeUi.spinner(this);roleSpin.setAdapter(adapter(new String[]{"Teknisi","Owner/Admin"}));Spinner active=NativeUi.spinner(this);active.setAdapter(adapter(new String[]{"Aktif","Nonaktif"}));if(row!=null){email.setText(row.optString("email"));name.setText(row.optString("display_name"));roleSpin.setSelection("owner_admin".equals(row.optString("role"))?1:0);active.setSelection(row.optBoolean("active",true)?0:1);}box.addView(email,NativeUi.match(this));NativeUi.gap(this,box,8);box.addView(name,NativeUi.match(this));NativeUi.gap(this,box,8);box.addView(roleSpin,NativeUi.match(this));NativeUi.gap(this,box,8);box.addView(active,NativeUi.match(this));new AlertDialog.Builder(this).setTitle("Akses Internal").setView(box).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->io.execute(()->{
            try{JSONObject b=new JSONObject();b.put("p_email",email.getText().toString().trim());b.put("p_display_name",name.getText().toString().trim());b.put("p_role",roleSpin.getSelectedItemPosition()==1?"owner_admin":"cashier_technician");b.put("p_active",active.getSelectedItemPosition()==0);rpc("owner_save_staff_access",b);runOnUiThread(()->{NativeUi.toast(this,"Akses disimpan.");showOwnerStaff();});}catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        })).show();
    }

    private void showOwnerSettings(){
        clear("Pengaturan","home");
        addOwnerSettingButton("Reward",this::showRewards);
        addOwnerSettingButton("Membership",this::showMembership);
        addOwnerSettingButton("Warranty Coating",this::showWarranties);
        addOwnerSettingButton("Pengaturan Bisnis",this::showBusinessSettings);
        buildNav("home");
    }

    private void addOwnerSettingButton(String label,Runnable action){Button b=actionButton(label,action);content.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,NativeUi.dp(this,56)));NativeUi.gap(this,content,8);}

    private void showRewards(){
        clear("Reward","home");addLoading("Memuat reward…");io.execute(()->{
            try{JSONArray rows=get("reward_catalog?select=id,code,title_id,reward_type,points_cost,voucher_amount,min_transaction,validity_days,active,sort_order&business_code=eq.starpoint_garage&order=sort_order.asc");runOnUiThread(()->{content.removeAllViews();content.addView(NativeUi.section(this,"Reward"));NativeUi.gap(this,content,12);for(int i=0;i<rows.length();i++){JSONObject r=rows.optJSONObject(i);if(r==null)continue;LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,r.optString("title_id"),15,true));c.addView(NativeUi.muted(this,r.optString("code")+"  •  "+r.optInt("points_cost")+" point",11));c.addView(NativeUi.chip(this,r.optBoolean("active")?"AKTIF":"NONAKTIF",r.optBoolean("active")?NativeUi.GREEN:NativeUi.MUTED));NativeUi.margin(this,c,8);content.addView(c);}buildNav("home");});}catch(Exception e){runOnUiThread(()->replaceLoadingWithError(e));}
        });
    }

    private void showMembership(){
        clear("Membership","home");addLoading("Memuat membership…");io.execute(()->{
            try{JSONArray rows=get("membership_levels?select=level,min_qualifying_points,reward_bonus_percent,early_access_hours,waitlist_priority,daily_priority_reservations,sort_order&order=sort_order.asc");runOnUiThread(()->{content.removeAllViews();content.addView(NativeUi.section(this,"Membership"));NativeUi.gap(this,content,12);for(int i=0;i<rows.length();i++){JSONObject m=rows.optJSONObject(i);if(m==null)continue;LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,m.optString("level").toUpperCase(Locale.ROOT),16,true));addKeyValue(c,"Minimum Point",String.valueOf(m.optInt("min_qualifying_points")));addKeyValue(c,"Bonus Reward",m.optInt("reward_bonus_percent")+"%");addKeyValue(c,"Early Access",m.optInt("early_access_hours")+" jam");Button edit=NativeUi.button(this,"Ubah",false);edit.setOnClickListener(v->ownerMembershipDialog(m));c.addView(edit,NativeUi.match(this));NativeUi.margin(this,c,8);content.addView(c);}buildNav("home");});}catch(Exception e){runOnUiThread(()->replaceLoadingWithError(e));}
        });
    }

    private void ownerMembershipDialog(JSONObject m){
        EditText min=NativeUi.moneyInput(this,"Minimum Point");min.setText(String.valueOf(m.optInt("min_qualifying_points")));EditText bonus=NativeUi.moneyInput(this,"Bonus %");bonus.setText(String.valueOf(m.optInt("reward_bonus_percent")));EditText early=NativeUi.moneyInput(this,"Early access jam");early.setText(String.valueOf(m.optInt("early_access_hours")));LinearLayout box=NativeUi.page(this);box.addView(min,NativeUi.match(this));NativeUi.gap(this,box,8);box.addView(bonus,NativeUi.match(this));NativeUi.gap(this,box,8);box.addView(early,NativeUi.match(this));new AlertDialog.Builder(this).setTitle(m.optString("level").toUpperCase(Locale.ROOT)).setView(box).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->io.execute(()->{
            try{JSONObject b=new JSONObject();b.put("p_level",m.optString("level"));b.put("p_min_qualifying_points",parseMoney(min.getText().toString()));b.put("p_reward_bonus_percent",parseMoney(bonus.getText().toString()));b.put("p_early_access_hours",parseMoney(early.getText().toString()));b.put("p_waitlist_priority",m.optInt("waitlist_priority"));b.put("p_daily_priority_reservations",m.optInt("daily_priority_reservations"));rpc("owner_update_membership_level",b);runOnUiThread(()->{NativeUi.toast(this,"Membership diperbarui.");showMembership();});}catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        })).show();
    }

    private void showWarranties(){
        clear("Warranty Coating","home");addLoading("Memuat warranty…");io.execute(()->{
            try{JSONArray rows=get("coating_warranties?select=id,member_id,starts_on,ends_on,next_maintenance_on,status,notes,created_at&order=created_at.desc&limit=150");runOnUiThread(()->{content.removeAllViews();content.addView(NativeUi.section(this,"Warranty Coating"));NativeUi.gap(this,content,12);if(rows.length()==0)content.addView(NativeUi.muted(this,"Belum ada warranty.",13));for(int i=0;i<rows.length();i++){JSONObject w=rows.optJSONObject(i);if(w==null)continue;LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,w.optString("status","active").toUpperCase(Locale.ROOT),14,true));c.addView(NativeUi.muted(this,w.optString("starts_on")+"  →  "+w.optString("ends_on"),11));c.addView(NativeUi.muted(this,"Maintenance: "+w.optString("next_maintenance_on","-"),11));NativeUi.margin(this,c,8);content.addView(c);}buildNav("home");});}catch(Exception e){runOnUiThread(()->replaceLoadingWithError(e));}
        });
    }

    private void showBusinessSettings(){
        clear("Pengaturan Bisnis","home");addLoading("Memuat pengaturan…");io.execute(()->{
            try{JSONArray rows=get("app_settings?select=key,value&key=in.(business,booking_rules,payment_mode,bank_transfer,qris_manual,point_rules)&order=key.asc");runOnUiThread(()->{content.removeAllViews();content.addView(NativeUi.section(this,"Pengaturan Bisnis"));NativeUi.gap(this,content,12);for(int i=0;i<rows.length();i++){JSONObject s=rows.optJSONObject(i);if(s==null)continue;LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,s.optString("key").replace('_',' ').toUpperCase(Locale.ROOT),13,true));c.addView(NativeUi.muted(this,s.opt("value").toString(),10));NativeUi.margin(this,c,8);content.addView(c);}buildNav("home");});}catch(Exception e){runOnUiThread(()->replaceLoadingWithError(e));}
        });
    }

    // ---------------------- ACCOUNT / NOTIFICATION ----------------------

    private void showAccount(){
        clear("Akun","account");
        LinearLayout c=NativeUi.redCard(this);c.addView(NativeUi.text(this,profile.optString("display_name",isOwner()?"Owner/Admin":"Teknisi"),20,true));c.addView(NativeUi.muted(this,profile.optString("email",""),12));NativeUi.gap(this,c,8);c.addView(NativeUi.chip(this,isOwner()?"OWNER / ADMIN":"TEKNISI",NativeUi.RED));content.addView(c,NativeUi.match(this));NativeUi.gap(this,content,16);Button out=NativeUi.dangerButton(this,"Keluar");out.setOnClickListener(v->logout());content.addView(out,NativeUi.match(this));buildNav("account");
    }

    private final Runnable notificationPoll = new Runnable() {
        @Override public void run() {
            if (!isFinishing() && AppSession.exists(InternalActivity.this)) {
                io.execute(() -> {
                    try {
                        String uid=AppSession.userId(InternalActivity.this);
                        JSONArray rows=get("notifications?select=id,kind,title,body,created_at&user_id=eq."+ApiClient.q(uid)+"&read_at=is.null&order=created_at.desc&limit=1");
                        if(rows.length()>0){JSONObject n=rows.optJSONObject(0);if(n!=null && n.optString("kind").startsWith("staff_booking"))notifySystem(n);}
                    } catch (Exception ignored) {}
                });
                handler.postDelayed(this,30000);
            }
        }
    };

    private void createNotificationChannel(){
        if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("booking","Booking Starpoint",NotificationManager.IMPORTANCE_HIGH);c.setDescription("Booking baru Starpoint Garage");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}
    }

    private void notifySystem(JSONObject n){
        String id=n.optString("id","");String last=getSharedPreferences("internal_notice",MODE_PRIVATE).getString("last","");if(id.isEmpty()||id.equals(last))return;getSharedPreferences("internal_notice",MODE_PRIVATE).edit().putString("last",id).apply();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        Intent i=new Intent(this,InternalActivity.class);PendingIntent pi=PendingIntent.getActivity(this,44,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"booking"):new Notification.Builder(this);b.setSmallIcon(R.drawable.ic_launcher).setContentTitle(n.optString("title","Booking baru")).setContentText(n.optString("body","")).setAutoCancel(true).setContentIntent(pi);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(id.hashCode(),b.build());
    }

    // ---------------------- HELPERS ----------------------

    private int categoryIndex(String value){for(int i=0;i<CATEGORY_VALUES.length;i++)if(CATEGORY_VALUES[i].equals(value))return i;return 0;}

    private int parseMoney(String raw){
        try{String s=raw==null?"":raw.replaceAll("[^0-9]","");return s.isEmpty()?0:Integer.parseInt(s);}catch(Exception e){return 0;}
    }

    private String joinLimited(List<String> values,int max){
        if(values.isEmpty())return "-";StringBuilder b=new StringBuilder();for(int i=0;i<values.size()&&i<max;i++){if(i>0)b.append(" • ");b.append(values.get(i));}if(values.size()>max)b.append(" +").append(values.size()-max);return b.toString();
    }

    private String formatDateTime(String iso){
        if(iso==null||iso.isEmpty()||"null".equals(iso))return "";
        try{
            String x=iso.trim();
            if(x.endsWith("Z")) x=x.substring(0,x.length()-1)+"+00:00";
            int dot=x.indexOf('.');
            if(dot>0){
                int plus=x.indexOf('+',dot);
                int minus=x.indexOf('-',dot);
                int zone=plus>0?plus:minus;
                if(zone>0 && zone-dot>4) x=x.substring(0,dot+4)+x.substring(zone);
            }
            java.text.SimpleDateFormat in=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US);
            Date d;
            try{d=in.parse(x);}catch(Exception first){java.text.SimpleDateFormat in2=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US);d=in2.parse(x);}
            java.text.SimpleDateFormat out=new java.text.SimpleDateFormat("dd MMM yyyy • HH:mm",new Locale("id","ID"));
            out.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Jakarta"));
            return d==null?iso:out.format(d);
        }catch(Exception e){return iso.length()>16?iso.substring(0,16).replace('T',' '):iso;}
    }

    private void logout(){AppSession.clear(this);startActivity(new Intent(this,MainActivity.class));finish();}

    @Override protected void onDestroy(){handler.removeCallbacks(notificationPoll);io.shutdownNow();super.onDestroy();}

    private static class SimpleItemListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable r;SimpleItemListener(Runnable r){this.r=r;}
        @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){if(r!=null)r.run();}
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
    }
}