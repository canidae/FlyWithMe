package net.exent.flywithme.task;

import net.exent.flywithme.FlyWithMe;
import net.exent.flywithme.ProgressDialog;
import net.exent.flywithme.R;
import net.exent.flywithme.data.Airspace;
import net.exent.flywithme.data.Database;
import net.exent.flywithme.data.Flightlog;
import android.content.Context;
import android.os.AsyncTask;

public class InitDataTask extends AsyncTask<Context, String, Void> {
    @Override
    protected Void doInBackground(Context... contexts) {
        publishProgress("0", FlyWithMe.getInstance().getString(R.string.init_database));
    	Database.init(contexts[0]);
        publishProgress("25", FlyWithMe.getInstance().getString(R.string.loading_takeoffs));
        Flightlog.init(contexts[0]);
        publishProgress("50", FlyWithMe.getInstance().getString(R.string.loading_airspace));
        Airspace.init(contexts[0]);
        publishProgress("75", FlyWithMe.getInstance().getString(R.string.sorting_takeoffs));
        Flightlog.sortTakeoffListToLocation(Flightlog.getAllTakeoffs(), FlyWithMe.getInstance().getLocation());
        return null;
    }

    @Override
    protected void onProgressUpdate(String... messages) {
        showProgress(Integer.parseInt(messages[0]), messages[1]);
    }
    
    @Override
    protected void onPostExecute(Void nothing) {
    	FlyWithMe.getInstance().showTakeoffList();
        showProgress(-1, null); // dismiss dialog
    }

    /**
     * Show ProgressDialog fragment.
     * @param progress Progress slider, value from 0 to 100.
     * @param text Progress text.
     */
    private void showProgress(int progress, String text) {
        ProgressDialog progressDialog = ProgressDialog.getInstance();
        if (progress >= 0) {
            if (progressDialog == null) {
                progressDialog = new ProgressDialog();
                progressDialog.show(FlyWithMe.getInstance().getSupportFragmentManager(), "progressDialog");
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