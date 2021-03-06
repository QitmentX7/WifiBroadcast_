package com.example.qitment.wifibroadcast;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Created by qitment on 04.12.17.
 */

public abstract class WifiActivity extends AppCompatActivity {
    public static final String PSK = "PSK";
    public static final String WEP = "WEP";
    public static final String OPEN = "Open";


    private final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture taskHandler;

    private ProgressDialog progressDialog;
    private ScanReceiver scanReceiver;
    private ConnectionReceiver connectionReceiver;

    /**
     * Get the timeout in seconds for connecting to the wifi network
     *
     * @return wifi network connection timeout
     */
    protected abstract int getSecondsTimeout();

    /**
     * Get the SSID of the wifi network
     *
     * @return SSID of wifi network to connect to
     */
    protected abstract String getWifiSSID();

    /**
     * Get the password/key of the wifi network
     *
     * @return passwork/key of wifi network
     */
    protected abstract String getWifiPass();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //handleWIFI();
    }

    /**
     * Start connecting to specific wifi network
     */
    protected void handleWIFI() {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (!wifi.isWifiEnabled()) {
            wifi.setWifiEnabled(true);
        }
        if(!permisionLocationOn()) {
            setLocationPermission();
        }
        else
        {
            if (checkLocationTurnOn()) {
                connectToSpecificNetwork();
            }
        }
    }


    public Boolean permisionLocationOn() {
        boolean permissionGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (permissionGranted) {
            return true;
        } else {
            return false;
        }
    }
    public void setLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
        }

    }

    public Boolean checkLocationTurnOn(){
        boolean onLocation=true;
        boolean permissionGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && permissionGranted) {
            LocationManager lm = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
            boolean gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if(!gps_enabled){
                onLocation =false;
                android.support.v7.app.AlertDialog.Builder dialog = new android.support.v7.app.AlertDialog.Builder(this);
                dialog.setMessage("Please turn on your location");
                dialog.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                        Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(myIntent);
                    }
                });
                dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface paramDialogInterface, int paramInt) {

                    }
                });
                dialog.show();
            }
        }
        return onLocation;
    }


    /**
     * Get the security type of the wireless network
     *
     * @param scanResult the wifi scan result
     * @return one of WEP, PSK of OPEN
     */
    private String getScanResultSecurity(ScanResult scanResult) {
        final String cap = scanResult.capabilities;
        final String[] securityModes = {WEP, PSK};
        for (int i = securityModes.length - 1; i >= 0; i--) {
            if (cap.contains(securityModes[i])) {
                return securityModes[i];
            }
        }

        return OPEN;
    }



    /**
     * Start to connect to a specific wifi network
     */
    private void connectToSpecificNetwork() {
        final WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        WifiInfo wifiInfo = wifi.getConnectionInfo();
        if (networkInfo.isConnected() && wifiInfo.getSSID().replace("\"", "").equals(getWifiSSID())) {
            return;
        }
        else {
            wifi.disconnect();
        }
        progressDialog = ProgressDialog.show(this, getString(R.string.connecting), String.format(getString(R.string.connecting_to_wifi), getWifiSSID()));
        taskHandler = worker.schedule(new TimeoutTask(), getSecondsTimeout(), TimeUnit.SECONDS);
        scanReceiver = new ScanReceiver();
        registerReceiver(scanReceiver
                , new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        wifi.startScan();
    }

    /**
     * Broadcast receiver for connection related events
     */
    private class ConnectionReceiver extends BroadcastReceiver {

        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);


        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            WifiInfo wifiInfo = wifi.getConnectionInfo();
            if (networkInfo.isConnected()) {
                if (wifiInfo.getSSID().replace("\"", "").equals(getWifiSSID())) {
                    unregisterReceiver(this);
                    if (taskHandler != null) {
                        taskHandler.cancel(true);
                    }
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                    }
                }
            }
        }
    }


    /**
     * Broadcast receiver for wifi scanning related events
     */
    private class ScanReceiver extends BroadcastReceiver {


        @Override
        public void onReceive(Context context, Intent intent) {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            List<ScanResult> scanResultList = wifi.getScanResults();
            boolean found = false;
            String security = null;
            for (ScanResult scanResult : scanResultList) {
                if (scanResult.SSID.equals(getWifiSSID())) {
                    security = getScanResultSecurity(scanResult);
                    found = true;
                    break; // found don't need continue
                }
            }
            if (!found) {
                // if no wifi network with the specified ssid is not found exit
                if (progressDialog != null) {
                    progressDialog.dismiss();
                }
                new AlertDialog.Builder(WifiActivity.this)
                        .setCancelable(false)
                        .setMessage(String.format(getString(R.string.wifi_not_found), getWifiSSID()))
                        .setPositiveButton(getString(R.string.exit_app), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                unregisterReceiver(ScanReceiver.this);
                                finish();
                            }
                        })
                        .show();
            } else {
                // configure based on security
                final WifiConfiguration conf = new WifiConfiguration();
                conf.SSID = "\"" + getWifiSSID() + "\"";
                switch (security) {
                    case WEP:
                        conf.wepKeys[0] = "\"" + getWifiPass() + "\"";
                        conf.wepTxKeyIndex = 0;
                        conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                        conf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
                        break;
                    case PSK:
                        conf.preSharedKey = "\"" + getWifiPass() + "\"";
                        break;
                    case OPEN:
                        conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                        break;

                }
                connectionReceiver = new ConnectionReceiver();
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
                intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
                intentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
                registerReceiver(connectionReceiver, intentFilter);

                int networkId = wifi.getConnectionInfo().getNetworkId();

                wifi.removeNetwork(networkId);
                int netId = wifi.addNetwork(conf);
                wifi.enableNetwork(netId, true);
                wifi.saveConfiguration();
                wifi.reconnect();
                unregisterReceiver(this);

                 /* int networkId = wifi.getConnectionInfo().getNetworkId();
                 wifi.removeNetwork(networkId);
                 wifi.saveConfiguration();
                 //remember id
                 int netId = wifi.addNetwork(conf);
                 wifi.disconnect();
                 wifi.enableNetwork(netId, true);
                 wifi.reconnect();
                 unregisterReceiver(this);*/


            }
        }
    }

    /**
     * Timeout task. Called when timeout is reached
     */
    private class TimeoutTask implements Runnable {
        @Override
        public void run() {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            WifiInfo wifiInfo = wifi.getConnectionInfo();
            if (networkInfo.isConnected() && wifiInfo.getSSID().replace("\"", "").equals(getWifiSSID())) {
                try {
                    unregisterReceiver(connectionReceiver);
                } catch (Exception ex) {
                    // ignore if receiver already unregistered
                }
                WifiActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (progressDialog != null) {
                            progressDialog.dismiss();
                        }
                    }
                });
            } else {
                try {
                    unregisterReceiver(connectionReceiver);
                } catch (Exception ex) {
                    // ignore if receiver already unregistered
                }
                WifiActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (progressDialog != null) {
                            progressDialog.dismiss();
                        }
                        new AlertDialog.Builder(WifiActivity.this)
                                .setCancelable(false)
                                .setMessage(String.format(getString(R.string.wifi_not_connected), getWifiSSID()))
                                .setPositiveButton(getString(R.string.exit_app), new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        finish();
                                    }
                                })
                                .show();
                    }

                });
            }
        }
    }


}
