package sh.ftp.rocketninelabs.meditationassistant;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Created by root on 11/2/13.
 */
public class MediNET {
    public static Integer version = 5;
    public String status = "disconnected";
    public String result = "";
    public MainActivity activity;
    public MeditationSession session = new MeditationSession();
    public String provider = "";
    public ArrayList<MeditationSession> result_sessions = null;
    public String announcement = "";
    private Boolean debug = false;
    private MeditationAssistant ma = null;
    private MediNETTask task = null;
    private Handler handler = new Handler();
    private Runnable runnable;
    private Runnable runnable2;
    private Boolean runnable_finished = true;
    private AlertDialog alertDialog = null;
    private String browsetopage = "";

    public MediNET(MainActivity _activity) {
        activity = _activity;
        runnable = new Runnable() {
            @Override
            public void run() {
                handler.removeCallbacks(this);
                getMeditationAssistant().updateWidgets();
                runnable_finished = true;
            }
        };
        runnable2 = new Runnable() {
            @Override
            public void run() {
                handler.removeCallbacks(this);
                Log.d("MeditationAssistant", "Delayed update() running...");
                getMeditationAssistant().getMediNET().updated();
            }
        };
    }

    public static String durationToTimerString(long duration, boolean countDown) {
        int hours = (int) duration / 3600;
        int minutes = ((int) duration % 3600) / 60;
        if (countDown) {
            minutes += 1;
        }

        return String.valueOf(hours) + ":" + String.format("%02d", minutes);
    }

