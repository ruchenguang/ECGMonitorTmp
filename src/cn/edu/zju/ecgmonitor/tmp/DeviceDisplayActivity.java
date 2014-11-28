package cn.edu.zju.ecgmonitor.tmp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cn.edu.zju.curveplotter.CurveSurfaceView;
import cn.edu.zju.ecgmonitor.tmp.ble.BluetoothLeService;
import cn.edu.zju.ecgmonitor.tmp.ble.SampleGattAttributes;
import cn.edu.zju.ecgmonitor.tmp.updaters.AccelerometerUpdater;
import cn.edu.zju.ecgmonitor.tmp.updaters.DataRecorder;
import cn.edu.zju.ecgmonitor.tmp.updaters.RecordReader;
import cn.edu.zju.ecgmonitor.tmp.updaters.RrIntervalsUpdater;
import cn.edu.zju.ecgmonitor.tmp.updaters.SignalProcessor;
import cn.edu.zju.ecgmonitor.tmp.updaters.TimeUpdater;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

public class DeviceDisplayActivity extends Activity {
	public final static String TAG = "MainActivity";

	CurveSurfaceView ecgCurveSfv, rrIntervalsSfv;
	TextView tvHundred, tvTen, tvOne, tvBpm;
	TextView tvAcceX, tvAcceY, tvAcceZ, tvAcceS;
	TextView tvState;
	ImageView ivHeart;
	Button recordButton;
	
	RecordReader recordReader;
	SignalProcessor signalProcessor;
	RrIntervalsUpdater rrIntervalsUpdater;
	AccelerometerUpdater acceUpdater;
	DataRecorder dataRecorder;
	TimeUpdater timeUpdater;
	
	boolean isDevice = true;
	boolean isWritingToFile = false;
	boolean isDisplaying = true;
	boolean isConnected = false;
	
