
package com.example.aiotchat;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class ServerChatActivity extends AppCompatActivity {

    private static final int PORT = 8080;

    private static final int[] COLORS = {
            Color.rgb(229, 57, 53),
            Color.rgb(30, 136, 229),
            Color.rgb(67, 160, 71),
            Color.rgb(142, 36, 170),
            Color.rgb(245, 124, 0),
            Color.rgb(0, 137, 123)
    };

    private PaintCanvasView paintCanvas;
    private TextView tvUsers;
    private TextView tvChatMessages;
    private LinearLayout userList;
    private Button btnClearAll;
    private Button btnCloseServer;

    private String serverName;

    private volatile ServerSocket serverSocket;
    private volatile boolean running = false;

    private final Object stateLock = new Object();
    private final List<ClientPeer> clients = new ArrayList<>();

    // Server 保存目前畫布上的所有筆跡
    private final LinkedHashMap<String, StrokeRecord> strokes =
            new LinkedHashMap<>();

    private final ArrayDeque<String> events =
            new ArrayDeque<>();

    private int nextColorIndex = 0;

    private static class ClientPeer {

        final Socket socket;
        final PrintWriter writer;
        final String name;
        final String ip;
        final int color;

        ClientPeer(
                Socket socket,
                PrintWriter writer,
                String name,
                int color) {

            this.socket = socket;
            this.writer = writer;
            this.name = name;
            this.color = color;

            this.ip = socket.getInetAddress()
                    .getHostAddress();
        }

        synchronized void send(String line) {
            writer.println(line);
        }
    }

    private static class StrokeRecord {

        final String id;
        final String owner;
        final int color;
        final String points;

        StrokeRecord(
                String id,
                String owner,
                int color,
                String points) {

            this.id = id;
            this.owner = owner;
            this.color = color;
            this.points = points;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_server_chat);

        TextView tvServerStatus =
                findViewById(R.id.tvServerStatus);

        paintCanvas = findViewById(R.id.paintCanvas);
        tvUsers = findViewById(R.id.tvUsers);
        tvChatMessages = findViewById(R.id.tvChatMessages);

        userList = findViewById(R.id.userList);
        btnClearAll = findViewById(R.id.btnClearAll);
        btnCloseServer = findViewById(R.id.btnCloseServer);

        serverName = getIntent()
                .getStringExtra("SERVER_NAME");

        if (serverName == null ||
                serverName.trim().isEmpty()) {
            serverName = "Server";
        }

        serverName = serverName.trim()
                .replace("|", "_")
                .replace("\n", " ")
                .replace("\r", " ");

        tvServerStatus.setText(
                "Online Paint - " + serverName
        );

        tvUsers.setText("在線使用者：0");
        tvChatMessages.setText("");

        btnClearAll.setOnClickListener(v -> clearAllCanvas());

        btnCloseServer.setOnClickListener(v -> finish());

        refreshUsers();
        startServer();
    }

    // 啟動 TCP Server
    private void startServer() {
        running = true;

        new Thread(() -> {
            try (ServerSocket listener = new ServerSocket(PORT)) {

                serverSocket = listener;

                if (!running) {
                    return;
                }

                showEvent("Server 啟動成功，Port " + PORT);

                while (running) {
                    Socket socket = listener.accept();

                    if (!running) {
                        socket.close();
                        break;
                    }

                    new Thread(() ->
                            handleClient(socket)
                    ).start();
                }

            } catch (IOException e) {
                if (running) {
                    showEvent(
                            "Server 錯誤：" + e.getMessage()
                    );
                }
            } finally {
                running = false;
            }
        }).start();
    }

    // 分配目前未使用的畫筆顏色
    // 必須在 stateLock 內呼叫
    private int assignColorLocked() {

        for (int i = 0; i < COLORS.length; i++) {

            int candidate = COLORS[
                    (nextColorIndex + i) % COLORS.length
                    ];

            boolean used = false;

            for (ClientPeer peer : clients) {
                if (peer.color == candidate) {
                    used = true;
                    break;
                }
            }

            if (!used) {
                nextColorIndex =
                        (nextColorIndex + i + 1) % COLORS.length;

                return candidate;
            }
        }

        // 超過六位使用者時，額外產生顏色
        int candidate;
        boolean used;

        do {
            float hue =
                    (nextColorIndex * 137.508f) % 360f;

            nextColorIndex++;

            candidate = Color.HSVToColor(
                    new float[]{hue, 0.85f, 0.90f}
            );

            used = false;

            for (ClientPeer peer : clients) {
                if (peer.color == candidate) {
                    used = true;
                    break;
                }
            }

        } while (used);

        return candidate;
    }

    // 處理每位 Client 的訊息
    private void handleClient(Socket socket) {

        ClientPeer peer = null;

        try (
                Socket connection = socket;

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                );

                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(
                                connection.getOutputStream(),
                                StandardCharsets.UTF_8
                        ),
                        true
                )
        ) {
            String line;

            while (running &&
                    (line = reader.readLine()) != null) {

                // Client 加入畫板
                if (line.startsWith("JOIN|") && peer == null) {

                    String name = line.substring(5)
                            .trim()
                            .replace("|", "_")
                            .replace("\n", " ")
                            .replace("\r", " ");

                    if (name.isEmpty()) {
                        name = "訪客";
                    }

                    if (name.length() > 24) {
                        name = name.substring(0, 24);
                    }

                    boolean duplicate = false;

                    synchronized (stateLock) {

                        for (ClientPeer existing : clients) {
                            if (existing.name.equals(name)) {
                                duplicate = true;
                                break;
                            }
                        }

                        if (!duplicate) {

                            int color = assignColorLocked();

                            peer = new ClientPeer(
                                    connection,
                                    writer,
                                    name,
                                    color
                            );

                            // 將畫筆顏色通知新 Client
                            peer.send("COLOR|" + color);

                            // 補發目前畫布上的所有筆跡
                            for (StrokeRecord record :
                                    strokes.values()) {

                                peer.send(
                                        "STROKE|" + record.owner +
                                                "|" + record.id +
                                                "|" + record.color +
                                                "|" + record.points
                                );
                            }

                            clients.add(peer);

                            broadcastLocked(
                                    "SYSTEM|" + name + " 已加入畫板"
                            );
                        }
                    }

                    if (duplicate) {
                        writer.println("REJECT|名稱已被使用");
                        break;
                    }

                    showEvent(name + " 已加入");
                    refreshUsers();
                }

                // 接收一整筆畫線
                else if (line.startsWith("DRAW|")
                        && peer != null) {

                    String[] parts = line.split("\\|", 3);

                    if (parts.length != 3) {
                        continue;
                    }

                    String id = parts[1];
                    String points = parts[2];

                    if (!id.matches("[A-Za-z0-9-]{1,80}")
                            || !PaintCanvasView.isValidPoints(points)) {
                        continue;
                    }

                    String owner = peer.name;
                    int color = peer.color;
                    boolean added = false;

                    synchronized (stateLock) {

                        if (!strokes.containsKey(id)) {

                            strokes.put(
                                    id,
                                    new StrokeRecord(
                                            id,
                                            owner,
                                            color,
                                            points
                                    )
                            );

                            broadcastLocked(
                                    "STROKE|" + owner +
                                            "|" + id +
                                            "|" + color +
                                            "|" + points
                            );

                            runOnUiThread(() ->
                                    paintCanvas.addStroke(
                                            owner,
                                            id,
                                            color,
                                            points
                                    )
                            );

                            added = true;
                        }
                    }

                    if (added) {
                        showEvent(owner + " 畫了一筆");
                    }
                }

                // 撤銷自己的指定筆跡
                else if (line.startsWith("UNDO|")
                        && peer != null) {

                    String id = line.substring(5);
                    boolean removed = false;

                    synchronized (stateLock) {

                        StrokeRecord record = strokes.get(id);

                        if (record != null &&
                                record.owner.equals(peer.name)) {

                            strokes.remove(id);

                            broadcastLocked("REMOVE|" + id);

                            runOnUiThread(() ->
                                    paintCanvas.removeStroke(id)
                            );

                            removed = true;
                        }
                    }

                    if (removed) {
                        showEvent(peer.name + " 撤銷了一筆");
                    }
                }

                // Client 清除自己的全部筆跡
                else if (line.equals("CLEAR_MINE")
                        && peer != null) {

                    String owner = peer.name;

                    synchronized (stateLock) {

                        removeOwnerRecordsLocked(owner);

                        broadcastLocked(
                                "CLEAR_OWNER|" + owner
                        );

                        runOnUiThread(() ->
                                paintCanvas.clearOwnerStrokes(owner)
                        );
                    }

                    showEvent(owner + " 清除了自己的筆跡");
                }
            }

        } catch (IOException e) {

            if (running && peer != null) {
                showEvent(peer.name + " 連線中斷");
            }

        } finally {

            if (peer != null) {

                boolean removed;

                synchronized (stateLock) {
                    removed = clients.remove(peer);

                    if (removed) {
                        removeOwnerRecordsLocked(peer.name);

                        if (running) {
                            broadcastLocked(
                                    "CLEAR_OWNER|" + peer.name
                            );

                            broadcastLocked(
                                    "SYSTEM|" + peer.name +
                                            " 已離開畫板"
                            );
                        }

                        String owner = peer.name;

                        runOnUiThread(() ->
                                paintCanvas.clearOwnerStrokes(owner)
                        );
                    }
                }

                if (removed) {
                    showEvent(peer.name + " 已離開");
                    refreshUsers();
                }
            }
        }
    }

    // Server 清除指定 Client 的筆跡
    private void clearOwnerStrokes(String owner) {

        new Thread(() -> {

            synchronized (stateLock) {

                removeOwnerRecordsLocked(owner);

                broadcastLocked(
                        "CLEAR_OWNER|" + owner
                );

                runOnUiThread(() ->
                        paintCanvas.clearOwnerStrokes(owner)
                );
            }

            showEvent(owner + " 的筆跡已清除");

        }).start();
    }

    // Server 清除整張畫布
    private void clearAllCanvas() {

        new Thread(() -> {

            synchronized (stateLock) {

                strokes.clear();

                broadcastLocked("CLEAR_ALL");

                runOnUiThread(() ->
                        paintCanvas.clearAllStrokes()
                );
            }

            showEvent("Server 已清除整張畫布");

        }).start();
    }

    // Server 踢出指定 Client
    private void kickClient(ClientPeer target) {

        new Thread(() -> {

            boolean removed;

            synchronized (stateLock) {

                removed = clients.remove(target);

                if (removed) {

                    target.send(
                            "KICK|你已被 Server 踢出畫板"
                    );

                    removeOwnerRecordsLocked(target.name);

                    broadcastLocked(
                            "CLEAR_OWNER|" + target.name
                    );

                    broadcastLocked(
                            "SYSTEM|" + target.name +
                                    " 已被踢出畫板"
                    );

                    runOnUiThread(() ->
                            paintCanvas.clearOwnerStrokes(
                                    target.name
                            )
                    );
                }
            }

            if (!removed) {
                return;
            }

            try {
                target.socket.close();
            } catch (IOException ignored) {
            }

            showEvent(target.name + " 已被踢出");
            refreshUsers();

        }).start();
    }

    // 僅供持有 stateLock 的方法呼叫
    private void broadcastLocked(String line) {

        for (ClientPeer peer : clients) {
            peer.send(line);
        }
    }

    // 僅供持有 stateLock 的方法呼叫
    private void removeOwnerRecordsLocked(String owner) {

        Iterator<StrokeRecord> iterator =
                strokes.values().iterator();

        while (iterator.hasNext()) {

            if (iterator.next().owner.equals(owner)) {
                iterator.remove();
            }
        }
    }

    // 更新在線使用者與管理按鈕
    private void refreshUsers() {

        List<ClientPeer> snapshot;

        synchronized (stateLock) {
            snapshot = new ArrayList<>(clients);
        }

        runOnUiThread(() -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            tvUsers.setText(
                    "在線使用者：" + snapshot.size()
            );

            userList.removeAllViews();

            for (ClientPeer peer : snapshot) {
                addUserRow(peer);
            }
        });
    }

    private int dp(int value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    // 動態建立每個 Client 的管理欄位
    private void addUserRow(ClientPeer peer) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(6), dp(4), dp(6), dp(6));

        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);

        View colorSwatch = new View(this);

        GradientDrawable colorBackground =
                new GradientDrawable();

        colorBackground.setColor(peer.color);
        colorBackground.setCornerRadius(dp(8));

        colorSwatch.setBackground(colorBackground);

        infoRow.addView(
                colorSwatch,
                new LinearLayout.LayoutParams(
                        dp(16), dp(16)
                )
        );

        TextView info = new TextView(this);

        String hex = String.format(
                Locale.US,
                "#%06X",
                peer.color & 0xFFFFFF
        );

        info.setText(
                peer.name +
                        " | IP：" + peer.ip +
                        " | " + hex
        );

        info.setTextSize(13f);
        info.setPadding(dp(8), 0, 0, 0);

        infoRow.addView(info);

        card.addView(infoRow);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnClearUser = new Button(this);
        btnClearUser.setText("清除筆跡");
        btnClearUser.setTextSize(12f);

        btnClearUser.setOnClickListener(v ->
                clearOwnerStrokes(peer.name)
        );

        Button btnKickUser = new Button(this);
        btnKickUser.setText("踢出");
        btnKickUser.setTextSize(12f);

        btnKickUser.setOnClickListener(v ->
                kickClient(peer)
        );

        buttonRow.addView(
                btnClearUser,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        buttonRow.addView(
                btnKickUser,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                )
        );

        card.addView(buttonRow);

        userList.addView(card);
    }

    // 顯示最近四筆事件
    private void showEvent(String message) {

        runOnUiThread(() -> {

            if (isFinishing() || isDestroyed()) {
                return;
            }

            events.addLast(message);

            while (events.size() > 4) {
                events.removeFirst();
            }

            StringBuilder text = new StringBuilder();

            for (String event : events) {

                if (text.length() > 0) {
                    text.append("\n");
                }

                text.append(event);
            }

            tvChatMessages.setText(text.toString());
        });
    }

    // 關閉 Server 並通知所有 Client
    @Override
    protected void onDestroy() {

        running = false;

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        List<ClientPeer> snapshot;

        synchronized (stateLock) {
            snapshot = new ArrayList<>(clients);
            clients.clear();
            strokes.clear();
        }

        // 網路 I/O 放在背景執行緒
        new Thread(() -> {

            for (ClientPeer peer : snapshot) {
                peer.send("SERVER_CLOSED");
            }

            for (ClientPeer peer : snapshot) {
                try {
                    peer.socket.close();
                } catch (IOException ignored) {
                }
            }

        }).start();

        super.onDestroy();
    }
}
