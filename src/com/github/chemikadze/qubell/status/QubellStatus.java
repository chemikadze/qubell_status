package com.github.chemikadze.qubell.status;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

class FailedUpdateException extends Exception {
    public FailedUpdateException(String msg) { super(msg); }
    public FailedUpdateException(String msg, Throwable cause) { super(msg, cause); }
}

public class QubellStatus extends Activity {

    public static final int WARNING_AVAILABILITY = 99;
    public static final int FAILED_AVAILABILITY = 90;

    private static final String STATUS_ENDPOINT = "http://api.io.watchmouse.com/synth/current/62120/folder/28340/";
    private Handler handler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onStart();
        refresh();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.refresh) {
            refresh();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void refresh() {
        TextView label = (TextView)findViewById(R.id.availability_label);
        label.setText(R.string.updating_msg);
        label.setTextColor(getResources().getColor(R.color.updatingColor));
        if (handler == null) {
            handler = new Handler();
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final int a = getAvailability();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            showAvailability(a);
                        }
                    });
                } catch (FailedUpdateException e) {
                    final Exception error = e;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            showFailure(error.getMessage());
                            showAvailability(-1);
                        }
                    });
                }
            }
        });
        t.start();
    }

    private String fromStream(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder out = new StringBuilder();
        String newLine = System.getProperty("line.separator");
        String line;
        while ((line = reader.readLine()) != null) {
            out.append(line);
            out.append(newLine);
        }
        return out.toString();
    }

    private void showFailure(String msg) {
        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void showAvailability(int availability) {
        TextView label = (TextView)findViewById(R.id.availability_label);
        if (availability >= 0) {
            label.setText(Integer.toString(availability) + "%");
            if (availability > WARNING_AVAILABILITY) {
                label.setTextColor(getResources().getColor(R.color.okColor));
            } else if (availability > FAILED_AVAILABILITY && availability <= WARNING_AVAILABILITY) {
                label.setTextColor(getResources().getColor(R.color.waringColor));
            } else {
                label.setTextColor(getResources().getColor(R.color.errorColor));
            }
        } else {
            label.setText(R.string.failed_update_msg);
            label.setTextColor(getResources().getColor(R.color.failedColor));
        }
    }

    private int getAvailability() throws FailedUpdateException {
        InputStream in;
        try {
            in = new URL(STATUS_ENDPOINT).openStream();
        } catch (IOException e) {
            throw new FailedUpdateException("Failed to download status info: " + e.getMessage(), e);
        }

        try {
            try {
                String jsonString = fromStream(in);
                JSONObject statusInfo = new JSONObject(jsonString);
                JSONArray array = statusInfo.getJSONArray("result");
                for (int i = 0; i < array.length(); ++i) {
                    JSONObject item = array.getJSONObject(i);
                    String name = item.getJSONObject("info").getString("name");
                    if (name.equals("Express agent")) {
                        return item.getJSONObject("cur").getInt("uptime");
                    }
                }
                throw new FailedUpdateException("Can not find status for \"Express agent\"");
            } finally {
                in.close();
            }
        } catch (JSONException e) {
            throw new FailedUpdateException("Failed to parse status info: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new FailedUpdateException("Failed to download status info: " + e.getMessage(), e);
        }
    }
}