	//*********BLE*********
	private BluetoothLeService mBluetoothLeService;
	String deviceName, deviceAddress;
    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";  
    private BluetoothGattCharacteristic mNotifyCharacteristic;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
        getActionBar().setTitle(R.string.app_name);
        //keep the screen on
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); 
        
        //initialize rrintervlas update to update heart beat and rr intervals curve
        rrIntervalsSfv = (CurveSurfaceView) findViewById(R.id.curveSurfaceView1);
        rrIntervalsSfv.setPointOnScreen(20);
        rrIntervalsSfv.setRedrawParams(200, 1, 1, 1);
        rrIntervalsSfv.setScale(false);
        rrIntervalsSfv.setDisplayRpeaks(false);
        rrIntervalsSfv.setFindRpeak(false);
        
        ivHeart = (ImageView) findViewById(R.id.imageView1);
        tvHundred = (TextView) findViewById(R.id.TextViewHundred);
        tvTen = (TextView) findViewById(R.id.textViewTen);
        tvOne = (TextView) findViewById(R.id.TextViewOne);
        tvBpm = (TextView) findViewById(R.id.textViewBpm);
        
        //initialize record reader
        ecgCurveSfv = (CurveSurfaceView) findViewById(R.id.CurveSurfaceView01);
        ecgCurveSfv.setPointOnScreen(1024);
        ecgCurveSfv.setRedrawParams(40, 10, 2, 500);
        recordButton = (Button) findViewById(R.id.button1);
        recordButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if(isWritingToFile){
					dataRecorder.stopWriting();
					timeUpdater.cancel(R.string.record_btn_name);
					
					recordButton.setText("Record");
					tvState.setText(R.string.state_reading_date_from_ble);
				} else{
					dataRecorder = new DataRecorder(SelectActivity.ecgRecordsDirPath);
					dataRecorder.startWriting();
					timeUpdater = new TimeUpdater(recordButton);
					timeUpdater.schedule(0);
					
					recordButton.setText("Recording...");
					tvState.setText(R.string.state_record_date_to_file);
				}
				isWritingToFile = !isWritingToFile;	
			}
		});
        
        Intent intent = getIntent();
        deviceName = intent.getStringExtra(SelectActivity.EXTRAS_DEVICE_NAME);
    	deviceAddress = intent.getStringExtra(SelectActivity.EXTRAS_DEVICE_ADDRESS);
    	
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        
        signalProcessor = new SignalProcessor(ecgCurveSfv, false);
        signalProcessor.schedule(600, 200);
        
        
        rrIntervalsUpdater = new RrIntervalsUpdater(signalProcessor, rrIntervalsSfv, ivHeart, 
				tvHundred, tvTen, tvOne, tvBpm);
        rrIntervalsUpdater.schedule(500, 200);	
            
        //initialize acceUpdater to update acce sensors data to the textviews
        tvAcceX = (TextView) findViewById(R.id.TextViewAcceX);
        tvAcceY = (TextView) findViewById(R.id.TextViewAcceY);
        tvAcceZ = (TextView) findViewById(R.id.TextViewAcceZ);
        tvAcceS = (TextView) findViewById(R.id.TextViewAcceS);
        acceUpdater = new AccelerometerUpdater(this, tvAcceX, tvAcceY, tvAcceZ, tvAcceS);
        acceUpdater.schedule(500, 500);
        
        tvState = (TextView) findViewById(R.id.textViewState);
        tvState.setText(R.string.state_reading_date_from_ble);
    }
	
    @Override
    protected void onResume() {
        super.onResume();
        isDisplaying = false;
    	registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(deviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }	
        
        //start record at the beginning of this activity
        //**************the code here are copied from recordButton click listener
		if(isWritingToFile){
			dataRecorder.stopWriting();
			timeUpdater.cancel(R.string.record_btn_name);
			
			recordButton.setText("Record");
			tvState.setText(R.string.state_reading_date_from_ble);
		} else{
			dataRecorder = new DataRecorder(SelectActivity.ecgRecordsDirPath);
			dataRecorder.startWriting();
			timeUpdater = new TimeUpdater(recordButton);
			timeUpdater.schedule(0);
			
			recordButton.setText("Recording...");
			tvState.setText(R.string.state_record_date_to_file);
		}
		isWritingToFile = !isWritingToFile;	
		//*******************************code end here*******************************
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        unregisterReceiver(mGattUpdateReceiver);
        mBluetoothLeService.close();
        mBluetoothLeService = null;
        
        isDisplaying = false;   
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        
    	if(isWritingToFile) dataRecorder.stopWriting();
    	signalProcessor.cancel();	
    	acceUpdater.cancel();
    	if(null!=timeUpdater) timeUpdater.cancel();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (isConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(deviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
			mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(deviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };
    
    long cnt = 0;
    long startTime = System.currentTimeMillis();
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
            	byte[] bleRawBytes = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
//            	Log.d(TAG, "raw bytes size is: " + bleRawBytes.length);
            	if(isWritingToFile) 
            		dataRecorder.writeToFile(bleRawBytes);
            	
//            	int[] bleRawInts = new int[bleRawBytes.length/2];
//            	for(int i=0; i<bleRawBytes.length/2; i++){
//            		bleRawInts[i] = (int) (bleRawBytes[2*i]&0xff)*256 + (bleRawBytes[2*i+1]&0xff);
//                }
//        		if(isDisplaying) 
//        			signalProcessor.addData(bleRawInts);      
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
            	isConnected = false;
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
            	connectCharacteristic(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action) ) {
            	isConnected = true;
                invalidateOptionsMenu();
            }
        }
    };
    
    void connectCharacteristic(List<BluetoothGattService> gattServices){
    	if (gattServices == null) {
    		Log.e(TAG, "gattServices is null!");
    		return;
    	}
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = 
        		new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, cn.edu.zju.ecgmonitor.tmp.ble.SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }
        
        // Initiate the connection of ECG signal on the last char of the last service
        // Here the positon is (2, 0)
        final BluetoothGattCharacteristic characteristic =
                mGattCharacteristics.get(2).get(0);
        final int charaProp = characteristic.getProperties();
        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
            // If there is an active notification on a characteristic, clear
            // it first so it doesn't update the data field on the user interface.
            if (mNotifyCharacteristic != null) {
                mBluetoothLeService.setCharacteristicNotification(
                        mNotifyCharacteristic, false);
                mNotifyCharacteristic = null;
            }
            //mBluetoothLeService.readCharacteristic(characteristic);
        }
        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            mNotifyCharacteristic = characteristic;
            mBluetoothLeService.setCharacteristicNotification(
                    characteristic, true);
        }
    }
    
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.setPriority(999);
        return intentFilter;
    }
}
