package org.bsc.cordova;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.webkit.ValueCallback;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class echoes a string called from JavaScript.
 */
public class CDVBroadcaster extends CordovaPlugin {

    public static final String USERDATA = "userdata";
    private static String TAG =  CDVBroadcaster.class.getSimpleName();

    public static final String EVENTNAME_ERROR = "event name null or empty.";

    java.util.Map<String,BroadcastReceiver> receiverMap =
                    new java.util.HashMap<String,BroadcastReceiver>(10);

    /**
     *
     * @param eventName
     * @param jsonUserData
     * @throws JSONException
     */
    protected void fireEvent( final String eventName, final Object jsonUserData) throws JSONException {

        String method ;
        if( jsonUserData != null ) {

            if (!(jsonUserData instanceof JSONObject)) {
                final JSONObject json = new JSONObject(String.valueOf(jsonUserData)); // CHECK IF VALID
            }

            method = String.format("window.broadcaster.fireEvent( '%s', %s );", eventName, String.valueOf(jsonUserData));
        }
        else {
            method = String.format("window.broadcaster.fireEvent( '%s', {} );", eventName);
        }
        this.webView.sendJavascript(method);
        /*
        this.webView.evaluateJavascript(method, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                Log.d(TAG, "fireEvent executed!");
            }
        });
        */
    }

    protected void registerReceiver(android.content.BroadcastReceiver receiver, android.content.IntentFilter filter) {
        LocalBroadcastManager.getInstance(super.webView.getContext()).registerReceiver(receiver,filter);
    }

    protected void unregisterReceiver(android.content.BroadcastReceiver receiver) {
        LocalBroadcastManager.getInstance(super.webView.getContext()).unregisterReceiver(receiver);
    }

    protected boolean sendBroadcast(android.content.Intent intent) {
        return LocalBroadcastManager.getInstance(super.webView.getContext()).sendBroadcast(intent);
    }

    @Override
    public Object onMessage(String id, Object data) {

        try {
            fireEvent( id, data );
        } catch (JSONException e) {
            Log.e(TAG, "'userdata' is not a valid json object!");
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    private void fireNativeEvent( final String eventName, JSONObject userData ) {
        if( eventName == null ) {
            throw new IllegalArgumentException("eventName parameter is null!");
        }

        final Intent intent = new Intent(eventName);

        if( userData != null ) {
            Bundle b = new Bundle();
            b.putString(USERDATA, userData.toString());
            intent.putExtras(b);
        }

        sendBroadcast( intent );
    }

    /**
     *
     * @param action          The action to execute.
     * @param args            The exec() arguments.
     * @param callbackContext The callback context used when calling back into JavaScript.
     * @return
     * @throws JSONException
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if( action.equals("fireNativeEvent")) {

            final String eventName = args.getString(0);
            if( eventName==null || eventName.isEmpty() ) {
                callbackContext.error(EVENTNAME_ERROR);

            }
            final JSONObject userData = args.getJSONObject(1);

            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    fireNativeEvent(eventName, userData);
                }
            });

            return true;
        }
        else {
            if (action.equals("addEventListener")) {

                final String eventName = args.getString(0);
                if (eventName == null || eventName.isEmpty()) {
                    callbackContext.error(EVENTNAME_ERROR);
                    return false;
                }
                if (!receiverMap.containsKey(eventName)) {

                    final BroadcastReceiver r = new BroadcastReceiver() {

                        @Override
                        public void onReceive(Context context, final Intent intent) {

                            final Bundle b = intent.getExtras();

                            // parse the JSON passed as a string.
                            try {

                                final String userData = b.getString(USERDATA, "{}");

                                fireEvent( eventName, userData);

                            } catch (JSONException e) {
                                Log.e(TAG, "'userdata' is not a valid json object!");
                            }

                        }
                    };

                    registerReceiver(r, new IntentFilter(eventName));

                    receiverMap.put(eventName, r);
                }
                callbackContext.success();

                return true;
            } else if (action.equals("removeEventListener")) {

                final String eventName = args.getString(0);
                if (eventName == null || eventName.isEmpty()) {
                    callbackContext.error(EVENTNAME_ERROR);
                    return false;
                }

                BroadcastReceiver r = receiverMap.remove(eventName);

                if (r != null) {

                    unregisterReceiver(r);


                }
                callbackContext.success();
                return true;
            }
        }
        return false;
    }

    /**
     *
     */
    @Override
    public void onDestroy() {
        // deregister receiver
        for( BroadcastReceiver r : receiverMap.values() ) {
                    unregisterReceiver(r);
        }

        receiverMap.clear();

        super.onDestroy();

    }

}
