package com.pelea.ui;

import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class LogServer {

    private static final String LOG_FOLDER = "server_logs";
    private static final int RETENTION_DAYS = 30;
    private static final int PORT = 5000;
    
    private ServerSocket serverSocket;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean running = true;

    @PostConstruct
    public void startLogServer() {
        executorService.submit(() -> {
            cleanOldLogs();
            System.out.println("LOG SERVER STARTED ON PORT " + PORT);
            
            try {
                serverSocket = new ServerSocket(PORT);
                while (running) {
                    try (Socket clientSocket = serverSocket.accept();
                         BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
                        
                        String message = in.readLine();
                        if (message != null && !message.equals("EXIT")) {
                            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                            String logEntry = "[" + time + "] " + message;
                            
                            System.out.println("Log recorded: " + logEntry);
                            saveToFile(logEntry);
                        }
                    } catch (Exception e) {
                        if (running) System.err.println("Log Server Error: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                if (running) System.err.println("Could not start Log Server on port 5000: " + e.getMessage());
            }
        });
    }

    private void saveToFile(String logEntry) {
        try {
            File directory = new File(LOG_FOLDER);
            if (!directory.exists()) directory.mkdir();

            String filename = "log_" + LocalDate.now().toString() + ".txt";
            File logFile = new File(directory, filename);

            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(logEntry);
            }
        } catch (Exception e) {
            System.err.println("Failed to write to file: " + e.getMessage());
        }
    }

    private void cleanOldLogs() {
        File directory = new File(LOG_FOLDER);
        if (!directory.exists()) return;

        File[] files = directory.listFiles();
        if (files == null) return;

        long currentTime = System.currentTimeMillis();
        long retentionTime = (long) RETENTION_DAYS * 24 * 60 * 60 * 1000;

        for (File file : files) {
            if (currentTime - file.lastModified() > retentionTime) {
                file.delete();
            }
        }
    }

    @PreDestroy
    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
            executorService.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}