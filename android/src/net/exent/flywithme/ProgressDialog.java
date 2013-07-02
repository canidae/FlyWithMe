package net.exent.flywithme;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Service;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ProgressDialog extends DialogFragment {
    private static ProgressDialog instance;
    private static AsyncTask<?, ?, ?> task;
    private static int progress;
    private static String text;
    private static Bitmap image;
    private static Runnable runnable;
    private View view;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
    	Log.i(getClass().getName(), "onCreateDialog(" + savedInstanceState + ")");
        LayoutInflater inflater = getActivity().getLayoutInflater();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        view = inflater.inflate(R.layout.progress_dialog, null);
        builder.setView(view);
        if (task != null) {
            builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    if (task != null && !task.isCancelled())
                        task.cancel(true);
                }
            }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    if (task != null && !task.isCancelled())
                        task.cancel(true);
                }
            });
        }
        setCancelable(false);
        //setRetainInstance(true); // XXX: not retaining instance due to bug: http://code.google.com/p/android/issues/detail?id=17423
        instance = this;
        return builder.create();
    }

    @Override
    public void onStart() {
    	Log.i(getClass().getName(), "onStart()");
        super.onStart();
        showProgress();
    }
    
    @Override
    public void onDetach() {
        instance = null;
        super.onDetach();
    }
    
    public static ProgressDialog getInstance() {
        return instance;
    }
    
    public String getInputText() {
        EditText progressInput = (EditText) view.findViewById(R.id.progressInput);
        return progressInput.getText().toString();
    }
    
    public void setTask(AsyncTask<?, ?, ?> task) {
    	ProgressDialog.task = task;
    }

    public void setProgress(int progress, String text, Bitmap image, final Runnable runnable) {
    	ProgressDialog.progress = progress;
    	ProgressDialog.text = text;
    	ProgressDialog.image = image;
    	ProgressDialog.runnable = runnable;
        showProgress();
    }
    
    private void showProgress() {
        try {
            Log.i(getClass().getName(), "showProgress(): " + isVisible() + " | " + progress + ", " + text + ", " + image + ", " + runnable);
            /* apparently the method isVisible() does not tell whether the fragment actually is visible, but rather always return false even when the fragment is visible (at least in this case).
             * i don't know, don't ask me, ask the android people, it's their crack
            if (!isVisible())
                return;
                */
            ProgressBar progressBar = (ProgressBar) view.findViewById(R.id.progressBar);
            if (progressBar != null)
                progressBar.setProgress(progress > progressBar.getMax() ? progressBar.getMax() : progress);
    
            TextView progressText = (TextView) view.findViewById(R.id.progressText);
            if (progressText != null)
                progressText.setText(text);
    
            ImageView progressImage = (ImageView) view.findViewById(R.id.progressImage);
            if (progressImage != null) {
                progressImage.setImageBitmap(image);
                progressImage.setVisibility(image == null ? View.GONE : View.VISIBLE);
            }
    
            final EditText progressInput = (EditText) view.findViewById(R.id.progressInput);
            if (progressInput != null) {
                progressInput.setInputType(0x00001001); // set input to upper case
                progressInput.setVisibility(runnable == null ? View.GONE : View.VISIBLE);
                progressInput.getEditableText().clear();
                progressInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        runnable.run();
                        progressInput.setVisibility(View.GONE);
                        ImageView progressImage = (ImageView) view.findViewById(R.id.progressImage);
                        progressImage.setVisibility(View.GONE);
                        return true;
                    }
                });
                progressInput.requestFocus();
                ((InputMethodManager) getActivity().getSystemService(Service.INPUT_METHOD_SERVICE)).showSoftInput(progressInput, 0);
            }
        } catch (Exception e) {
            /* presumably the view is not visible yet */
            Log.w(getClass().getName(), "Unable to show progress dialog", e);
        }
    }
}
