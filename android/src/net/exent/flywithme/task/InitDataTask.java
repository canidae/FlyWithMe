package net.exent.flywithme.task;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.ProgressDialog;
import net.exent.flywithme.R;
import net.exent.flywithme.data.Airspace;
import net.exent.flywithme.data.Flightlog;
import android.content.Context;
import android.os.AsyncTask;

public class InitDataTask extends AsyncTask<Context, String, Void> {
    private FlyWithMe flywithme;

    public InitDataTask(FlyWithMe flywithme) {
        this.flywithme = flywithme;
    }

    @Override
    protected Void doInBackground(Context... contexts) {
        publishProgress("" + (int) (Math.random() * 33), flywithme.getString(R.string.loading_takeoffs));
        Flightlog.init(contexts[0]);
        publishProgress("" + (int) (Math.random() * 34 + 33), flywithme.getString(R.string.loading_airspace));
        Airspace.init(contexts[0]);
        publishProgress("" + (int) (Math.random() * 33 + 67), flywithme.getString(R.string.sorting_takeoffs));
        Flightlog.sortTakeoffListToLocation(Flightlog.getAllTakeoffs(), flywithme.getLocation());
        return null;
    }

    @Override
    protected void onProgressUpdate(String... messages) {
        showProgress(Integer.parseInt(messages[0]), messages[1]);
    }

    @Override
    protected void onPostExecute(Void nothing) {
        flywithme.showTakeoffList();
        showProgress(-1, null); // dismiss dialog
    }

    private void showProgress(int progress, String text) {
        ProgressDialog progressDialog = ProgressDialog.getInstance();
        if (progress >= 0) {
            if (progressDialog == null) {
                progressDialog = new ProgressDialog();
                progressDialog.show(flywithme.getSupportFragmentManager(), "progressDialog");
            }
            /* pass arguments */
            progressDialog.setProgress(progress, text, null, null);
            /* show fragment */
        } else if (progressDialog != null) {
            /* hide fragment */
            progressDialog.dismiss();
        }
    }
}
