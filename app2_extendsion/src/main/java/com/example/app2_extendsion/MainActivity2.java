package com.example.app2_extendsion;

/*
* Improvements
* 1. Rename file (append end-time to the file name when stop is pressed)
* 2. Chronometer
* */


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class MainActivity2 extends WearableActivity {

    public static final String RESPONSE_STARTED = "11";
    public static final String RESPONSE_STOPPED = "00";
    public static final String RESPONSE_ERROR = "-1";

    private static final int REQUEST_ENABLE_BT = 1;
    // for displaying elapsed time since 'start' button is pressed.
    public Chronometer chrono;

    public String accel_vals;
    public String gyro_vals;
    public String grav_vals;

    // For writing data to files
    FileWriter fw_acc = null, fw_gyro=null, fw_grav=null;
    BufferedWriter bufwr_acc = null, bufwr_gyro=null, bufwr_grav=null;

    private boolean recording_started = false;
    public String dotText = ".txt";

    // UI
    private TextView textView_main, textView_display;
    private Button start_btn, stop_btn;
    private ImageButton blueToothServerButton;
    public TextView status, connectStatus;

    // SensorManager
    private SensorManager sensorManager;

    // For file_names
    public String start_time;
    public String end_time;

    // List of strings for fileNames
    private static final String FILE_NAME_ACCEL = "Acc_";
    private static final String FILE_NAME_GYRO = "Gyro_";
    private static final String FILE_NAME_GRAVITY = "Grav_";
    private static final String FILE_NAME_LINEAR_ACCEL= "LinAcc_";
    private static final String FILE_NAME_HEART_RATE = "Heart_";
    private static final String FILE_NAME_ROTATION = "Rot_";
    private static final String FILE_NAME_ALL_SENSORS = "All_Sensors_info.txt";

    // String to contain sensor data. Pass this string as data to write them on files.
    String append_accel="";
    String append_gyro="";
    String append_grav="";
    String append_linAcc="";
    String append_heartRate="";
    String append_rotation="";

    String total_sensor_info = "";   // For extra information

    // List to contain all sensor objects
    List<Sensor> deviceSensors = null;

    // selected sensors
    ArrayList<Sensor> sensorNeeded = null;

    // File objects
    File file_accel = null;
    File file_gyro = null;
    File file_grav = null;
    File file_linear_accel = null;
    File file_heart_rate = null;
    File file_rotation = null;

    // For sensor information of this device
    File file_all_sensors_info = null;
    File file_sensors_needed;

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_RECEIVED = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_OBJECT = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final String DEVICE_OBJECT = "device_name";

    static boolean blueToothOn = false;
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private BluetoothDevice connectingDevice;
    private ArrayAdapter<String> discoveredDevicesAdapter;
    private BluetoothAdapter bluetoothAdapter;
    private String mConnectedDeviceName=null;
    private Handler handler;
    private ChatController chatController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear2);

        // findViewByIds
        start_btn =  findViewById(R.id.start_button);
        stop_btn = findViewById(R.id.stop_button);
        chrono = findViewById(R.id.chrono);
        status = findViewById(R.id.status);
        connectStatus = findViewById(R.id.connectStatus);



        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else{ // it's already on
            blueToothOn=true;
        }

        // make me discoverable for 5 min
        Intent discoverableIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);

        handler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case Constants.MESSAGE_STATE_CHANGE:
                        switch (msg.arg1) {
                            case ChatController.STATE_CONNECTED:
                                setStatus("connected to " + mConnectedDeviceName);
                                break;
                            case ChatController.STATE_CONNECTING:
                                setStatus("connecting...");
                                break;
                            case ChatController.STATE_LISTEN:
                            case ChatController.STATE_NONE:
                                setStatus("Not connected");
                                break;
                        }
                        break;
                    case Constants.MESSAGE_WRITE:
                        byte[] writeBuf = (byte[]) msg.obj;
                        // construct a string from the buffer
                        String writeMessage = new String(writeBuf);
                        break;
                    case Constants.MESSAGE_READ:
                        byte[] readBuf = (byte[]) msg.obj;
                        // construct a string from the valid bytes in the buffer
                        String readMessage = new String(readBuf, 0, msg.arg1);
                        Toast.makeText(getApplicationContext(), readMessage, Toast.LENGTH_SHORT).show();

                        break;
                    case Constants.MESSAGE_DEVICE_NAME:
                        // save the connected device's name
                        mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                        Toast.makeText(getApplicationContext(), "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();

                        break;
                    case Constants.MESSAGE_TOAST:
                        Toast.makeText(getApplicationContext(), msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();

                        break;
                }
            }
        };

        // create recordController object

        if(blueToothOn){
            connectStatus.setText("BT ON");
            chatController = new ChatController(this, handler);
            Toast.makeText(this, "bluetooth is on", Toast.LENGTH_LONG).show();
        }


        // Get all sensors to this list
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);

        file_all_sensors_info = new File(getExternalFilesDir(null), FILE_NAME_ALL_SENSORS);
        file_sensors_needed = new File(getExternalFilesDir(null), "sensors_needed_list.txt");

        // register onclickListener for two buttons
        set_btn_eventListner();

        sensorNeeded = new ArrayList<>();
        for (Sensor each_sensor: deviceSensors)
        {
            // Sensor number information
            // accel - 1, gyro - 4, gravity - 9, linear_accel - 10, Rotation - 11, Heart rate = 21
            //* Significant motion detector - 17

            if(each_sensor.getType() == 1 || each_sensor.getType() == 4 || each_sensor.getType() == 9 || /*(lin_acc) each_sensor.getType() == 10
                    ||*/ /* (rotation) each_sensor.getType() == 11 ||*/ each_sensor.getType() == 21) {
                sensorNeeded.add(each_sensor);
            }

            // A String type info, about all sensors
            total_sensor_info += each_sensor.toString() + "\n\n";
        }


        // Enables Always-on
        setAmbientEnabled();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    blueToothOn=true;
                    Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Bluetooth still disabled, turn off application!", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private void setStatus(String s) {
        connectStatus.setText(s);
    }


    // Register sensors to the sensorListener
    public void registerSensors(ArrayList<Sensor> sensorNeeded){
        for(Sensor each_sensor: sensorNeeded){
             boolean sensorRegistered = sensorManager.registerListener(mySensorListener,
                     each_sensor, SensorManager.SENSOR_DELAY_NORMAL);
             String sensor_info = (each_sensor.getName() + ": " + each_sensor.getType());
            Log.d("Sensor Status:", sensor_info + " is registered: " + (sensorRegistered ? "yes" : "no"));
        }
    }


    // Close funciton
    public void close_fileWriter(BufferedWriter bufwr, FileWriter fw){
        // close file_accel
        try {
            if (bufwr != null)
                bufwr.close() ;

            if (fw != null)
                fw.close() ;
        } catch (Exception e) {
            e.printStackTrace();
        }
    } // function end

    @Override
    protected void onStart() {
        super.onStart();

        if (bluetoothAdapter == null) {
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else if (chatController == null){
            chatController = new ChatController(this, handler);
            Toast.makeText(this, "chatController spawned", Toast.LENGTH_LONG).show();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        if(chatController != null){
            if(chatController.getState() == ChatController.STATE_NONE){
                chatController.start();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(mySensorListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(chatController !=null){
            chatController.stop();
        }
    }

    // Start Button
    public void set_btn_eventListner(){

        start_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                start_record();
            }
        });

        // Stop Button
        stop_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop_record();
            }
        });
    }

    // start recording and creating files
    public void start_record(){
        Toast.makeText(MainActivity2.this, "Start recording...", Toast.LENGTH_LONG).show();
//        Log.d("state", "state: " + recordController.getState()+"");

        if(chatController.getState() == chatController.STATE_CONNECTED){
            chatController.write("Recording Started".getBytes());
        }


        // [edit]
        // record the time when the start_button was pressed
        Date currentTime = Calendar.getInstance().getTime();
//              SimpleDateFormat dateFormat = new SimpleDateFormat("hh:mm:ss a, MM-dd");
        SimpleDateFormat dateFormat = new SimpleDateFormat("[MM.dd] hh-mm-ss");
        start_time = dateFormat.format(currentTime);

        // Edit: removed .txt at the end of fileName -> will attach it later in the stop_btn event listener.
        // Create .txt files
        file_accel = new File(getExternalFilesDir(null), FILE_NAME_ACCEL + start_time);
        file_gyro = new File(getExternalFilesDir(null), FILE_NAME_GYRO + start_time );
        textView_main.setText("Created Files...\n");

        // Create bufferWriter, fileWriter for each sensor_type
        try {
            fw_acc = new FileWriter(file_accel, true);
            fw_gyro = new FileWriter(file_gyro, true);
//                    fw_grav = new FileWriter(file_grav, true);

            bufwr_acc = new BufferedWriter(fw_acc);
            bufwr_gyro = new BufferedWriter(fw_gyro);
        } catch (IOException e) {
            e.printStackTrace();
        }

        textView_main.append("Created Buffer/File Writer\n");

        // Start to receive sensor data: recording starts
        recording_started = true;
        registerSensors(sensorNeeded);

        // reset time
        chrono.setBase(SystemClock.elapsedRealtime());
        // Start chornometer
        chrono.start();

        textView_main.append("Recording...\n");

        // enable stop button (originally disabled)
        stop_btn.setEnabled(true);
        stop_btn.setClickable(true);
        stop_btn.setVisibility(View.VISIBLE); // Make it appear

        // disable start button
        start_btn.setClickable(false);
        start_btn.setEnabled(false);
        // [edit]
        start_btn.setVisibility(View.GONE);
    }

    // stop recording and save files
    public void stop_record(){

        if(chatController.getState() == chatController.STATE_CONNECTED){
//            // send reponse "stopped recording"
            chatController.write("Recording Stopped".getBytes());
        }

        sensorManager.unregisterListener(mySensorListener);
        chrono.stop();

        Log.d("Stop", "Successfully unregistered mySensorListener");
        textView_main.setText("Unregistered SensorListener.\n");
        // get current time
        Date currentTime = Calendar.getInstance().getTime();
        SimpleDateFormat dateFormat = new SimpleDateFormat("hh-mm-ss");
        end_time = dateFormat.format(currentTime);

                /* Rename Accel file name (add end-time)
                 Adds the finish time to the end of the file name.*/
        String later_name = file_accel.getName() + "..." + end_time + dotText;
        File newFile = new File(file_accel.getParent(), later_name);

        boolean acc_rename=false;
        boolean gyro_rename = false;

        // Rename file with the new name (end time included)
        if(file_accel.renameTo(newFile))
        {
            acc_rename = true;
            Log.d("Rename file", "Success, accel file: " + file_accel.getName());
        }
        else{
            Log.d("Rename file", "accel rename fail");
        }

        // Rename Gyro
        later_name = file_gyro.getName() + "..." + end_time + dotText;
        File newFile_Gyro = new File(file_gyro.getParent(), later_name);
        // Rename file with the new name (end time included)
        if(file_gyro.renameTo(newFile_Gyro))
        {
            gyro_rename=true;
            Log.d("Rename file", "Success, gyro file: " + file_gyro.getName());
        }
        else{
            Log.d("Rename file", "gyro rename fail");
        }

        // if both file names are successfully renamed, put it in the display_text
        if(acc_rename && gyro_rename){
            textView_main.append("File Rename, save success\n");
        }

        // Close writers
        close_fileWriter(bufwr_acc, fw_acc);
//                close_fileWriter(bufwr_grav, fw_grav);
        close_fileWriter(bufwr_gyro, fw_gyro);
        textView_main.append("Closed File Writers\n");
        textView_main.append("Operation Done");

        // recording is done
        recording_started =false;

        // disable stop button
        stop_btn.setEnabled(false);
        stop_btn.setClickable(false);
        stop_btn.setVisibility(View.GONE);

        // Enable start button
        start_btn.setClickable(true);
        start_btn.setEnabled(true);
        start_btn.setVisibility(View.VISIBLE);
    }


    public SensorEventListener mySensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

                if(recording_started ==true) {

                    float ax, ay, az;
                    ax = event.values[0];
                    ay = event.values[1];
                    az = event.values[2];
                    long time_stamp = event.timestamp; // nanoseconds

                    accel_vals = time_stamp + "," + ax + "," + ay + "," + az;

                    // Record the data by line by line to the txt file.
//                    write_to_file(file_accel, accel_data, true);
//                    append_accel += accel_data;

                    try {
                        bufwr_acc.write(accel_vals);
                        bufwr_acc.newLine();
                        bufwr_acc.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            // Gravity Sensor
            if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {

//                if(recording==true) {
//
//                    float ax, ay, az;
//                    ax = event.values[0];
//                    ay = event.values[1];
//                    az = event.values[2];
//                    long time_stamp = event.timestamp; // nanoseconds
//
//                    grav_vals = time_stamp + "," + ax + "," + ay + "," + az + "\n";
//
//                    // Write data to according file by line by line to the txt file.
//                    try {
//                        bufwr_grav.write(grav_vals);
//                        bufwr_grav.newLine();
//                        bufwr_grav.flush();
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                }
            }

            // Gyro sensor
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {

                if(recording_started ==true) {

                    float ax, ay, az;
                    ax = event.values[0];
                    ay = event.values[1];
                    az = event.values[2];
                    long time_stamp = event.timestamp; // nanoseconds

                    gyro_vals = time_stamp + "," + ax + "," + ay + "," + az;

                    // Write data to according file by line by line to the txt file.
                    try {
                        bufwr_gyro.write(gyro_vals);
                        bufwr_gyro.newLine();
                        bufwr_gyro.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {

            }
            // TYPE_HEART_RATE
            if (event.sensor.getType() == 21) {
//                String heart_rate_vals = "";
//                float bpm_float = event.values[0];
//                long bpm_int = Math.round(bpm_float);
//                long timeStamp = event.timestamp;
//
//                heart_rate_vals = timeStamp + "," + bpm_int + "\n";
//                append_heartRate += heart_rate_vals;

            }
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
//                String rotation_vals;
//                float ax, ay, az, as;
//                ax = event.values[0];
//                ay = event.values[1];
//                az = event.values[2];
//                as = event.values[3];
//                long timeStamp = event.timestamp;
//
//                rotation_vals = timeStamp + "," + ax + "," + ay + "," + az + "," + as + "\n";
//                append_rotation += rotation_vals;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };
}


