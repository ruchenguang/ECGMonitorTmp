package cn.edu.zju.ecgmonitor.tmp.updaters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.renderscript.FieldPacker;
import android.util.Log;

public class DataRecorder {
	File recordDir;
	File recordFile[];
	FileOutputStream fos[];
	String fileName, recordDirPath;
	public DataRecorder(String recordDirPath){
		this.recordDirPath = recordDirPath;
	}
	
	public void startWriting(){
		Date currentDate = new Date(System.currentTimeMillis()); 
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.CHINA); 
		fileName = formatter.format(currentDate);
		
//		recordDir = new File(recordDirPath, fileName);
//		if(!recordDir.exists())
//			recordDir.mkdir();
		
//		recordFile = new File[6];
//		for(int i=0; i<6; i++)
//			recordFile[i] = new File(recordDir.getAbsolutePath(), String.valueOf(i)+".txt");
		recordFile = new File[6];
		recordFile[0] = new File(recordDirPath, fileName+".txt");
		
		
//		recordFile[0] = new File(recordDirPath, fileName);
		try {
			fos = new FileOutputStream[6];
//			for(int i=0; i<1; i++)
//				fos[i] = new FileOutputStream(recordFile[i]);
			fos[0] = new FileOutputStream(recordFile[0]);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		bleRawData = new byte[6][20];
	}
	
	int cnt = 0;
	byte[][] bleRawData;
	public void writeToFile(byte[] rawData){
		if(rawData.length != 20) 
			Log.e("Data", "Got data with size " + bleRawData.length);
		Log.d("Data", "the cnt is " + cnt +" and the data is " + (rawData[0]&0xff));
		if(cnt<6){
			for(int i=0; i<20; i++){
				bleRawData[cnt][i] = rawData[i];
			}
			
			if(cnt != 5) cnt++;
			else{
				cnt = 0;
				try {
		        	for(int i=0; i<6; i++){
		        		for(int j=0; j<10; j++){
		            		fos[0].write(String.valueOf((int) (bleRawData[i][2*j]&0xff)*256 + (bleRawData[i][2*j+1]&0xff)).getBytes());
		            		fos[0].write(" ".getBytes());
		        		}
		        	}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	
	public void stopWriting(){
		try {
			fos[0].close();
//			for(int i=0; i<6; i++)
//				fos[i].close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
