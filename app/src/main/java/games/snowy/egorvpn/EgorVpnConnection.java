package games.snowy.egorvpn;

/**
 * Created by eshas on 25.06.2018.
 */

import static java.nio.charset.StandardCharsets.US_ASCII;

import android.app.PendingIntent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.TimeUnit;

public class EgorVpnConnection implements Runnable {

    public interface OnEstablishListener {
        void onEstablish(ParcelFileDescriptor tunInterface);
    }

    private final VpnService mService;
    private final int mConnectionId;
    private final String mServerName;
    private final int mServerPort;
    private final byte[] mSharedSecret;

    private PendingIntent mConfigureIntent;
    private OnEstablishListener mOnEstablishListener;

    private static final int MAX_PACKET_SIZE = Short.MAX_VALUE;
    private static final long IDLE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(20);
    private static final long KEEPALIVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(15);
    private static final long RECEIVE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20);


    public EgorVpnConnection(final VpnService service, final int connectionid, final String serverName, final int serverPort, final byte[] sharedSecret){
        mService = service;
        mConnectionId = connectionid;

        mServerName = serverName;
        mServerPort = serverPort;
        mSharedSecret = sharedSecret;
    }

    public void setConfigureIntent(PendingIntent intent) {
        mConfigureIntent = intent;
    }
    public void setOnEstablishListener(OnEstablishListener listener) {
        mOnEstablishListener = listener;
    }

    @Override
    public void run(){
        try{
            final SocketAddress serverAdress = new InetSocketAddress(mServerName,mServerPort);

            for(int attempt = 0; attempt < 10; ++attempt){
                if(run(serverAdress)){
                    attempt = 0;
                }
            }

            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean run(SocketAddress server)
            throws IOException, InterruptedException, IllegalArgumentException {
        ParcelFileDescriptor iface = null;
        boolean connected = false;
        try (DatagramChannel tunnel = DatagramChannel.open()){
            if (!mService.protect(tunnel.socket())){
                throw new IllegalStateException("Cannot protect the tunnel");}
            tunnel.connect(server);

            tunnel.configureBlocking(false);
            iface = handshake(tunnel);

            connected = true;

            FileInputStream in = new FileInputStream(iface.getFileDescriptor());

            FileOutputStream out = new FileOutputStream(iface.getFileDescriptor());

            ByteBuffer packet = ByteBuffer.allocate(MAX_PACKET_SIZE);
            long lastSendTime = System.currentTimeMillis();
            long lastReceiveTime = System.currentTimeMillis();

            while (true) {

                boolean idle = true;


                int length = in.read(packet.array());
                if (length > 0) {

                    packet.limit(length);
                    tunnel.write(packet);
                    packet.clear();


                    idle = false;
                    lastReceiveTime = System.currentTimeMillis();
                }
                int lenght = tunnel.read(packet);

                if(lenght > 0){
                    if(packet.get(0) != 0){
                        out.write(packet.array(),0,lenght);
                    }
                }
                packet.clear();
                idle = false;
                lastSendTime = System.currentTimeMillis();

                if (idle){
                    Thread.sleep(IDLE_INTERVAL_MS);
                    final long timeNow = System.currentTimeMillis();

                    if (lastSendTime + KEEPALIVE_INTERVAL_MS <= timeNow) {
                        packet.put((byte) 0).limit(1);
                        for (int i = 0; i < 3; ++i ) {
                            packet.position(0);
                            tunnel.write(packet);
                        }
                        packet.clear();
                        lastSendTime = timeNow;
                    } else if(lastReceiveTime + RECEIVE_TIMEOUT_MS <= timeNow){
                        throw new IllegalStateException("Timed out");
                    }

                }
            }

        }finally {
            if (iface != null){
                try{
                    iface.close();
                }catch (IOException e){
                    Log.e(getTag(), "Unable to close interface", e);
                }
            }
            return connected;
        }}

    private ParcelFileDescriptor handshake(DatagramChannel tunnel)
            throws IOException, InterruptedException{
        ByteBuffer packet = ByteBuffer.allocate(1024);

        packet.put((byte) 0).put(mSharedSecret).flip();

        for (int i = 0; i < 3; ++i){
            Thread.sleep(IDLE_INTERVAL_MS);
            int lenght = tunnel.read(packet);
            if (lenght > 00 && packet.get(0) == 0){
                return configure(new String(packet.array(),1,lenght - 1, US_ASCII).trim());
            }
        }
        throw new IOException("Timed out");
    }

    private ParcelFileDescriptor configure(String parameters) throws IllegalArgumentException{
        VpnService.Builder builder = mService.new Builder();
        for (String parameter: parameters.split(" ")){
            String[] fields = parameter.split(",");
            try{
                switch (fields[0].charAt(0)){
                    case 'm':
                        builder.setMtu(Short.parseShort(fields[1]));
                        break;
                    case 'a':
                        builder.addAddress(fields[1], Integer.parseInt((fields[2])));
                        break;
                    case 'r':
                        builder.addRoute(fields[1], Integer.parseInt(fields[2]));
                        break;
                    case 'd':
                        builder.addDnsServer(fields[1]);
                        break;
                    case 's':
                        builder.addSearchDomain(fields[1]);
                        break;
                }
            }catch (NumberFormatException e){
                throw new IllegalArgumentException("Bad parameter: " + parameter);
            }
        }
        final ParcelFileDescriptor vpnInterface;
        synchronized (mService){
            vpnInterface = builder
                    .setSession(mServerName)
                    .setConfigureIntent(mConfigureIntent)
                    .establish();
            if(mOnEstablishListener != null){
                mOnEstablishListener.onEstablish(vpnInterface);
            }
        }
        Log.i(getTag(), "New interface: " + vpnInterface + " (" + parameters + ")");
        return vpnInterface;
    }

    private final String getTag() {
        return EgorVpnConnection.class.getSimpleName() + "[" + mConnectionId + "]";
    }
}