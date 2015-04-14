package org.orbisgis.protonomap;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.graphics.Color;
import android.content.Intent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.Legend.LegendPosition;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.XAxis.XAxisPosition;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.utils.ValueFormatter;

import java.text.DecimalFormat;
import java.util.ArrayList;

public class Measurement extends MainActivity {

    public ImageButton button;
    static float Leqi;

    // For the Charts
    protected HorizontalBarChart mChart; // VUMETER representation
    protected BarChart sChart; // Spectrum representation

    // Other ressources
    private String[] ltob;  // List of third-octave bands (defined as ressources)


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement);
        initDrawer();

        // Check if app starts for the first time and (if true)
        // display a dialog box for caution
        if (CheckNbRun()) {
            // show dialog
            new AlertDialog.Builder(this).setTitle(R.string.title_caution).
                    setMessage(R.string.text_caution).
                    setNeutralButton(R.string.text_OK, null).show();
        }

        // To start a record (test mode)
        button=(ImageButton)findViewById(R.id.recordBtn);
        button.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub

                //int i;
                //for (i=1; i<10; i++) {


                    // Vumeter data
                    setData(135);
                    // Change the text and the textcolor in the corresponding textview
                    // for the Leqi value
                    final TextView mTextView = (TextView) findViewById(R.id.textView_value_SL_i);
                    mTextView.setText(String.format("%.1f", Leqi));
                    int nc=getNEcatColors(Leqi);    // Choose the color category in function of the sound level
                    mTextView.setTextColor(NE_COLORS[nc]);

                    // Spectrum data
                    setDataS(30, 135);
                /*
                try {
                        Thread.sleep(1000);                 //1000 milliseconds is one second.
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    */
                    Intent i = new Intent(getApplicationContext(),Results.class);
                    startActivity(i);
                //}
            }
        });

        // Instantaneous sound level VUMETER
        mChart = (HorizontalBarChart) findViewById(R.id.vumeter);
        mChart.setDrawBarShadow(false);
        mChart.setDescription("");
        mChart.setPinchZoom(false);
        mChart.setDrawGridBackground(false);
        mChart.setMaxVisibleValueCount(0);
        mChart.setScaleXEnabled(false); // Disable scaling on the X-axis
        // XAxis parameters: hide all
        XAxis xlv = mChart.getXAxis();
        xlv.setPosition(XAxisPosition.BOTTOM);
        xlv.setDrawAxisLine(false);
        xlv.setDrawGridLines(false);
        xlv.setDrawLabels(false);
        // YAxis parameters (left): main axis for dB values representation
        YAxis ylv = mChart.getAxisLeft();
        ylv.setDrawAxisLine(false);
        ylv.setDrawGridLines(true);
        ylv.setAxisMaxValue(141f);
        ylv.setStartAtZero(true);
        ylv.setTextColor(Color.WHITE);
        ylv.setGridColor(Color.WHITE);
        ylv.setValueFormatter(new dBValueFormatter());
        setData(0);
        // YAxis parameters (right): no axis, hide all
        YAxis yrv = mChart.getAxisRight();
        yrv.setEnabled(false);
        // Legend: hide all
        Legend lv = mChart.getLegend();
        lv.setEnabled(false); // Hide legend

        // Instantaneous spectrum
        // Stacked bars are used for represented Min, Current and Max values
        sChart = (BarChart) findViewById(R.id.spectrumChart);
        sChart.setDrawBarShadow(false);
        sChart.setDescription("");
        sChart.setPinchZoom(false);
        sChart.setDrawGridBackground(false);
        sChart.setMaxVisibleValueCount(0);
        sChart.setDrawValuesForWholeStack(true); // Stacked
        // XAxis parameters:
        XAxis xls = sChart.getXAxis();
        xls.setPosition(XAxisPosition.BOTTOM);
        xls.setDrawAxisLine(true);
        xls.setDrawGridLines(false);
        xls.setDrawLabels(true);
        xls.setTextColor(Color.WHITE);
        // YAxis parameters (left): main axis for dB values representation
        YAxis yls = sChart.getAxisLeft();
        yls.setDrawAxisLine(true);
        yls.setDrawGridLines(true);
        yls.setAxisMaxValue(141.f);
        yls.setStartAtZero(true);
        yls.setTextColor(Color.WHITE);
        yls.setGridColor(Color.WHITE);
        setDataS(30, 0);  // 30 values for each third-octave band
        // YAxis parameters (right): no axis, hide all
        YAxis yrs = sChart.getAxisRight();
        yrs.setEnabled(false);
        // Legend: hide all
        Legend ls = sChart.getLegend();
        ls.setEnabled(false); // Hide legend

    }

    /***
     * Checks that application runs first time and write flags at SharedPreferences
     * Need further codes for enhancing conditions
     * @return true if 1st time
     * see : http://stackoverflow.com/questions/9806791/showing-a-message-dialog-only-once-when-application-is-launched-for-the-first
     * see also for checking version (later) : http://stackoverflow.com/questions/7562786/android-first-run-popup-dialog
     * Can be used for checking new version
     */
    private boolean CheckNbRun() {
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        //boolean AlreadyRanBefore = preferences.getBoolean("AlreadyRanBefore", false);
        SharedPreferences.Editor editor = preferences.edit();
        Integer NbRun = preferences.getInt("NbRun", 1);
        if (NbRun > 3) {
            NbRun=1;
            editor.putInt("NbRun", NbRun+1);
            editor.apply();
        }
        else
        {
            editor.putInt("NbRun", NbRun+1);
            editor.apply();
            //AlreadyRanBefore = preferences.getBoolean("AlreadyRanBefore", false);
        }
        return (NbRun==1);
    }

    // Fix the format of the dB Axis of the vumeter
    public class dBValueFormatter implements ValueFormatter {

        private DecimalFormat mFormat;

        public dBValueFormatter() {
            mFormat = new DecimalFormat("###,###,##0"); // use one decimal
        }

        @Override
        public String getFormattedValue(float value) {
            return mFormat.format(value);
        }
    }

    // Generate artificial 1 data (sound level) for vumeter representation
    private void setData(float range) {

        ArrayList<String> xVals = new ArrayList<String>();
        xVals.add("");

        ArrayList<BarEntry> yVals1 = new ArrayList<BarEntry>();
        float mult = (range + 1f);
        float val = (float) (Math.random() * mult);
        Leqi=val;
        yVals1.add(new BarEntry(val, 0));

        BarDataSet set1 = new BarDataSet(yVals1, "DataSet");
        //set1.setBarSpacePercent(35f);
        //set1.setColor(Color.rgb(0, 153, 204));
        int nc=getNEcatColors(Leqi);    // Choose the color category in function of the sound level
        set1.setColor(NE_COLORS[nc]);

        ArrayList<BarDataSet> dataSets = new ArrayList<BarDataSet>();
        dataSets.add(set1);

        BarData data = new BarData(xVals, dataSets);
        data.setValueTextSize(10f);

        mChart.setData(data);
        mChart.invalidate(); // refresh
    }

    // Generate artificial data (sound level for each 1/3 octave band) for spectrum representation
    private void setDataS(int count, float range) {

        ArrayList<String> xVals = new ArrayList<String>();
        ltob= getResources().getStringArray(R.array.tob_list_array);
        for (int i = 0; i < count; i++) {
            xVals.add(ltob[i % 30]);
        }

        ArrayList<BarEntry> yVals1 = new ArrayList<BarEntry>();

        // Value for each third-octave band
        for (int i = 0; i < count; i++) {
            float mult = (range + 1f);
            float val = (float) (20f+Math.random() * mult);
            //yVals1.add(new BarEntry(val, i));
            yVals1.add(new BarEntry(new float[] {40f,30f,val-30f}, i));
        }

        BarDataSet set1 = new BarDataSet(yVals1, "DataSet");
        set1.setColors(getColors());
        set1.setStackLabels(new String[] {
                "Min", "SL", "Max"
        });

        ArrayList<BarDataSet> dataSets = new ArrayList<BarDataSet>();
        dataSets.add(set1);

        BarData data = new BarData(xVals, dataSets);
        data.setValueTextSize(10f);

        sChart.setData(data);
        sChart.invalidate(); // refresh
    }

    // Color for spectrum representation (min, iSL, max)
    public static final int[] SPECTRUM_COLORS = {
            Color.rgb(0, 128, 255), Color.rgb(102, 178, 255), Color.rgb(204, 229, 255),
    };

}
