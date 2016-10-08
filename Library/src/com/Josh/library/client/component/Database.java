package com.Josh.library.client.component;

import java.util.HashMap;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * this class deals with database
 * @author Josh
 *
 */
public class Database {
	
	public static final String KEY_ROWID = "_id";//idÁÐµÄ¹Ø¼ü×Ö
	public static final String KEY_METHOD_NAME = "methodName";	
	public static final String KEY_METHOD_TIME_NORMAL= "timeNormal";
	public static final String KEY_METHOD_TIME_REMOTE = "timeRemote";
	public static final int INDEX_ROWID = 0;	
	public static final int INDEX_METHOD_NAME = 1;
	public static final int INDEX_METHOD_TIME_NORMAL = 2;
	public static final int INDEX_METHOD_TIME_REMOTE = 3;
	private static final String TAG = "database";
	private static final String DATABASE_NAME = "methodData.db";
	private static final String DATABASE_TABLE = "methodData";
	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_CREATE = 
		"CREATE TABLE methodData("
			+" _id INTEGER PRIMARY KEY AUTOINCREMENT,"			
			+" methodName TEXT,"
			+" timeNormal INTEGER,"
			+" timeRemote INTEGER"
			+" );";
	private final Map<String,Integer> method2RowidMap = new HashMap<String,Integer>();
	private final Map<String,Integer[]> methodTime = new HashMap<String,Integer[]>();
	private final Context context;
	private DatabaseHelper DBHelper;
	private SQLiteDatabase db;
	
	public Database(Context ctx){
		this.context = ctx;
		DBHelper=new DatabaseHelper(context);
		searchDatabase();
	}
		
	private class DatabaseHelper extends SQLiteOpenHelper{
		
		DatabaseHelper(Context ctx){
			super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
			}
		
