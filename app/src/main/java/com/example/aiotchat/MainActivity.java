package com.example.aiotchat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText editServerName;
    private Button btnStartServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editServerName = findViewById(R.id.editServerName);
        btnStartServer = findViewById(R.id.btnStartServer);

        btnStartServer.setOnClickListener(v -> {
            String serverName =
                    editServerName.getText().toString().trim();

            if (serverName.isEmpty()) {
                Toast.makeText(this,
                        "請輸入 Server 名稱",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(
                    MainActivity.this,
                    ServerChatActivity.class
            );

            intent.putExtra("SERVER_NAME", serverName);
            startActivity(intent);
        });
    }
}
