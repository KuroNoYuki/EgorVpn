package games.snowy.egorvpn;

/**
 * Created by eshas on 25.06.2018.
 */

import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.Pair;
import android.widget.ThemedSpinnerAdapter;
import android.widget.Toast;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
/**
 * Created by eshas on 25.06.2018.
 */

public class EgorVpnService extends VpnService implements Handler.Callback {
    private static final String TAG = "EgorVpn";
    public static final String ACTION_CONNECT = "snowygames.egorvpnservice.START";
    public static final String ACTION_DISCONNECT = "snowygames.egorvpnservice.STOP";

    //private Handler mHandler;

    @Override
    public boolean handleMessage(Message message) {
        return false;
    }

    private static class Connection extends Pair<Thread, ParcelFileDescriptor>{

        public Connection(Thread thread, ParcelFileDescriptor pfd) {
            super(thread, pfd);
        }
    }

    private final AtomicReference<Thread> mConnectingThread = new AtomicReference<>();
    private final AtomicReference<Connection> mConnection = new AtomicReference<>();

    private AtomicInteger mNextConnectionId = new AtomicInteger(1);

    private PendingIntent mConfigureIntent;

    @Override
    public void onCreate(){
      /* if(mHandler == null){
            mHandler = new Handler(this);
        }*/
        mConfigureIntent = PendingIntent.getActivity(this,0,new Intent(this, EgorVpnClient.class),PendingIntent.FLAG_UPDATE_CURRENT);

    }

    @Override
    public int onStartCommand(Intent intent,int flags, int startId){
        if(intent != null&& ACTION_DISCONNECT.equals(intent.getAction())){
            disconnect();
            return START_NOT_STICKY;
        }else{
            connect();
            return START_STICKY;
        }
    }

    @Override
    public void onDestroy(){
        disconnect();
    }

    public void connect(){
        // updateForegroundNotification(R.string.connecting);
        // mHandler.sendEmptyMessage(R.string.connecting);

        final SharedPreferences prefs = getSharedPreferences(EgorVpnClient.Prefs.NAME, MODE_PRIVATE);
        final String server = prefs.getString(EgorVpnClient.Prefs.SERVER_ADDRESS,"");
        final byte[] secret = prefs.getString(EgorVpnClient.Prefs.SHARED_SECRET, "").getBytes();
        final int port;

        try{
            port = Integer.parseInt(prefs.getString(EgorVpnClient.Prefs.SERVER_PORT,null));
        } catch (NumberFormatException e){
            Log.e(TAG, "Bad port: " + prefs.getString(EgorVpnClient.Prefs.SERVER_PORT,null),e);
            return;
        }

        startConnection(new EgorVpnConnection(this,mNextConnectionId.getAndIncrement(),server, port, secret));
    }

    public void startConnection(final EgorVpnConnection connection){
        final Thread thread = new Thread(connection,"EgorVpnThread");
        setConnectingThread(thread);

        connection.setConfigureIntent(mConfigureIntent);
        connection.setOnEstablishListener(new EgorVpnConnection.OnEstablishListener() {
            @Override
            public void onEstablish(ParcelFileDescriptor tunInterface) {
                //mHandler.sendEmptyMessage(R.string.connected);

                mConnectingThread.compareAndSet(thread,null);
                setConnection(new Connection(thread, tunInterface));
            }
        });
        thread.start();
    }

    private void setConnectingThread(final Thread thread){
        final Thread oldThread = mConnectingThread.getAndSet(thread);
        if(oldThread != null){
            oldThread.interrupt();
        }
    }
    private void setConnection(final Connection connection){
        final Connection oldConnection = mConnection.getAndSet(connection);
        if(oldConnection != null){
            try{
                oldConnection.first.interrupt();
                oldConnection.second.close();
            } catch (IOException e) {
                Log.e(TAG, "Closing VPN interface",e);
            }
        }
    }
    private void disconnect(){
        // mHandler.sendEmptyMessage(R.string.disconnected);
        setConnectingThread(null);
        setConnection(null);
        stopForeground(true);
    }

}