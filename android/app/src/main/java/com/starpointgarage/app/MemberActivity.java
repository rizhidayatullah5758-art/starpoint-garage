package com.starpointgarage.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MemberActivity extends Activity {
    private final ExecutorService io = Executors.newFixedThreadPool(3);
    private LinearLayout shell, content;
    private JSONObject profile = new JSONObject();
    private final HashMap<String,String> serviceNames = new HashMap<>();
    private Uri pendingAvatar;
    private Uri pendingProof;
    private String pendingPaymentBookingId;
    private TextView pendingProofLabel;
    private static final int PICK_AVATAR = 6001;
    private static final int PICK_PROOF = 6002;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!"member".equals(AppSession.type(this))) { logout(); return; }
        getWindow().setStatusBarColor(NativeUi.BG);
        getWindow().setNavigationBarColor(NativeUi.BG);
        buildChrome();
        showHome();
        refreshProfile(false);
    }

    private void buildChrome() {
        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(NativeUi.BG);

        LinearLayout top = NativeUi.page(this);
        top.setPadding(NativeUi.dp(this,18),NativeUi.dp(this,14),NativeUi.dp(this,18),NativeUi.dp(this,12));
        top.addView(NativeUi.text(this,"STARPOINT GARAGE",20,true));
        top.addView(NativeUi.muted(this,"Member App",12));
        shell.addView(top,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = NativeUi.page(this);
        scroll.addView(content);
        shell.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(NativeUi.dp(this,8),NativeUi.dp(this,8),NativeUi.dp(this,8),NativeUi.dp(this,8));
        nav.setBackgroundColor(Color.rgb(14,14,14));
        addNav(nav,"Beranda",this::showHome);
        addNav(nav,"Booking",this::showServices);
        addNav(nav,"Riwayat",this::showHistory);
        addNav(nav,"QR",this::showQr);
        addNav(nav,"Profil",this::showProfile);
        shell.addView(nav,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,NativeUi.dp(this,66)));
        setContentView(shell);
    }

    private void addNav(LinearLayout nav,String label,Runnable action) {
        Button b = NativeUi.button(this,label,false);
        b.setTextSize(11);
        b.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1);
        if(nav.getChildCount()>0) lp.leftMargin=NativeUi.dp(this,4);
        nav.addView(b,lp);
    }

    private void clear(String title,String sub) {
        content.removeAllViews();
        content.addView(NativeUi.text(this,title,24,true));
        content.addView(NativeUi.muted(this,sub,13));
        NativeUi.gap(this,content,16);
    }

    private void showHome() {
        clear("Beranda","Ringkasan akun dan aktivitas Starpoint Garage.");
        JSONObject p = profile.length()>0 ? profile : AppSession.profile(this);
        LinearLayout card=NativeUi.card(this);
        card.addView(NativeUi.text(this,p.optString("full_name","Member"),20,true));
        card.addView(NativeUi.muted(this,"@"+p.optString("username","-")+" · "+p.optString("member_code","-"),13));
        NativeUi.gap(this,card,10);
        card.addView(NativeUi.text(this,"Status: "+p.optString("status","registered"),14,true));
        content.addView(card,NativeUi.match(this));
        NativeUi.gap(this,content,12);

        LinearLayout membership=NativeUi.card(this);
        TextView mt=NativeUi.text(this,"Membership",17,true);
        membership.addView(mt);
        TextView mv=NativeUi.muted(this,"Memuat...",13);
        membership.addView(mv);
        content.addView(membership,NativeUi.match(this));

        io.execute(() -> {
            try {
                String uid=AppSession.userId(this);
                JSONArray a=ApiClient.getArray("member_membership_summary?select=reward_points,qualifying_points_12m,level&member_id=eq."+ApiClient.q(uid),AppSession.access(this));
                JSONObject x=a.optJSONObject(0);
                String text=x==null?"Classic · 0 point":x.optString("level","classic").toUpperCase(Locale.ROOT)+" · "+x.optInt("reward_points",0)+" reward point · "+x.optInt("qualifying_points_12m",0)+" qualifying";
                runOnUiThread(()->mv.setText(text));
            } catch(Exception e) { runOnUiThread(()->mv.setText("Membership belum dapat dimuat.")); }
        });

        NativeUi.gap(this,content,12);
        Button booking=NativeUi.button(this,"Buat booking",true);
        booking.setOnClickListener(v->showServices());
        content.addView(booking,NativeUi.match(this));
        NativeUi.gap(this,content,8);
        Button history=NativeUi.button(this,"Lihat riwayat booking",false);
        history.setOnClickListener(v->showHistory());
        content.addView(history,NativeUi.match(this));
    }

    private void refreshProfile(boolean redraw) {
        io.execute(() -> {
            try {
                String uid=AppSession.userId(this);
                JSONArray a=ApiClient.getArray("member_profiles?select=user_id,member_code,username,email,full_name,status,qr_token,onboarding_completed,phone,birth_date,avatar_path,marketing_notifications&user_id=eq."+ApiClient.q(uid),AppSession.access(this));
                JSONObject p=a.optJSONObject(0);
                if(p!=null){ profile=p; AppSession.updateProfile(this,p); }
                if(redraw) runOnUiThread(this::showProfile);
            } catch(Exception ignored){}
        });
    }

    private void showProfile() {
        clear("Profil Member","Data ini dipakai untuk booking dan membership.");
        JSONObject p=profile.length()>0?profile:AppSession.profile(this);

        EditText name=NativeUi.input(this,"Nama lengkap"); name.setText(p.optString("full_name",""));
        EditText birth=NativeUi.input(this,"Tanggal lahir YYYY-MM-DD"); birth.setText(p.optString("birth_date",""));
        Switch marketing=new Switch(this); marketing.setText("Notifikasi marketing"); marketing.setTextColor(NativeUi.TEXT); marketing.setChecked(p.optBoolean("marketing_notifications",true));
        TextView avatar=NativeUi.muted(this,p.optString("avatar_path","").isEmpty()?"Avatar belum diupload":"Avatar tersimpan",13);
        Button choose=NativeUi.button(this,"Pilih avatar",false);
        choose.setOnClickListener(v->pickImage(PICK_AVATAR));
        Button save=NativeUi.button(this,"Simpan profil",true);

        content.addView(name,NativeUi.match(this)); NativeUi.gap(this,content,8);
        content.addView(birth,NativeUi.match(this)); NativeUi.gap(this,content,8);
        content.addView(marketing,NativeUi.match(this)); NativeUi.gap(this,content,8);
        content.addView(avatar); NativeUi.gap(this,content,8);
        content.addView(choose,NativeUi.match(this)); NativeUi.gap(this,content,10);
        content.addView(save,NativeUi.match(this));

        save.setOnClickListener(v -> {
            save.setEnabled(false);
            io.execute(() -> {
                try {
                    String path=p.optString("avatar_path","");
                    if(pendingAvatar!=null) {
                        byte[] bytes=readUri(pendingAvatar,3*1024*1024);
                        String mime=getContentResolver().getType(pendingAvatar);
                        String ext="image/png".equals(mime)?"png":"image/webp".equals(mime)?"webp":"jpg";
                        path=AppSession.userId(this)+"/avatar-"+System.currentTimeMillis()+"."+ext;
                        ApiClient.upload("member-avatars",path,bytes,mime,AppSession.access(this));
                    }
                    JSONObject body=new JSONObject();
                    body.put("p_full_name",name.getText().toString().trim());
                    body.put("p_avatar_path",path.isEmpty()?JSONObject.NULL:path);
                    body.put("p_birth_date",birth.getText().toString().trim().isEmpty()?JSONObject.NULL:birth.getText().toString().trim());
                    body.put("p_marketing_notifications",marketing.isChecked());
                    JSONObject updated=ApiClient.asObject(ApiClient.rpc("update_my_member_profile",body,AppSession.access(this)));
                    profile=updated; AppSession.updateProfile(this,updated); pendingAvatar=null;
                    runOnUiThread(()->{ NativeUi.toast(this,"Profil tersimpan."); showProfile(); });
                } catch(Exception e) {
                    runOnUiThread(()->{ save.setEnabled(true); NativeUi.toast(this,NativeUi.errorText(e.getMessage())); });
                }
            });
        });
    }

    private void showServices() {
        clear("Booking","Pilih layanan dan slot yang tersedia.");
        TextView loading=NativeUi.muted(this,"Memuat layanan...",13);
        content.addView(loading);
        io.execute(() -> {
            try {
                JSONArray services=ApiClient.getArray("services?select=id,code,name_id,description_id,booking_enabled,requires_deposit,minimum_deposit,duration_label,duration_minutes&active=eq.true&booking_enabled=eq.true&order=sort_order.asc",AppSession.access(this));
                runOnUiThread(()->{
                    content.removeView(loading);
                    if(services.length()==0){content.addView(NativeUi.muted(this,"Belum ada layanan booking.",13));return;}
                    for(int i=0;i<services.length();i++){
                        JSONObject s=services.optJSONObject(i); if(s==null)continue;
                        serviceNames.put(s.optString("id"),s.optString("name_id","Layanan"));
                        LinearLayout card=NativeUi.card(this);
                        card.addView(NativeUi.text(this,s.optString("name_id","Layanan"),17,true));
                        card.addView(NativeUi.muted(this,s.optString("duration_label","")+" · "+(s.optBoolean("requires_deposit")?"DP min "+NativeUi.rupiah(s.optInt("minimum_deposit")):"Tanpa DP"),12));
                        NativeUi.gap(this,card,8);
                        Button choose=NativeUi.button(this,"Pilih layanan",true);
                        choose.setOnClickListener(v->openBookingDialog(s));
                        card.addView(choose,NativeUi.match(this));
                        NativeUi.margin(this,card,10); content.addView(card);
                    }
                });
            } catch(Exception e){ runOnUiThread(()->loading.setText("Gagal memuat layanan: "+NativeUi.errorText(e.getMessage()))); }
        });
    }

    private void openBookingDialog(JSONObject service) {
        LinearLayout box=NativeUi.page(this);
        box.setPadding(NativeUi.dp(this,16),NativeUi.dp(this,8),NativeUi.dp(this,16),NativeUi.dp(this,8));
        EditText vehicle=NativeUi.input(this,"Kendaraan / model");
        String[] labels={"Small","Medium","Large","Big Bike","Luxury"};
        String[] values={"small","medium","large","big_bike","luxury"};
        Spinner category=new Spinner(this);
        category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));
        Spinner slots=new Spinner(this);
        slots.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Memuat slot..."}));
        box.addView(vehicle,NativeUi.match(this)); NativeUi.gap(this,box,8); box.addView(category); NativeUi.gap(this,box,8); box.addView(slots);

        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(service.optString("name_id","Booking"))
                .setView(box)
                .setNegativeButton("Batal",null)
                .setPositiveButton("Booking",null)
                .create();
        ArrayList<JSONObject> slotRows=new ArrayList<>();
        dialog.setOnShowListener(x->{
            Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(false);
            positive.setOnClickListener(v->{
                if(slotRows.isEmpty()||vehicle.getText().toString().trim().isEmpty()){NativeUi.toast(this,"Isi kendaraan dan pilih slot.");return;}
                JSONObject slot=slotRows.get(slots.getSelectedItemPosition());
                positive.setEnabled(false);
                io.execute(()->{
                    try{
                        JSONObject b=new JSONObject();
                        b.put("p_service_id",service.getString("id"));
                        b.put("p_slot_id",slot.getString("id"));
                        b.put("p_vehicle_type",vehicle.getText().toString().trim());
                        b.put("p_vehicle_category",values[category.getSelectedItemPosition()]);
                        b.put("p_notes",JSONObject.NULL);
                        JSONObject created=ApiClient.asObject(ApiClient.rpc("create_member_booking",b,AppSession.access(this)));
                        runOnUiThread(()->{dialog.dismiss();NativeUi.toast(this,"Booking "+created.optString("booking_code","")+" dibuat.");showHistory();});
                    }catch(Exception e){runOnUiThread(()->{positive.setEnabled(true);NativeUi.toast(this,NativeUi.errorText(e.getMessage()));});}
                });
            });
        });
        dialog.show();

        io.execute(()->{
            try{
                JSONObject b=new JSONObject(); b.put("p_service_id",service.getString("id"));
                Object out=ApiClient.rpc("available_booking_slots",b,AppSession.access(this));
                JSONArray arr=out instanceof JSONArray?(JSONArray)out:new JSONArray();
                ArrayList<String> names=new ArrayList<>();
                for(int i=0;i<arr.length();i++){
                    JSONObject s=arr.optJSONObject(i); if(s==null)continue;
                    slotRows.add(s); names.add(formatTime(s.optString("starts_at"))+" · sisa "+s.optInt("available_capacity"));
                }
                runOnUiThread(()->{
                    slots.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names.isEmpty()?new String[]{"Slot tidak tersedia"}:names));
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!slotRows.isEmpty());
                });
            }catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        });
    }

    private void showHistory() {
        clear("Riwayat Booking","Status booking dan pembayaran.");
        TextView loading=NativeUi.muted(this,"Memuat riwayat...",13); content.addView(loading);
        io.execute(()->{
            try{
                String uid=AppSession.userId(this);
                JSONArray bookings=ApiClient.getArray("bookings?select=id,booking_code,service_id,vehicle_type,vehicle_category,status,quoted_total,deposit_required,deposit_forfeited,reschedule_count,created_at&member_id=eq."+ApiClient.q(uid)+"&order=created_at.desc&limit=100",AppSession.access(this));
                JSONArray services=ApiClient.getArray("services?select=id,name_id&active=eq.true",AppSession.access(this));
                for(int i=0;i<services.length();i++){JSONObject s=services.optJSONObject(i);if(s!=null)serviceNames.put(s.optString("id"),s.optString("name_id"));}
                runOnUiThread(()->{
                    content.removeView(loading);
                    if(bookings.length()==0){content.addView(NativeUi.muted(this,"Belum ada booking.",13));return;}
                    for(int i=0;i<bookings.length();i++){
                        JSONObject b=bookings.optJSONObject(i); if(b==null)continue;
                        LinearLayout card=NativeUi.card(this);
                        card.addView(NativeUi.text(this,b.optString("booking_code","Booking"),17,true));
                        card.addView(NativeUi.muted(this,serviceNames.getOrDefault(b.optString("service_id"),"Layanan")+" · "+b.optString("vehicle_type",""),12));
                        NativeUi.gap(this,card,8);
                        card.addView(NativeUi.text(this,b.optString("status","").toUpperCase(Locale.ROOT)+" · "+NativeUi.rupiah(b.optInt("quoted_total")),14,true));
                        if(b.optBoolean("deposit_forfeited")) card.addView(NativeUi.muted(this,"DP hangus",12));
                        String st=b.optString("status","");
                        if(!"cancelled".equals(st)&&!"no_show".equals(st)&&!"completed".equals(st)&&b.optInt("quoted_total")>0){
                            NativeUi.gap(this,card,8);
                            Button pay=NativeUi.button(this,"Pembayaran",true); pay.setOnClickListener(v->openPayment(b)); card.addView(pay,NativeUi.match(this));
                        }
                        if("awaiting_payment".equals(st)||"confirmed".equals(st)){
                            NativeUi.gap(this,card,6);
                            Button cancel=NativeUi.button(this,"Batalkan booking",false); cancel.setOnClickListener(v->cancelBooking(b.optString("id"))); card.addView(cancel,NativeUi.match(this));
                        }
                        NativeUi.margin(this,card,10); content.addView(card);
                    }
                });
            }catch(Exception e){runOnUiThread(()->loading.setText("Gagal memuat booking: "+NativeUi.errorText(e.getMessage())));}
        });
    }

    private void cancelBooking(String id) {
        new AlertDialog.Builder(this).setTitle("Batalkan booking?").setMessage("DP yang sudah dibayar dapat hangus sesuai aturan booking.")
                .setNegativeButton("Tidak",null).setPositiveButton("Ya",(d,w)->io.execute(()->{
                    try{JSONObject b=new JSONObject();b.put("p_booking_id",id);ApiClient.rpc("cancel_member_booking",b,AppSession.access(this));runOnUiThread(()->{NativeUi.toast(this,"Booking dibatalkan.");showHistory();});}
                    catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
                })).show();
    }

    private void openPayment(JSONObject booking) {
        io.execute(()->{
            try{
                JSONObject q=new JSONObject();q.put("p_booking_id",booking.getString("id"));
                JSONObject sum=ApiClient.asObject(ApiClient.rpc("booking_payment_summary",q,AppSession.access(this)));
                runOnUiThread(()->showPaymentDialog(booking,sum));
            }catch(Exception e){runOnUiThread(()->NativeUi.toast(this,NativeUi.errorText(e.getMessage())));}
        });
    }

    private void showPaymentDialog(JSONObject booking,JSONObject sum) {
        int remaining=sum.optInt("remaining_balance",0), minimum=sum.optInt("minimum_payment_now",0);
        if(remaining<=0){NativeUi.toast(this,"Booking sudah lunas.");return;}
        LinearLayout box=NativeUi.page(this);
        box.addView(NativeUi.text(this,"Sisa "+NativeUi.rupiah(remaining),17,true));
        box.addView(NativeUi.muted(this,"Minimum sekarang "+NativeUi.rupiah(minimum),12));
        NativeUi.gap(this,box,8);
        Spinner method=new Spinner(this);
        String[] ml={"QRIS BRI Manual","Transfer BRI","Cash"};
        String[] mv={"qris_bri_manual","bank_transfer_bri","cash"};
        method.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ml));
        EditText amount=NativeUi.input(this,"Nominal");amount.setText(String.valueOf(minimum));
        EditText notes=NativeUi.input(this,"Catatan (opsional)");
        Button proof=NativeUi.button(this,"Pilih bukti pembayaran (opsional)",false);
        pendingProofLabel=NativeUi.muted(this,"Belum ada bukti dipilih.",12);
        proof.setOnClickListener(v->pickImage(PICK_PROOF));
        box.addView(method);NativeUi.gap(this,box,8);box.addView(amount,NativeUi.match(this));NativeUi.gap(this,box,8);box.addView(notes,NativeUi.match(this));NativeUi.gap(this,box,8);box.addView(proof,NativeUi.match(this));box.addView(pendingProofLabel);
        pendingPaymentBookingId=booking.optString("id");
        pendingProof=null;

        AlertDialog d=new AlertDialog.Builder(this).setTitle("Pembayaran Manual").setView(box).setNegativeButton("Batal",null).setPositiveButton("Kirim",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int a; try{a=Integer.parseInt(amount.getText().toString().trim());}catch(Exception e){NativeUi.toast(this,"Nominal tidak valid.");return;}
            if(a<minimum||a>remaining){NativeUi.toast(this,"Nominal harus "+NativeUi.rupiah(minimum)+" s/d "+NativeUi.rupiah(remaining));return;}
            Button btn=d.getButton(AlertDialog.BUTTON_POSITIVE);btn.setEnabled(false);
            io.execute(()->{
                try{
                    String path=null;
                    if(pendingProof!=null){
                        byte[] bytes=readUri(pendingProof,5*1024*1024);
                        String mime=getContentResolver().getType(pendingProof);
                        String ext="image/png".equals(mime)?"png":"image/webp".equals(mime)?"webp":"jpg";
                        path=AppSession.userId(this)+"/payment-"+System.currentTimeMillis()+"."+ext;
                        ApiClient.upload("payment-proofs",path,bytes,mime,AppSession.access(this));
                    }
                    JSONObject b=new JSONObject();
                    b.put("p_booking_id",pendingPaymentBookingId);b.put("p_method",mv[method.getSelectedItemPosition()]);b.put("p_amount",a);b.put("p_proof_path",path==null?JSONObject.NULL:path);b.put("p_notes",notes.getText().toString().trim());
                    ApiClient.rpc("create_manual_payment",b,AppSession.access(this));
                    runOnUiThread(()->{d.dismiss();NativeUi.toast(this,"Pembayaran dikirim untuk verifikasi.");showHistory();});
                }catch(Exception e){runOnUiThread(()->{btn.setEnabled(true);NativeUi.toast(this,NativeUi.errorText(e.getMessage()));});}
            });
        }));
        d.show();
    }

    private void showQr() {
        clear("QR Member","Tunjukkan QR ini kepada Teknisi saat check-in.");
        JSONObject p=profile.length()>0?profile:AppSession.profile(this);
        String token=p.optString("qr_token","");
        if(token.isEmpty()){content.addView(NativeUi.muted(this,"QR belum tersedia. Muat ulang profil.",13));refreshProfile(false);return;}
        String payload="SPGQ:"+token;
        try{
            BitMatrix m=new MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE,720,720);
            Bitmap bmp=Bitmap.createBitmap(720,720,Bitmap.Config.RGB_565);
            for(int x=0;x<720;x++)for(int y=0;y<720;y++)bmp.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);
            ImageView img=new ImageView(this);img.setImageBitmap(bmp);img.setAdjustViewBounds(true);img.setBackgroundColor(Color.WHITE);img.setPadding(20,20,20,20);
            content.addView(img,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
            NativeUi.gap(this,content,10);
            content.addView(NativeUi.center(this,p.optString("member_code",""),16,true));
        }catch(Exception e){content.addView(NativeUi.muted(this,"QR gagal dibuat.",13));}
    }

    private void pickImage(int code) {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,code);
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(requestCode==PICK_AVATAR){pendingAvatar=uri;NativeUi.toast(this,"Avatar dipilih. Tekan Simpan profil.");}
        if(requestCode==PICK_PROOF){pendingProof=uri;if(pendingProofLabel!=null)pendingProofLabel.setText("Bukti pembayaran dipilih.");}
    }

    private byte[] readUri(Uri uri,int max) throws Exception {
        try(InputStream in=getContentResolver().openInputStream(uri)){
            if(in==null)throw new Exception("FILE_OPEN_FAILED");
            byte[] b=ApiClient.readAll(in);if(b.length>max)throw new Exception("FILE_TOO_LARGE");return b;
        }
    }

    private String formatTime(String iso) {
        try{
            OffsetDateTime d=OffsetDateTime.parse(iso);
            return d.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm",new Locale("id","ID")));
        }catch(Exception e){return iso;}
    }

    private void logout() {
        AppSession.clear(this);
        startActivity(new Intent(this,MainActivity.class));
        finish();
    }

    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
