
package com.example.aiotchat.client;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class ClientChatActivity extends AppCompatActivity {

    private PaintCanvasView paintCanvas;

    private TextView tvClientStatus;
    private TextView tvPaintStatus;
    private TextView tvToolMode;

    private Button btnPen;
    private Button btnEraser;
    private Button btnUndo;
    private Button btnClear;
    private Button btnBack;

    private String clientName;
    private String serverIP;
    private int serverPort;

    private volatile Socket socket;
    private volatile PrintWriter writer;

    private volatile boolean leaving = false;

    private final ExecutorService sender =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_client_chat);

        paintCanvas = findViewById(R.id.paintCanvas);

        tvClientStatus = findViewById(R.id.tvClientStatus);
        tvPaintStatus = findViewById(R.id.tvPaintStatus);
        tvToolMode = findViewById(R.id.tvToolMode);

        btnPen = findViewById(R.id.btnPen);
        btnEraser = findViewById(R.id.btnEraser);
        btnUndo = findViewById(R.id.btnUndo);
        btnClear = findViewById(R.id.btnClear);
        btnBack = findViewById(R.id.btnBack);

        clientName = getIntent()
                .getStringExtra("CLIENT_NAME");

        serverIP = getIntent()
                .getStringExtra("SERVER_IP");

        serverPort = getIntent()
                .getIntExtra("SERVER_PORT", 8080);

        if (clientName == null ||
                clientName.trim().isEmpty()) {
            clientName = "Client";
        }

        clientName = clientName.trim()
                .replace("|", "_")
                .replace("\n", " ")
                .replace("\r", " ");

        paintCanvas.setLocalOwner(clientName);

        tvClientStatus.setText(
                "Online Paint - " + clientName
        );

        tvPaintStatus.setText("正在連線...");
        updateToolMode();

        // Server 分配顏色之前不允許操作
        setDrawingEnabled(false);

        // 畫筆：完成一筆後同步給 Server
        paintCanvas.setOnStrokeFinishedListener(stroke -> {

            String points =
                    paintCanvas.encodePoints(stroke);

            sendLine(
                    "DRAW|" + stroke.id + "|" + points
            );
        });

        // 橡皮擦：點選自己的筆跡後請求 Server 刪除
        paintCanvas.setOnEraseStrokeListener(strokeId -> {

            if (strokeId == null) {
                showStatus("請點選自己的筆跡");
                return;
            }

            // 沿用 Server 已支援的指定筆跡刪除協定
            sendLine("UNDO|" + strokeId);

            showStatus("已選取筆跡，等待同步刪除");
        });

        // 切換到畫筆模式
        btnPen.setOnClickListener(v -> {

            paintCanvas.setEraserMode(false);
            updateToolMode();
        });

        // 切換到橡皮擦模式
        btnEraser.setOnClickListener(v -> {

            paintCanvas.setEraserMode(true);
            updateToolMode();
        });

        // 撤銷自己最後畫的一筆
        btnUndo.setOnClickListener(v -> {

            String id = paintCanvas.undoLastStroke();

            if (id != null) {
                sendLine("UNDO|" + id);
            } else {
                showStatus("沒有可以撤銷的筆跡");
            }
        });

        // 清除自己的所有筆跡
        btnClear.setOnClickListener(v -> {

            paintCanvas.clearOwnerStrokes(clientName);
            sendLine("CLEAR_MINE");
        });

        // 主動離開畫板
        btnBack.setOnClickListener(v -> finish());

        connectToServer();
    }

    private void updateToolMode() {

        boolean eraser = paintCanvas.isEraserMode();

        tvToolMode.setText(
                eraser
                        ? "目前工具：橡皮擦（點選自己的線條）"
                        : "目前工具：畫筆（拖曳繪畫）"
        );

        btnPen.setText(eraser ? "畫筆" : "✓ 畫筆");
        btnEraser.setText(eraser ? "✓ 橡皮擦" : "橡皮擦");
    }

    private void setDrawingEnabled(boolean enabled) {

        paintCanvas.setEnabled(enabled);

        btnPen.setEnabled(enabled);
        btnEraser.setEnabled(enabled);
        btnUndo.setEnabled(enabled);
        btnClear.setEnabled(enabled);
    }

    private void connectToServer() {

        new Thread(() -> {

            Socket connection = new Socket();
            socket = connection;

            try {

                connection.connect(
                        new InetSocketAddress(
                                serverIP, serverPort
                        ),
                        5000
                );

                if (leaving) {
                    return;
                }

                PrintWriter out = new PrintWriter(
                        new OutputStreamWriter(
                                connection.getOutputStream(),
                                StandardCharsets.UTF_8
                        ),
                        true
                );

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                );

                writer = out;

                // 向 Server 登記使用者名稱
                synchronized (out) {

                    out.println("JOIN|" + clientName);

                    if (out.checkError()) {
                        throw new IOException(
                                "JOIN 傳送失敗"
                        );
                    }
                }

                showStatus(
                        "已連線，等待 Server 分配畫筆顏色..."
                );

                // 持續接收 Server 廣播
                String line;

                while (!leaving &&
                        (line = in.readLine()) != null) {

                    handleServerMessage(line);
                }

                if (!leaving) {
                    returnToConnectionScreen(
                            "Server 連線已中斷"
                    );
                }

            } catch (IOException e) {

                if (!leaving) {
                    returnToConnectionScreen(
                            "連線失敗：" + e.getMessage()
                    );
                }

            } finally {

                writer = null;

                try {
                    connection.close();
                } catch (IOException ignored) {
                }

                socket = null;

                if (!leaving) {
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            setDrawingEnabled(false);
                        }
                    });
                }
            }

        }).start();
    }

    private void handleServerMessage(String line) {

        // 被 Server 踢出
        if (line.startsWith("KICK|")) {

            returnToConnectionScreen(line.substring(5));
            return;
        }

        // Server 主動關閉
        if (line.equals("SERVER_CLOSED")) {

            returnToConnectionScreen("Server 已關閉");
            return;
        }

        // 名稱重複或加入遭拒絕
        if (line.startsWith("REJECT|")) {

            returnToConnectionScreen(
                    "無法加入：" + line.substring(7)
            );
            return;
        }

        // Server 分配專屬顏色
        if (line.startsWith("COLOR|")) {

            try {

                int color = Integer.parseInt(
                        line.substring(6)
                );

                runOnUiThread(() -> {

                    if (leaving || isFinishing() || isDestroyed()) {
                        return;
                    }

                    paintCanvas.setPenColor(color);
                    setDrawingEnabled(true);

                    tvPaintStatus.setText(
                            "已連線，可以開始繪畫"
                    );
                });

            } catch (NumberFormatException e) {
                showStatus("畫筆顏色資料錯誤");
            }
        }

        // 系統通知
        else if (line.startsWith("SYSTEM|")) {

            showStatus(line.substring(7));
        }

        // 接收其他使用者的筆跡
        // STROKE|名稱|ID|顏色|座標
        else if (line.startsWith("STROKE|")) {

            String[] parts = line.split("\\|", 5);

            if (parts.length != 5) {
                return;
            }

            String owner = parts[1];
            String id = parts[2];
            String points = parts[4];

            try {

                int color = Integer.parseInt(parts[3]);

                runOnUiThread(() -> {

                    if (!leaving && !isFinishing() && !isDestroyed()) {

                        paintCanvas.addRemoteStroke(
                                owner,
                                id,
                                color,
                                points
                        );
                    }
                });

            } catch (NumberFormatException e) {
                showStatus("收到無效的畫筆顏色");
            }
        }

        // Server 確認刪除指定筆跡
        else if (line.startsWith("REMOVE|")) {

            String id = line.substring(7);

            runOnUiThread(() -> {

                if (!leaving && !isFinishing() && !isDestroyed()) {

                    paintCanvas.removeStroke(id);
                }
            });
        }

        // 清除指定使用者的所有筆跡
        else if (line.startsWith("CLEAR_OWNER|")) {

            String owner = line.substring(12);

            runOnUiThread(() -> {

                if (!leaving && !isFinishing() && !isDestroyed()) {

                    paintCanvas.clearOwnerStrokes(owner);
                }
            });
        }

        // 清除整張畫布
        else if (line.equals("CLEAR_ALL")) {

            runOnUiThread(() -> {

                if (!leaving && !isFinishing() && !isDestroyed()) {

                    paintCanvas.clearAllStrokes();
                }
            });
        }
    }

    // 讓網路資料按順序傳送
    private void sendLine(String line) {

        if (leaving || sender.isShutdown()) {
            return;
        }

        try {
            sender.execute(() -> {

                PrintWriter out = writer;

                if (out == null || leaving) {
                    return;
                }

                synchronized (out) {

                    out.println(line);

                    if (out.checkError()) {
                        showStatus("資料傳送失敗");
                    }
                }
            });

        } catch (RejectedExecutionException ignored) {
            // Activity 已關閉，無須再傳送
        }
    }

    private void showStatus(String message) {

        runOnUiThread(() -> {

            if (!leaving && !isFinishing() && !isDestroyed()) {

                tvPaintStatus.setText(message);
            }
        });
    }

    private void returnToConnectionScreen(String reason) {

        if (leaving) {
            return;
        }

        leaving = true;

        runOnUiThread(() -> {

            if (!isFinishing() && !isDestroyed()) {

                Toast.makeText(
                        this,
                        reason,
                        Toast.LENGTH_LONG
                ).show();

                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {

        leaving = true;

        try {

            if (socket != null) {
                socket.close();
            }

        } catch (IOException ignored) {
        }

        sender.shutdownNow();

        super.onDestroy();
    }
}