		@Override
		public void onCreate(SQLiteDatabase db){
			db.execSQL(DATABASE_CREATE);
			}
		
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion,int newVersion){
			Log.w(TAG, "Upgrading database from version " + oldVersion+ " to "+ newVersion + ", which will destroy all old data");
			db.execSQL("DROP TABLE IF EXISTS titles");
			onCreate(db);
			}
		}
	
	/**
	 * open this database
	 * @return this database
	 * @throws SQLException
	 */
	public Database open() throws SQLException
	{
		db = DBHelper.getWritableDatabase();
		return this;
		}
	
	/**
	 * close this database
	 */
	public void close()
	{
		DBHelper.close();
	}
	
	/**
	 * insert method data to database
	 * @param name
	 * 		full name of a method
	 * @param normalTime
	 * 		time of normal execution
	 * @param remoteTime
	 * 		time of remote execution
	 * @return
	 * 		the row ID of the newly inserted row, or -1 if an error occurred
	 */
	public long insertMethoddata(String name,int normalTime, int remoteTime )
	{
		ContentValues initialValues = new ContentValues();		
		initialValues.put(KEY_METHOD_NAME, name);
		initialValues.put(KEY_METHOD_TIME_NORMAL, normalTime);
		initialValues.put(KEY_METHOD_TIME_REMOTE, remoteTime);
		return db.insert(DATABASE_TABLE, null, initialValues);
		}
	
	/**
	 * delete a method data
	 * @param rowId
	 * 		the row id of a method
	 * @return
	 * 		if deleting succeed
	 */
	public boolean deleteMethoddata(int rowId)
	{
		return db.delete(DATABASE_TABLE, KEY_ROWID + "=" + rowId, null) > 0;
	}
	
	/**
	 * load all data in the database
	 * @return a cursor that contains all the data in the database
	 */
	public Cursor getAllMethodData()
	{
		return db.query(DATABASE_TABLE,
				new String[] {KEY_ROWID,KEY_METHOD_NAME
				,KEY_METHOD_TIME_NORMAL,KEY_METHOD_TIME_REMOTE},
				null,null,null,null,null);
	}
	
	/**
	 * get data of a specific method
	 * @param rowId
	 * 		the row id of the method
	 * @return
	 * 		a cursor that contains data of the specific method
	 * @throws SQLException
	 */
	public Cursor getMethodData(int rowId) throws SQLException
	{
		Cursor mCursor =db.query(true, DATABASE_TABLE,
				new String[] {KEY_ROWID,KEY_METHOD_NAME
				,KEY_METHOD_TIME_NORMAL,KEY_METHOD_TIME_REMOTE},
				KEY_ROWID + "=" + rowId,
				null,null,null,null,null);
		if (mCursor != null) {
			mCursor.moveToFirst();
			}
		return mCursor;
		}
	
	/**
	 * update data of a specific method
	 * @param rowId
	 * 		the row id of a method
	 * @param name
	 * 		the full name of the method
	 * @param normalTime
	 * 		time spent for normal execution
	 * @param remoteTime
	 * 		time spent for remote execution
	 * @return
	 * 		the number of rows affected
	 */
	public boolean updateMethoddata(int rowId, String name, int normalTime, int remoteTime )
	{
		ContentValues args = new ContentValues();	
		args.put(KEY_METHOD_NAME, name);
		args.put(KEY_METHOD_TIME_NORMAL, normalTime);
		args.put(KEY_METHOD_TIME_REMOTE, remoteTime);
		return db.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
	}
	
	/**
	 * set the normal execution time of a method, this method will consider all recorded normal 
	 * execution time to find the average value of normal execution time, and save the average value.
	 * @param name
	 * 		the specific name of a method
	 * @param normalTime
	 * 		the normal execution time
	 */
	public synchronized void setMethodNormalExecutionTime(String name, int normalTime){	
		Integer rowId = this.method2RowidMap.get(name);
		Integer[] times = this.methodTime.get(name);
		if(times == null){
			times = new Integer[8];
			times[0] = -1;
			times[1] = -1;
			times[2] = -1;
			times[3] = 1;
			times[4] = -1;
			times[5] = 1;
			times[6] = 0;
			times[7] = 0;

			this.methodTime.put(name, times);
		}
		if(times[2]<Integer.MAX_VALUE-normalTime && times[3]<Integer.MAX_VALUE-1){
			times[2] +=normalTime;
			times[3] ++; 
			times[0] = times[2]/times[3];
		}else{
			times[2] = normalTime;
			times[3] = 0;
			times[0] = normalTime;
		}
		this.open();
		if(rowId == null){
			method2RowidMap.put(name,(int)insertMethoddata(name, times[0], times[1]));
		}else{
			this.updateMethoddata(rowId, name, times[0], times[1]);
		}
		this.close();
	}
	
	/**
	 * set the remote execution time of a method, this method will consider all recorded remote 
	 * execution time to find the average value of remote execution time, and save the average value.
	 * @param name
	 * 		the specific name of a method
	 * @param normalTime
	 * 		the remote execution time
	 */
	public synchronized void setMethodRemoteExecutionTime(String name, int remoteTime){
		Integer rowId = this.method2RowidMap.get(name);
		Integer[] times = this.methodTime.get(name);
		if(times == null){
			times = new Integer[8];
			times[0] = -1;
			times[1] = -1;
			times[2] = -1;
			times[3] = 1;
			times[4] = -1;
			times[5] = 1;
			times[6] = 0;
			times[7] = 0;
			this.methodTime.put(name, times);
		}
		if(times[4]<Integer.MAX_VALUE-remoteTime && times[5]<Integer.MAX_VALUE-1){
			times[4] += remoteTime;
			times[5]++;
			times[1]=times[4]/times[5];
		}else{
			times[4] = remoteTime;
			times[5] = 1;
			times[1] = remoteTime;
		}
		this.open();
		if(rowId == null){
			method2RowidMap.put(name,(int)insertMethoddata(name, times[0], times[1]));
		}else{
			this.updateMethoddata(rowId, name, times[0], times[1]);
		}
		this.close();
	}
	
	
	/**
	 * get the remote execution time of a specific method 
	 * @param name
	 * 		full name of the method
	 * @return
	 * 		the remote execution time
	 */
	public synchronized int getMethodRemoteExecutionTime(String name){
		Integer[] result = this.methodTime.get(name);
		if(result == null) return -1;
		else
			return result[1];
	}
	
	/**
	 * get the normal execution time of a specific method 
	 * @param name
	 * 		full name of the method
	 * @return
	 * 		the normal execution time
	 */
	public synchronized int getMethodNormalExecutionTime(String name){
		Integer[] result = this.methodTime.get(name);
		if(result == null) return -1;
		else
			return result[0];
	}
	
	/**
	 * Decide if a method should be offloaded. This decision is made only by
	 * considering the normal execution time and remote execution time. In principle,
	 * if the method execution time is less in server, it should be executed in the
	 * server, otherwise it should be executed in the client. If the same decision has been
	 * made by this method continuously for more than 5 times, this method will make the
	 * opposite decision. That means if this method has decided to execute a method in the 
	 * server continuously for more than 5 times, it will decide to execute it in the client
	 * this time, so does it if it has decided to execute in the client continuously for
	 * more than 5 times.
	 * @param name
	 * 		the full name of a method
	 * @return
	 * 		should or not
	 */
	public synchronized boolean shouldExecuteRemotely(String name){
		Integer[] times = this.methodTime.get(name);
		if(times==null) return false;
		else{
			if(times[0]>times[1]){
				if(times[6] == 0){
					times[6] = 1;
					times[7] = 1;
				}else{
					times[7]++;
					if(times[7]>5){
						times[6] = 0;
						times[7] = 1;
						return false;
					}
				}
				return true;
			}
			else{
				if(times[6] == 0){
					times[7]++;
					if(times[7]>5){
						times[6] = 1;
						times[7] = 1;
						return true;
					}
				}else{
					times[6] = 0;
					times[7] = 1;
				}
				return false;
			}
		}
	}
	
	/**
	 * load all information in the database to memory, this action will boost the search process
	 */
	private void searchDatabase(){
		this.open();
		Cursor cursor = this.getAllMethodData();		
		while(cursor.moveToNext()){
			String name = cursor.getString(INDEX_METHOD_NAME);
			int normalTime = cursor.getInt(INDEX_METHOD_TIME_NORMAL);
			int remoteTime = cursor.getInt(INDEX_METHOD_TIME_REMOTE);
			int rowid = cursor.getInt(INDEX_ROWID);
			this.method2RowidMap.put(name, rowid);
			Integer[] times = new Integer[8];
			times[0] = normalTime;
			times[1] = remoteTime;
			times[2] = normalTime;
			times[3] = 1;
			times[4] = remoteTime;
			times[5] = 1;
			times[6] = 0;
			times[7] = 0;
			this.methodTime.put(name, times);
		}
		this.close();
	}
	
	
	
	
}

