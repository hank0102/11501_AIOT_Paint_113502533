
package com.example.aiotchat.client;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText editClientName;
    private EditText editServerIP;
    private EditText editPort;
    private Button btnConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editClientName = findViewById(R.id.editClientName);
        editServerIP = findViewById(R.id.editServerIP);
        editPort = findViewById(R.id.editPort);
        btnConnect = findViewById(R.id.btnConnect);

        btnConnect.setOnClickListener(v -> {

            String name = editClientName.getText()
                    .toString().trim();

            String ip = editServerIP.getText()
                    .toString().trim();

            String portText = editPort.getText()
                    .toString().trim();

            if (name.isEmpty() || ip.isEmpty()
                    || portText.isEmpty()) {

                Toast.makeText(this,
                        "請填寫所有欄位",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            int port;

            try {
                port = Integer.parseInt(portText);

                if (port < 1 || port > 65535) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this,
                        "Port 必須介於 1～65535",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(
                    MainActivity.this,
                    ClientChatActivity.class
            );

            intent.putExtra("CLIENT_NAME", name);
            intent.putExtra("SERVER_IP", ip);
            intent.putExtra("SERVER_PORT", port);

            startActivity(intent);
        });
    }
}