    public void askToSignIn() {
        /*if (activity == null || activity.stopped) {
            Log.d("MeditationAssistant",
                    "MainActivity null or stopped, restarting...  Stopped: "
                            + activity.stopped.toString());*/
        if (activity == null) {
            Intent openActivity = new Intent(getMeditationAssistant()
                    .getApplicationContext(), MainActivity.class);
            openActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            openActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            handler.postDelayed(new Runnable() {

                @Override
                public void run() {
                    Log.d("MeditationAssistant",
                            "Open MainActivity runnable is now running...");
                    askToSignIn();
                }
            }, 400);

            getMeditationAssistant().getApplicationContext().startActivity(
                    openActivity);
            return;
        }

        activity.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (activity == null) {
                    Log.d("MeditationAssistant",
                            "askToSignIn activity is null, returning...");
                    return;
                }
                getMeditationAssistant().showSignInDialog(activity);
            }
        });
    }

    public void browseTo(MainActivity act, String page) {
        activity = act;
        browsetopage = page;

        if (status.equals("success") || page.equals("community")) {
            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent openActivity = new Intent(activity
                            .getApplicationContext(), MediNETActivity.class);
                    openActivity.putExtra("page", browsetopage);
                    openActivity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    openActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    getMeditationAssistant().getApplicationContext()
                            .startActivity(openActivity);
                }
            });
        } else {
            askToSignIn();
        }
    }

    public void resetSession() {
        session = new MeditationSession();
    }

    public boolean connect() {
        ConnectivityManager cm = (ConnectivityManager) getMeditationAssistant()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        Boolean internetconnected = (netInfo != null && netInfo.isConnectedOrConnecting());

        if (!internetconnected) {
            Log.d("MeditationAssistant",
                    "Cancelled MediNET connection:  Internet isn't connected");
            return false;
        }

        if (getMeditationAssistant().getMediNETKey().equals("")) {
            askToSignIn();
            return false;
        }

        runnable_finished = true;
        if (!this.status.equals("success")) {
            JSONObject jobj = new JSONObject();
            try {
                Log.d("MeditationAssistant", "Begin connect");
                jobj.put("x", getMeditationAssistant().getMediNETKey());
            } catch (JSONException e1) {
                e1.printStackTrace();
            }

            // display json object being sent
            // Log.d("MeditationAssistant", "JSON object send: " +
            // jobj.toString());

            status = "connecting";

            updated();
            if (task != null) {
                task.cancel(true);
            }
            task = new MediNETTask();
            task.action = "connect";
            task.context = activity.getApplicationContext();

            Log.d("MeditationAssistant", "Executing MediNET Task");
            task.doIt(this);
            return true;
        }
        updated();
        return false;
    }

    public Boolean deleteSessionByStarted(long started) {
        if (task != null) {
            task.cancel(true);
        }
        task = new MediNETTask();
        task.action = "deletesession";
        task.actionextra = String.valueOf(started);
        task.context = activity.getApplicationContext();
        if (debug) {
            task.nextURL += "&debug77";
        }
        task.doIt(this);
        return true;
    }

    public void signInWithAuthToken(String authtoken) {
        if (task != null) {
            task.cancel(true);
        }

        task = new MediNETTask();
        task.action = "signin";
        task.actionextra = authtoken;
        task.context = activity.getApplicationContext();

        task.doIt(this);
    }

    public MainActivity getActivity() {
        return activity;
    }

    public void setActivity(MainActivity activity) {
        this.activity = activity;
    }

    public MeditationAssistant getMeditationAssistant() {
        if (ma == null) {
            ma = (MeditationAssistant) this.activity.getApplication();
        }
        return ma;
    }

    public MeditationSession getSession() {
        return session;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean postSession() {
        return postSession(false, null);
    }

    public Boolean postSession(Boolean manualposting, FragmentActivity act) {
        Log.d("MeditationAssistant", "Session.toString(): "
                + this.getSession().export().toString());
        if (task != null) {
            task.cancel(true);
        }
        task = new MediNETTask();
        task.fragment_activity = act;
        task.action = "postsession";
        if (manualposting) {
            task.actionextra = "manualposting";
        } else {
            // Only add streak if there isn't already a session for today
            if (getMeditationAssistant().db.numSessionsByDate(getMeditationAssistant().db.timestampToAPIDate(getSession().completed * 1000)) == 0) {
                getMeditationAssistant().addMeditationStreak();
                if (getSession().streakday == 0) {
                    getSession().streakday = getMeditationAssistant().getMeditationStreak();
                }
            }
        }
        task.context = activity.getApplicationContext();
        if (debug) {
            task.nextURL += "&debug77";
        }
        task.doIt(this);
        return true;
    }

    public void saveSession(Boolean manualposting, Boolean posted) {
        Long postedlong = (long) 0;
        if (posted) {
            postedlong = (long) 1;
        }

        // Only add streak if there isn't already a session for that day
        if (getMeditationAssistant().db.numSessionsByDate(getMeditationAssistant().db.timestampToAPIDate(getSession().completed * 1000)) == 0) {
            if (!manualposting) {
                getMeditationAssistant().addMeditationStreak();

                if (getSession().streakday == 0) {
                    getSession().streakday = getMeditationAssistant().getMeditationStreak();
                }
            } else {
                Calendar c_midnight = new GregorianCalendar();
                c_midnight.setTime(new Date());
                c_midnight.set(Calendar.HOUR_OF_DAY, 0);
                c_midnight.set(Calendar.MINUTE, 0);
                c_midnight.set(Calendar.SECOND, 0);
                c_midnight.set(Calendar.MILLISECOND, 0);

                if ((c_midnight.getTimeInMillis() / 1000) < getSession().started && (getSession().started - (c_midnight.getTimeInMillis() / 1000)) < 86400) { // After midnight today
                    getMeditationAssistant().addMeditationStreak();

                    if (getSession().streakday == 0) {
                        getSession().streakday = getMeditationAssistant().getMeditationStreak();
                    }
                } else {
                    c_midnight.add(Calendar.DATE, -1);

                    if ((c_midnight.getTimeInMillis() / 1000) < getSession().started) { // After midnight yesterday
                        getMeditationAssistant().addMeditationStreak(false);

                        if (getSession().streakday == 0) {
                            getSession().streakday = getMeditationAssistant().getMeditationStreak();
                        }
                    }
                }
            }
        }

        getMeditationAssistant().db.addSession(new SessionSQL(
                getSession().started, getSession().completed, getSession().length,
                getSession().message, postedlong, getSession().streakday));

        resetSession();

        if (!manualposting) {
            getMeditationAssistant().asktorate = true;
        }
    }

    public void selectProvider(View v) {
        ImageButton img = (ImageButton) v;

        Intent intent = new Intent(getMeditationAssistant()
                .getApplicationContext(), MediNETActivity.class);
        if (img.getId() == R.id.btnGoogle) {
            intent.putExtra("provider", "Google");
        } else if (img.getId() == R.id.btnFacebook) {
            intent.putExtra("provider", "Facebook");
        } else if (img.getId() == R.id.btnAOL) {
            intent.putExtra("provider", "AOL");
        } else if (img.getId() == R.id.btnTwitter) {
            intent.putExtra("provider", "Twitter");
        } else if (img.getId() == R.id.btnLive) {
            intent.putExtra("provider", "Live");
        } else if (img.getId() == R.id.btnOpenID) {
            intent.putExtra("provider", "OpenID");
        }

        getMeditationAssistant().getApplicationContext().startActivity(intent);
    }

    public void signOut() {
        Log.d("MeditationAssistant", "Signing out");
        if (task != null) {
            task.cancel(true);
        }
        task = new MediNETTask();
        task.action = "signout";
        task.context = activity.getApplicationContext();
        if (debug) {
            task.nextURL += "&debug77";
        }
        task.doIt(this);

        status = "stopped";
        getMeditationAssistant().setMediNETKey("", "");
        if (!getMeditationAssistant().getPrefs().getBoolean("pref_autosignin", false)) {
            getMeditationAssistant().getPrefs().edit().putString("key", "").apply();
        }
        //getMeditationAssistant().setMeditationStreak(0, 0);
        updated();
    }

    public Boolean syncSessions() {
        getMeditationAssistant().shortToast(getMeditationAssistant().getString(R.string.downloadingSessions));
        if (task != null) {
            task.cancel(true);
        }
        task = new MediNETTask();
        task.action = "syncsessions";
        task.context = activity.getApplicationContext();
        if (debug) {
            task.nextURL += "&debug77";
        }
        task.doIt(this);
        return true;
    }

    public Boolean uploadSessions() {
        getMeditationAssistant().shortToast(getMeditationAssistant().getString(R.string.uploadingSessions));
        if (task != null) {
            task.cancel(true);
        }
        task = new MediNETTask();
        task.action = "uploadsessions";
        task.context = activity.getApplicationContext();
        if (debug) {
            task.nextURL += "&debug77";
        }
        task.doIt(this);
        return true;
    }

    public void updateAfterDelay() {
        Log.d("MeditationAssistant", "Update after delay: " + status);
        handler.postDelayed(runnable2, 1750);
    }

    public void updated() {
        Log.d("MeditationAssistant", "updated() " + status);

        activity.updateTextsAsync();
        if (runnable_finished) {
            runnable_finished = false;
            handler.postDelayed(runnable, 750);
        }
    }
}
