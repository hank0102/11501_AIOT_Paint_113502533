# AIOT Socket 多人聊天室

- **學號**：113502533
- **課程作業**：11501 AIOT Socket Chatroom
- **開發環境**：Android Studio、Java、Android SDK
- **專案內容**：Android TCP Socket 多人聊天室（1 個 Server、2 個以上 Client）

## 功能

- Server 可自訂名稱，監聽 TCP Port `8080`。
- Client 可輸入名稱、Server IP 與 Port，連線至聊天室。
- 支援多位 Client 同時加入、互傳訊息，以及 Server 發送訊息。
- 顯示成員加入與離開通知。
- 聊天訊息顯示由 Server 統一產生的時間戳記（`HH:mm:ss`，台灣時間）。
- Client 可透過「離開聊天室」按鈕正常中斷連線。

## 專案結構

```text
AIOTChat/
├── app/       # Server Android App
├── client/    # Client Android App
├── gradle/    # Gradle Wrapper 設定
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
└── README.md
```

### 主要程式檔案

| 檔案 | 用途 |
| --- | --- |
| `app/src/main/java/com/example/aiotchat/MainActivity.java` | Server 首頁、輸入 Server 名稱 |
| `app/src/main/java/com/example/aiotchat/ServerChatActivity.java` | 啟動 ServerSocket、處理 Client 連線與訊息廣播 |
| `app/src/main/res/layout/activity_server_chat.xml` | Server 聊天室介面 |
| `client/src/main/java/com/example/aiotchat/client/MainActivity.java` | Client 首頁、輸入名稱/IP/Port |
| `client/src/main/java/com/example/aiotchat/client/ClientChatActivity.java` | 建立 Socket 連線、傳送與接收訊息、離開聊天室 |
| `client/src/main/res/layout/activity_client_chat.xml` | Client 聊天室介面 |

## 執行方式

1. 使用 Android Studio 開啟此專案，等待 Gradle Sync 完成。
2. 在第一台模擬器執行 `app`（Server），輸入名稱並建立聊天室；Server 監聽 `8080`。
3. 在另外兩台模擬器分別執行 `client`，輸入不同的 Client 名稱，連線到 Server。
4. 測試 Client 相互傳訊、Server 發訊、時間戳記，以及 Client 離開通知。

### 跨模擬器連線（ADB Forward）

先使用 `adb devices` 確認 **Server 所在模擬器的編號**。以下以 `emulator-5554` 為例：

```powershell
adb -s emulator-5554 forward tcp:18080 tcp:8080
adb forward --list
```

若轉發成功，兩台 Client 均可使用：

- Server IP：`10.0.2.2`
- Port：`18080`

**注意**：`emulator-5554` 只是範例，需替換成實際執行 Server 的模擬器編號；若使用不同的轉發埠，也要同步修改 Client 的 Port。若 Client 與 Server 安裝在**同一台**模擬器，可使用 `127.0.0.1:8080`。

## Demo 影片

Demo 影片連結：https://youtu.be/Y5WkosALI2s?si=sfVNE57qw-yMl1mk。
