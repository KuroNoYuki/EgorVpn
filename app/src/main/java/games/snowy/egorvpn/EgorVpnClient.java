package games.snowy.egorvpn;

/**
 * Created by eshas on 25.06.2018.
 */

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.TextView;

import e.eshas.vpn.R;

/**
 * Created by eshas on 25.06.2018.
 */

public class EgorVpnClient extends Activity {

    public interface Prefs{
        String NAME = "connection";
        String SERVER_ADDRESS = "server.address";
        String SERVER_PORT = "server.port";
        String SHARED_SECRET = "shared.secret";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_egor);

        final TextView serverAddress = (TextView) findViewById(R.id.address);
        final TextView serverPort = (TextView) findViewById(R.id.port);
        final TextView sharedSecret = (TextView) findViewById(R.id.secret);

        final SharedPreferences prefs = getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        serverAddress.setText(prefs.getString(Prefs.SERVER_ADDRESS, ""));
        serverPort.setText(prefs.getString(Prefs.SERVER_PORT, ""));
        sharedSecret.setText(prefs.getString(Prefs.SHARED_SECRET, ""));

        findViewById(R.id.connect).setOnClickListener(v -> {
            prefs.edit()
                    .putString(Prefs.SERVER_ADDRESS, serverAddress.getText().toString())
                    .putString(Prefs.SERVER_PORT, serverPort.getText().toString())
                    .putString(Prefs.SHARED_SECRET, sharedSecret.getText().toString())
                    .commit();

            Intent intent = VpnService.prepare(EgorVpnClient.this);
            if (intent != null) {
                startActivityForResult(intent, 0);
            } else {
                onActivityResult(0, RESULT_OK, null);
            }
        });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data){
        if(result == RESULT_OK){
            startService(getServiceIntent().setAction(EgorVpnService.ACTION_CONNECT));
        }

    }
    private Intent getServiceIntent(){
        return new Intent(this, EgorVpnService.class);
    }
}
