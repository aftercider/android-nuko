package org.nekonium.androidnuko.services;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import org.nekonium.androidnuko.R;
import org.nekonium.androidnuko.activities.MainActivity;
import org.nekonium.androidnuko.network.EtherscanAPI;
import org.nekonium.androidnuko.utils.ExchangeCalculator;
import org.nekonium.androidnuko.utils.WalletStorage;

import static org.nekonium.androidnuko.utils.ResponseParser.remove0x;

public class NotificationService extends IntentService {

    public NotificationService() {
        super("Notification Service");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean("notifications_new_message", true) || WalletStorage.getInstance(this).get().size() <= 0) {
            NotificationLauncher.getInstance().stop();
            return;
        }

        try {
            EtherscanAPI.getInstance().getBalances(WalletStorage.getInstance(this).get(), new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                }

                @Override
                public void onResponse(Call call, final Response response) throws IOException {
                    JSONArray data = null;
                    try {
                        data = new JSONObject(response.body().string()).getJSONArray("result");
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(NotificationService.this);

                        boolean notify = false;
                        BigInteger amount = new BigInteger("0");
                        String address = "";
                        SharedPreferences.Editor editor = preferences.edit();
                        for (int i = 0; i < data.length(); i++) {
                            if (!preferences.getString(data.getJSONObject(i).getString("account"), data.getJSONObject(i).getString("balance")).equals(data.getJSONObject(i).getString("balance"))) {
                                Log.d("re",remove0x(data.getJSONObject(i).getString("balance")));
                                Log.d("re",data.getJSONObject(i).getString("account"));
                                //Log.d("re",preferences.getString(data.getJSONObject(i).getString("account"), data.getJSONObject(i).getString("balance")));
                                if (new BigInteger(remove0x(preferences.getString(data.getJSONObject(i).getString("account"), remove0x(data.getJSONObject(i).getString("balance")))),16).compareTo(new BigInteger(remove0x(data.getJSONObject(i).getString("balance")),16)) < 1) { // Nur wenn höhere Balance als vorher
                                    notify = true;
                                    address = data.getJSONObject(i).getString("account");
                                    amount = amount.add((new BigInteger(remove0x(data.getJSONObject(i).getString("balance")),16).subtract(new BigInteger(remove0x(preferences.getString(address, "0")),16))));
                                    if(amount.intValue() == 0)
                                        notify = false;
                                }
                            }
                            editor.putString(data.getJSONObject(i).getString("account"), remove0x(data.getJSONObject(i).getString("balance")));
                        }
                        editor.commit();
                        if (notify) {
                            try {
                                String amountS = new BigDecimal(amount).divide(ExchangeCalculator.ONE_ETHER, 4, BigDecimal.ROUND_DOWN).toPlainString();
                                sendNotification(address, amountS);
                            } catch (Exception e) {

                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendNotification(String address, String amount) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_notification)
                //.setLargeIcon(Blockies.createIcon(address.toLowerCase()))
                .setColor(0x2d435c)
                .setTicker(getString(R.string.notification_ticker))
                .setLights(Color.CYAN, 3000, 3000)
                .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
                .setContentTitle(this.getResources().getString(R.string.notification_title))
                .setAutoCancel(true)
                .setContentText(amount + " NUKO");

        if (android.os.Build.VERSION.SDK_INT >= 18) // Android bug in 4.2, just disable it for everyone then...
            builder.setVibrate(new long[]{1000, 1000});

        final NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        Intent main = new Intent(this, MainActivity.class);
        main.putExtra("STARTAT", 1);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                main, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentIntent(contentIntent);

        final int mNotificationId = (int) (Math.random() * 150);
        mNotifyMgr.notify(mNotificationId, builder.build());
    }


}
