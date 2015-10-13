/*
 * Copyright (C) 2015 Ferid Cafer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ferid.app.classroom.statistics;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.ferid.app.classroom.R;
import com.ferid.app.classroom.adapters.StatisticsAdapter;
import com.ferid.app.classroom.database.DatabaseManager;
import com.ferid.app.classroom.model.Attendance;
import com.ferid.app.classroom.model.Classroom;
import com.ferid.app.classroom.utility.DirectoryUtility;
import com.jjoe64.graphview.DefaultLabelFormatter;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Created by ferid.cafer on 4/20/2015.<br />
 * Shows the student attendance rate and graph.
 */
public class StudentsListActivity extends AppCompatActivity {

    private Context context;

    private ListView list;
    private ArrayList<Attendance> attendanceList;
    private StatisticsAdapter adapter;

    private Classroom classroom;

    //graphics
    private GraphView graph;
    private LinearLayout graphLayout;
    private Attendance attendance;
    private ArrayList<Attendance> graphList;
    //close graph icon
    private ImageView closeGraphIcon;
    //share graph icon
    private ImageView shareGraphIcon;
    //graph file name (out of screen shot)
    private final String FILE_NAME = "graph.png";



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.statistics);

        Bundle args = getIntent().getExtras();
        if (args != null) {
            classroom = (Classroom) args.getSerializable("classroom");
        }

        context = this;

        //toolbar
        setToolbar();

        //graph
        graphLayout = (LinearLayout) findViewById(R.id.graphLayout);
        graph = (GraphView) findViewById(R.id.graph);
        closeGraphIcon = (ImageView) findViewById(R.id.closeGraphIcon);
        shareGraphIcon = (ImageView) findViewById(R.id.shareGraphIcon);
        graphList = new ArrayList<Attendance>();

        //list
        list = (ListView) findViewById(R.id.list);
        attendanceList = new ArrayList<Attendance>();
        adapter = new StatisticsAdapter(context, R.layout.hash_text_item, attendanceList);
        list.setAdapter(adapter);

        //empty list view text
        TextView emptyText = (TextView) findViewById(R.id.emptyText);
        list.setEmptyView(emptyText);

        setCloseGraphIconListener();
        setShareGraphIconListener();
        setListItemClickListener();

        new SelectAllAttendancesOfClass().execute();
    }

    /**
     * Create toolbar and set its attributes
     */
    private void setToolbar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        setTitle(classroom.getName());
    }

    /**
     * setOnItemClickListener
     */
    private void setListItemClickListener() {
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (attendanceList != null && attendanceList.size() > position) {
                    attendance = attendanceList.get(position);
                    graphList.clear();

                    new SelectAllAttendancesOfStudent().execute();
                }
            }
        });
    }

    /**
     * setOnClickListener
     */
    private void setCloseGraphIconListener() {
        closeGraphIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideGraph();
            }
        });
    }

    private void setShareGraphIconListener() {
        shareGraphIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bitmap bitmap = takeScreenShot();
                saveBitmap(bitmap);
                shareInMedia();
            }
        });
    }

    /**
     * Take screen shot of the graph
     * @return
     */
    private Bitmap takeScreenShot() {
        //invalidate the layout, otherwise it will give the older screenshot
        //if taken more than once
        graphLayout.invalidate();

        View rootView = graphLayout;
        rootView.setDrawingCacheEnabled(true);
        return rootView.getDrawingCache();
    }

    /**
     * Save bitmap image into disc
     * @param bitmap Bitmap
     */
    public void saveBitmap(Bitmap bitmap) {
        DirectoryUtility.createDirectory();

        File imagePath = new File(DirectoryUtility.getPathFolder() + FILE_NAME);
        FileOutputStream fos = null;

        try {
            fos = new FileOutputStream(imagePath);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.flush();
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Share screenshot of the graph through social media
     */
    private void shareInMedia() {
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("plain/text");
        share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
        share.putExtra(Intent.EXTRA_TEXT, getString(R.string.takeAttendance));
        share.setType("image/png");
        share.putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///"
                + DirectoryUtility.getPathFolder() + FILE_NAME));
        startActivity(Intent.createChooser(share, getString(R.string.shareText)));
    }

    /**
     * Select students with percentages from DB
     */
    private class SelectAllAttendancesOfClass extends AsyncTask<Void, Void, ArrayList<Attendance>> {

        @Override
        protected ArrayList<Attendance> doInBackground(Void... params) {
            ArrayList<Attendance> tmpList = null;
            if (classroom != null) {
                DatabaseManager databaseManager = new DatabaseManager(context);
                tmpList = databaseManager.selectAllAttendancesOfClass(classroom.getId());
            }

            return tmpList;
        }

        @Override
        protected void onPostExecute(ArrayList<Attendance> tmpList) {
            attendanceList.clear();

            if (tmpList != null) {
                attendanceList.addAll(tmpList);
                adapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * Select students with percentages from DB
     */
    private class SelectAllAttendancesOfStudent extends AsyncTask<Void, Void, ArrayList<Attendance>> {

        @Override
        protected ArrayList<Attendance> doInBackground(Void... params) {
            ArrayList<Attendance> tmpList = null;
            if (attendance != null) {
                DatabaseManager databaseManager = new DatabaseManager(context);
                tmpList = databaseManager.selectAllAttendancesOfStudent(attendance.getClassroomId(),
                        attendance.getStudentId());
            }

            return tmpList;
        }

        @Override
        protected void onPostExecute(ArrayList<Attendance> tmpList) {
            graphList.clear();

            if (tmpList != null) {
                graphList.addAll(tmpList);

                calculateAttendanceByWeek();
            }
        }
    }

    /**
     * Calculate presence percentage by week
     */
    private void calculateAttendanceByWeek() {
        ArrayList<Integer> presenceList = new ArrayList<Integer>();
        int numberOfWeeks = graphList.size();
        int numberOfPresence = 0;

        for (int i = 0; i < numberOfWeeks; i++) {
            Attendance tmpAttendance = graphList.get(i);

            if (tmpAttendance.getPresent() == 1) {
                numberOfPresence++;
            }

            int percentage = (int) ((double)numberOfPresence * 100 / (i+1));
            presenceList.add(percentage);
        }

        prepareGraphics(presenceList);
    }

    /**
     * Set graph style attributes
     * @param maxX
     */
    private void setGraphAttributes(int maxX) {
        graph.setTitle(attendance.getStudentName());
        graph.setTitleColor(getResources().getColor(R.color.primary_text));

        graph.getViewport().setMaxY(100);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMaxX(maxX);
        graph.getViewport().setXAxisBoundsManual(true);

        graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.BOTH);
        graph.getGridLabelRenderer().setGridColor(getResources().getColor(R.color.gray));
        graph.getGridLabelRenderer().setHorizontalLabelsColor(getResources().getColor(R.color.blackish));
        graph.getGridLabelRenderer().setVerticalLabelsColor(getResources().getColor(R.color.blackish));

        //number of x-axis label items
        int numHorizontalLabels;
        if (maxX <= 8) {
            numHorizontalLabels = maxX + 1;
        } else if (maxX <= 16) {
            numHorizontalLabels = maxX / 2 + 1;
        } else {
            numHorizontalLabels = maxX / 4 + 1;
        }
        graph.getGridLabelRenderer().setNumHorizontalLabels(numHorizontalLabels);

        graph.getGridLabelRenderer().reloadStyles();
    }

    /**
     * Draw graph of weekly attendance
     * @param presenceList
     */
    private void prepareGraphics(ArrayList<Integer> presenceList) {
        DataPoint[] dataPoints = new DataPoint[presenceList.size() + 1];
        dataPoints[0] = new DataPoint(0, 0);
        for (int i = 0; i < presenceList.size(); i++) {
            dataPoints[i + 1] = new DataPoint((i+1), presenceList.get(i));
        }

        LineGraphSeries<DataPoint> series = new LineGraphSeries<DataPoint>(dataPoints);
        series.setColor(getResources().getColor(R.color.colourAccent));
        series.setThickness(getResources().getInteger(R.integer.statistics_series_thickness));

        graph.removeAllSeries();
        graph.addSeries(series);

        setGraphAttributes(presenceList.size());

        graph.getGridLabelRenderer().setLabelFormatter(new DefaultLabelFormatter() {
            @Override
            public String formatLabel(double value, boolean isValueX) {
                int valueInt = ((int) value);

                if (isValueX && valueInt == 0) {
                    return "";
                } else {
                    return super.formatLabel(valueInt, isValueX);
                }
            }
        });

        showGraph();
    }

    /**
     * Show graph layout with its full content
     */
    private void showGraph() {
        if (graphLayout.getVisibility() != View.VISIBLE) {
            Animation animShow = AnimationUtils.loadAnimation(context, R.anim.push_from_bottom);
            graphLayout.startAnimation(animShow);
            graphLayout.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Hide graph layout
     */
    private void hideGraph() {
        if (graphLayout.getVisibility() == View.VISIBLE) {
            Animation animHide = AnimationUtils.loadAnimation(context,
                    R.anim.push_to_bottom);
            graphLayout.setAnimation(animHide);
            graphLayout.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        boolean isGraphVisible;
        if (graphLayout.getVisibility() == View.VISIBLE) {
            isGraphVisible = true;
        } else {
            isGraphVisible = false;
        }

        outState.putBoolean("isGraphVisible", isGraphVisible);
        outState.putSerializable("attendance", attendance);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        boolean isGraphVisible = savedInstanceState.getBoolean("isGraphVisible");
        attendance = (Attendance) savedInstanceState.getSerializable("attendance");

        if (isGraphVisible) {
            graphList.clear();

            new SelectAllAttendancesOfStudent().execute();
        }
    }

    private void closeWindow() {
        finish();
        overridePendingTransition(R.anim.stand_still, R.anim.move_out_to_bottom);
    }

    @Override
    public void onBackPressed() {
        //if the graph is open, close it
        if (graphLayout.getVisibility() == View.VISIBLE) {
            hideGraph();
        } else { //otherwise leave the screen
            closeWindow();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar actions click
        switch (item.getItemId()) {
            case android.R.id.home:
                closeWindow();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}