package com.starpointgarage.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StaffActivity extends Activity {
    private final ExecutorService io=Executors.newFixedThreadPool(3);
    private LinearLayout content;
    private JSONObject staff;
    private String role;
    private final HashMap<String,String> services=new HashMap<>();
    private final HashMap<String,String> members=new HashMap<>();
    private EditText qrInput;
    private JSONObject lookedUpMember;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        if(!"staff".equals(AppSession.type(this))){logout();return;}
        staff=AppSession.profile(this);role=staff.optString("role","");
        getWindow().setStatusBarColor(NativeUi.BG);getWindow().setNavigationBarColor(NativeUi.BG);
        buildChrome();showSummary();
    }

    private void buildChrome(){
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(NativeUi.BG);
        LinearLayout top=NativeUi.page(this);top.setPadding(NativeUi.dp(this,18),NativeUi.dp(this,14),NativeUi.dp(this,18),NativeUi.dp(this,12));
        top.addView(NativeUi.text(this,"STARPOINT GARAGE",20,true));
        top.addView(NativeUi.muted(this,staff.optString("display_name","Staff")+" · "+("owner_admin".equals(role)?"Owner/Admin":"Teknisi"),12));
        shell.addView(top,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=NativeUi.page(this);scroll.addView(content);shell.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        LinearLayout nav=new LinearLayout(this);nav.setPadding(NativeUi.dp(this,6),NativeUi.dp(this,7),NativeUi.dp(this,6),NativeUi.dp(this,7));nav.setBackgroundColor(Color.rgb(14,14,14));
        addNav(nav,"Ringkas",this::showSummary);addNav(nav,"Booking",this::showBookings);addNav(nav,"Bayar",this::showPayments);addNav(nav,"Check-in",this::showCheckin);
        if("owner_admin".equals(role))addNav(nav,"Owner",this::showOwner);
        addNav(nav,"Akun",this::showAccount);
        shell.addView(nav,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,NativeUi.dp(this,66)));setContentView(shell);
    }

    private void addNav(LinearLayout nav,String label,Runnable r){
        Button b=NativeUi.button(this,label,false);b.setTextSize(10);b.setOnClickListener(v->r.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1);if(nav.getChildCount()>0)lp.leftMargin=NativeUi.dp(this,3);nav.addView(b,lp);
    }

    private void clear(String title,String sub){content.removeAllViews();content.addView(NativeUi.text(this,title,24,true));content.addView(NativeUi.muted(this,sub,13));NativeUi.gap(this,content,16);}

    private void showSummary(){
        clear("Ringkasan Operasional","Data hari ini dari backend Starpoint Garage.");
        TextView loading=NativeUi.muted(this,"Memuat...",13);content.addView(loading);
        io.execute(()->{
            try{
                JSONObject x=ApiClient.asObject(ApiClient.rpc("staff_dashboard_summary",new JSONObject(),AppSession.access(this)));
                runOnUiThread(()->{
                    content.removeView(loading);
                    addStat("Booking hari ini",x.optInt("bookings_today"));
                    addStat("Menunggu pembayaran",x.optInt("awaiting_payment"));
                    addStat("Verifikasi pembayaran",x.optInt("waiting_verification"));
                    addStat("Sedang treatment",x.optInt("in_treatment"));
                    addStat("Member aktif",x.optInt("members_active"));
                    addStat("Revenue hari ini",NativeUi.rupiah(x.optInt("revenue_today")));
                });
            }catch(Exception e){runOnUiThread(()->loading.setText(NativeUi.errorText(e.getMessage())));}
        });
    }

    private void addStat(String name,Object value){
        LinearLayout c=NativeUi.card(this);c.addView(NativeUi.muted(this,name,12));c.addView(NativeUi.text(this,String.valueOf(value),22,true));NativeUi.margin(this,c,8);content.addView(c);
    }

    private void preloadMaps() throws Exception {
        JSONArray ss=ApiClient.getArray("services?select=id,name_id",AppSession.access(this));for(int i=0;i<ss.length();i++){JSONObject s=ss.optJSONObject(i);if(s!=null)services.put(s.optString("id"),s.optString("name_id"));}
        JSONArray mm=ApiClient.getArray("member_profiles?select=user_id,member_code,full_name&limit=1000",AppSession.access(this));for(int i=0;i<mm.length();i++){JSONObject m=mm.optJSONObject(i);if(m!=null)members.put(m.optString("user_id"),m.optString("full_name","Member")+" · "+m.optString("member_code",""));}
    }

    private void showBookings(){
        clear("Booking","Kelola status booking operasional.");
        TextView loading=NativeUi.muted(this,"Memuat booking...",13);content.addView(loading);
        io.execute(()->{
            try{
                preloadMaps();
                JSONArray rows=ApiClient.getArray("bookings?select=id,booking_code,member_id,service_id,vehicle_type,status,quoted_total,created_at&order=created_at.desc&limit=150",AppSession.access(this));
                runOnUiThread(()->{
                    content.removeView(loading);
                    if(rows.length()==0){content.addView(NativeUi.muted(this,"Belum ada booking.",13));return;}
                    for(int i=0;i<rows.length();i++){
                        JSONObject b=rows.optJSONObject(i);if(b==null)continue;
                        LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,b.optString("booking_code","Booking"),16,true));
                        c.addView(NativeUi.muted(this,members.getOrDefault(b.optString("member_id"),"Member")+" · "+services.getOrDefault(b.optString("service_id"),"Layanan"),12));
                        c.addView(NativeUi.text(this,b.optString("status","").toUpperCase(Locale.ROOT)+" · "+NativeUi.rupiah(b.optInt("quoted_total")),13,true));
                        NativeUi.gap(this,c,8);Button status=NativeUi.button(this,"Ubah status",false);status.setOnClickListener(v->changeStatus(b));c.addView(status,NativeUi.match(this));NativeUi.margin(this,c,8);content.addView(c);
                    }
                });
            }catch(Exception e){runOnUiThread(()->loading.setText("Gagal: "+NativeUi.errorText(e.getMessage())));}
        });
    }

    private void changeStatus(JSONObject booking){
        String[] labels={"Confirmed","Late","No Show","In Treatment","Completed","Cancelled"};
        String[] values={"confirmed","late","no_show","in_treatment","completed","cancelled"};
        Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        new AlertDialog.Builder(this).setTitle("Status "+booking.optString("booking_code")).setView(s).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->io.execute(()->{
            try{JSONObject b=new JSONObject();b.put("p_booking_id",booking.optString("id"));b.put("p_status",values[s.getSelectedItemPosition()]);ApiClient.rpc("staff_update_booking_status",b,AppSession.access(this));runOnUiThread(()->{NativeUi.toast(this,"Status diperbarui.");showBookings();});}
            catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        })).show();
    }

    private void showPayments(){
        clear("Verifikasi Pembayaran","Pembayaran manual yang menunggu verifikasi.");
        TextView loading=NativeUi.muted(this,"Memuat pembayaran...",13);content.addView(loading);
        io.execute(()->{
            try{
                preloadMaps();
                JSONArray rows=ApiClient.getArray("payments?select=id,booking_id,member_id,method,status,amount,notes,created_at&status=eq.waiting_verification&order=created_at.desc&limit=100",AppSession.access(this));
                runOnUiThread(()->{
                    content.removeView(loading);
                    if(rows.length()==0){content.addView(NativeUi.muted(this,"Tidak ada pembayaran menunggu verifikasi.",13));return;}
                    for(int i=0;i<rows.length();i++){
                        JSONObject p=rows.optJSONObject(i);if(p==null)continue;
                        LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,NativeUi.rupiah(p.optInt("amount")),18,true));
                        c.addView(NativeUi.muted(this,members.getOrDefault(p.optString("member_id"),"Member")+" · "+p.optString("method"),12));
                        NativeUi.gap(this,c,8);
                        LinearLayout row=new LinearLayout(this);Button ok=NativeUi.button(this,"Setujui",true);Button no=NativeUi.button(this,"Tolak",false);
                        ok.setOnClickListener(v->verifyPayment(p,true));no.setOnClickListener(v->verifyPayment(p,false));
                        row.addView(ok,new LinearLayout.LayoutParams(0,NativeUi.dp(this,46),1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,NativeUi.dp(this,46),1);lp.leftMargin=NativeUi.dp(this,6);row.addView(no,lp);c.addView(row,NativeUi.match(this));
                        NativeUi.margin(this,c,8);content.addView(c);
                    }
                });
            }catch(Exception e){runOnUiThread(()->loading.setText("Gagal: "+NativeUi.errorText(e.getMessage())));}
        });
    }

    private void verifyPayment(JSONObject p,boolean approve){
        io.execute(()->{
            try{JSONObject b=new JSONObject();b.put("p_payment_id",p.optString("id"));b.put("p_approved",approve);b.put("p_notes",approve?"Verified native app":"Rejected native app");ApiClient.rpc("staff_verify_manual_payment",b,AppSession.access(this));runOnUiThread(()->{NativeUi.toast(this,approve?"Pembayaran disetujui.":"Pembayaran ditolak.");showPayments();});}
            catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        });
    }

    private void showCheckin(){
        clear("Check-in Member","Scan QR member atau masukkan token QR.");
        qrInput=NativeUi.input(this,"SPGQ:xxxxxxxx-....");
        Button scan=NativeUi.button(this,"Scan QR dengan kamera",true);Button lookup=NativeUi.button(this,"Cari member",false);
        content.addView(qrInput,NativeUi.match(this));NativeUi.gap(this,content,8);content.addView(scan,NativeUi.match(this));NativeUi.gap(this,content,8);content.addView(lookup,NativeUi.match(this));
        scan.setOnClickListener(v->scanQr());lookup.setOnClickListener(v->lookupQr(qrInput.getText().toString().trim()));
    }

    private void scanQr(){
        GmsBarcodeScannerOptions o=new GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build();
        GmsBarcodeScanner scanner=GmsBarcodeScanning.getClient(this,o);
        scanner.startScan().addOnSuccessListener(barcode->{String raw=barcode.getRawValue();if(raw!=null){qrInput.setText(raw);lookupQr(raw);}}).addOnFailureListener(e->NativeUi.toast(this,"Scan gagal."));
    }

    private void lookupQr(String raw){
        if(raw.isEmpty()){NativeUi.toast(this,"Masukkan QR member.");return;}
        io.execute(()->{
            try{JSONObject b=new JSONObject();b.put("p_qr_value",raw);lookedUpMember=ApiClient.asObject(ApiClient.rpc("staff_lookup_member_by_qr",b,AppSession.access(this)));runOnUiThread(this::renderLookup);}
            catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        });
    }

    private void renderLookup(){
        if(lookedUpMember==null)return;
        LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,lookedUpMember.optString("full_name","Member"),18,true));c.addView(NativeUi.muted(this,"@"+lookedUpMember.optString("username","")+" · "+lookedUpMember.optString("level","classic").toUpperCase(Locale.ROOT),12));c.addView(NativeUi.text(this,"Status "+lookedUpMember.optString("status")+" · "+lookedUpMember.optInt("reward_points")+" point",13,true));
        NativeUi.gap(this,c,8);Button check=NativeUi.button(this,"Check-in",true);check.setOnClickListener(v->openCheckinDialog());c.addView(check,NativeUi.match(this));NativeUi.margin(this,c,12);content.addView(c);
    }

    private void openCheckinDialog(){
        LinearLayout box=NativeUi.page(this);EditText booking=NativeUi.input(this,"Booking ID (opsional)");EditText payment=NativeUi.input(this,"Payment ID (opsional)");EditText reason=NativeUi.input(this,"Override reason (Owner, opsional)");
        box.addView(booking,NativeUi.match(this));NativeUi.gap(this,box,8);box.addView(payment,NativeUi.match(this));if("owner_admin".equals(role)){NativeUi.gap(this,box,8);box.addView(reason,NativeUi.match(this));}
        new AlertDialog.Builder(this).setTitle("Konfirmasi check-in").setView(box).setNegativeButton("Batal",null).setPositiveButton("Check-in",(d,w)->io.execute(()->{
            try{JSONObject b=new JSONObject();b.put("p_qr_value",qrInput.getText().toString().trim());b.put("p_booking_id",booking.getText().toString().trim().isEmpty()?JSONObject.NULL:booking.getText().toString().trim());b.put("p_payment_id",payment.getText().toString().trim().isEmpty()?JSONObject.NULL:payment.getText().toString().trim());b.put("p_override_reason",reason.getText().toString().trim().isEmpty()?JSONObject.NULL:reason.getText().toString().trim());JSONObject out=ApiClient.asObject(ApiClient.rpc("staff_create_checkin",b,AppSession.access(this)));runOnUiThread(()->{NativeUi.toast(this,"Check-in "+out.optString("full_name","member")+" berhasil.");showCheckin();});}
            catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        })).show();
    }

    private void showOwner(){
        clear("Owner Control","Kontrol member langsung dari aplikasi native.");
        if(!"owner_admin".equals(role)){content.addView(NativeUi.muted(this,"Akses Owner diperlukan.",13));return;}
        TextView loading=NativeUi.muted(this,"Memuat member...",13);content.addView(loading);
        io.execute(()->{
            try{
                JSONArray rows=ApiClient.getArray("member_profiles?select=user_id,member_code,full_name,username,status&order=created_at.desc&limit=200",AppSession.access(this));
                runOnUiThread(()->{
                    content.removeView(loading);
                    for(int i=0;i<rows.length();i++){
                        JSONObject m=rows.optJSONObject(i);if(m==null)continue;
                        LinearLayout c=NativeUi.card(this);c.addView(NativeUi.text(this,m.optString("full_name","Member"),16,true));c.addView(NativeUi.muted(this,m.optString("member_code","")+" · @"+m.optString("username","")+" · "+m.optString("status",""),12));
                        NativeUi.gap(this,c,8);LinearLayout actions=new LinearLayout(this);Button state=NativeUi.button(this,"Status",false);Button points=NativeUi.button(this,"Point",false);state.setOnClickListener(v->ownerStatus(m));points.setOnClickListener(v->ownerPoints(m));actions.addView(state,new LinearLayout.LayoutParams(0,NativeUi.dp(this,44),1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,NativeUi.dp(this,44),1);lp.leftMargin=NativeUi.dp(this,6);actions.addView(points,lp);c.addView(actions,NativeUi.match(this));NativeUi.margin(this,c,8);content.addView(c);
                    }
                });
            }catch(Exception e){runOnUiThread(()->loading.setText("Gagal: "+NativeUi.errorText(e.getMessage())));}
        });
    }

    private void ownerStatus(JSONObject m){
        String[] labels={"Registered","Active","Suspended"};String[] vals={"registered","active","suspended"};Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));EditText reason=NativeUi.input(this,"Alasan (disarankan)");
        LinearLayout box=NativeUi.page(this);box.addView(s);NativeUi.gap(this,box,8);box.addView(reason,NativeUi.match(this));
        new AlertDialog.Builder(this).setTitle("Status "+m.optString("full_name")).setView(box).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->io.execute(()->{
            try{JSONObject b=new JSONObject();b.put("p_member_id",m.optString("user_id"));b.put("p_status",vals[s.getSelectedItemPosition()]);b.put("p_reason",reason.getText().toString().trim());ApiClient.rpc("owner_update_member_status",b,AppSession.access(this));runOnUiThread(()->{NativeUi.toast(this,"Status member diperbarui.");showOwner();});}catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        })).show();
    }

    private void ownerPoints(JSONObject m){
        EditText delta=NativeUi.input(this,"Delta point, contoh 10 atau -10");EditText desc=NativeUi.input(this,"Keterangan");LinearLayout box=NativeUi.page(this);box.addView(delta,NativeUi.match(this));NativeUi.gap(this,box,8);box.addView(desc,NativeUi.match(this));
        new AlertDialog.Builder(this).setTitle("Adjust point "+m.optString("full_name")).setView(box).setNegativeButton("Batal",null).setPositiveButton("Simpan",(d,w)->io.execute(()->{
            try{int x=Integer.parseInt(delta.getText().toString().trim());JSONObject b=new JSONObject();b.put("p_member_id",m.optString("user_id"));b.put("p_delta",x);b.put("p_description",desc.getText().toString().trim());ApiClient.rpc("owner_adjust_member_points",b,AppSession.access(this));runOnUiThread(()->NativeUi.toast(this,"Point diperbarui."));}catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        })).show();
    }

    private void showAccount(){
        clear("Akun Staff","Sesi aktif di aplikasi native.");
        content.addView(NativeUi.text(this,staff.optString("display_name","Staff"),18,true));
        content.addView(NativeUi.muted(this,"Role: "+role,13));
        NativeUi.gap(this,content,16);Button out=NativeUi.button(this,"Keluar",false);out.setOnClickListener(v->logout());content.addView(out,NativeUi.match(this));
    }

    private void logout(){AppSession.clear(this);startActivity(new Intent(this,MainActivity.class));finish();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
