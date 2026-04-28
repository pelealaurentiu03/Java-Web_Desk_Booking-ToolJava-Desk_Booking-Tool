package com.pelea.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.IndexOptions;
import io.github.cdimascio.dotenv.Dotenv;
import org.bson.Document;
import com.pelea.ui.BookingInfo; 
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class DatabaseManager {

    private static DatabaseManager instance;
    private MongoDatabase database;
    private MongoCollection<Document> usersCollection;
    private MongoCollection<Document> bookingsCollection;

    private DatabaseManager() {
        try {
            Dotenv dotenv = Dotenv.load();
            String connectionString = dotenv.get("MONGO_URI");
            String dbName = dotenv.get("DB_NAME");

            MongoClient mongoClient = MongoClients.create(connectionString);
            database = mongoClient.getDatabase(dbName);
            
            usersCollection = database.getCollection("users");
            bookingsCollection = database.getCollection("bookings");
            
            System.out.println("OK: Connected to MongoDB: " + dbName);
            
            // Verificare și creare indexuri pentru viteză
            try {
                usersCollection.createIndex(Indexes.ascending("email"), new IndexOptions().unique(true));
                bookingsCollection.createIndex(Indexes.ascending("booking_date"));
                bookingsCollection.createIndex(Indexes.compoundIndex(
                    Indexes.ascending("desk_id"), 
                    Indexes.ascending("booking_date")
                ));
                System.out.println("OK: Database indexes verified.");
            } catch (Exception idxEx) {
                System.out.println("Warning: Could not verify indexes: " + idxEx.getMessage());
            }
            
            anonymizeOldBookings();
        } catch (Exception e) {
            System.err.println("ERROR: MongoDB Connection failed: " + e.getMessage());
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }
    
    private void anonymizeOldBookings() {
        try {
            if (bookingsCollection == null) return;

            String cutoffDate = LocalDate.now().minusDays(30).toString();

            var query = Filters.and(
                Filters.lt("booking_date", cutoffDate),
                Filters.ne("user_name", "Employee")
            );

            var update = Updates.combine(
                Updates.set("user_name", "Employee"),
                Updates.set("user_email", "Employee@oeslTM.local"),
                Updates.set("user_photo", "")
            );

            var result = bookingsCollection.updateMany(query, update);

            if (result.getModifiedCount() > 0) {
                System.out.println("DATABASE CLEANUP: Anonymized " + result.getModifiedCount() + " bookings.");
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not anonymize old bookings: " + e.getMessage());
        }
    }

    public boolean saveBooking(String deskId, String email, String name, String photo, String date, String interval) {
        try {
            Document booking = new Document()
                    .append("desk_id", deskId)
                    .append("user_email", email)
                    .append("user_name", name)
                    .append("user_photo", photo)
                    .append("booking_date", date)
                    .append("booking_interval", interval)
                    .append("createdAt", new Date());

            bookingsCollection.insertOne(booking);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public BookingInfo getBooking(String deskId, LocalDate date) {
        if (bookingsCollection == null) return null;

        Document query = new Document("desk_id", deskId)
                                .append("booking_date", date.toString());
        
        Document result = bookingsCollection.find(query).first();

        if (result != null) {
            return new BookingInfo(
                result.getString("desk_id"), 
                result.getString("user_email"),
                result.getString("user_name"),
                result.getString("user_photo"),
                result.getString("booking_date"),
                result.getString("booking_interval")
            );
        }
        return null;
    }
    
    public List<BookingInfo> getBookingsForUser(String email) {
        List<BookingInfo> userBookings = new ArrayList<>();
        if (bookingsCollection == null) return userBookings;

        bookingsCollection.find(Filters.eq("user_email", email))
                .sort(Sorts.ascending("booking_date"))
                .forEach(doc -> {
                    userBookings.add(new BookingInfo(
                        doc.getString("desk_id"),
                        doc.getString("user_email"),
                        doc.getString("user_name"),
                        doc.getString("user_photo"),
                        doc.getString("booking_date"),
                        doc.getString("booking_interval")
                    ));
                });
        return userBookings;
    }
    
    public void deleteBooking(String deskId, String date, String userEmail) {
        if (bookingsCollection == null) return;
        bookingsCollection.deleteOne(Filters.and(
            Filters.eq("desk_id", deskId),
            Filters.eq("booking_date", date),
            Filters.eq("user_email", userEmail)
        ));
    }

    public void saveUser(String email, String name) {
        if (usersCollection == null) return;
        Document query = new Document("email", email);
        Document update = new Document("$set", new Document()
                .append("email", email)
                .append("name", name)
                .append("lastLogin", new Date()))
                .append("$setOnInsert", new Document("joinedAt", new Date()));
        usersCollection.updateOne(query, update, new UpdateOptions().upsert(true));
    }
    
    public List<String> getAllBookedNamesForDate(String date) {
        List<String> names = new ArrayList<>();
        if (bookingsCollection == null) return names;

        bookingsCollection.find(Filters.eq("booking_date", date))
                .forEach(doc -> {
                    String name = doc.getString("user_name");
                    if (name != null && !names.contains(name)) {
                        names.add(name);
                    }
                });
        return names;
    }
    
    public Map<String, BookingInfo> getAllBookingsForDateAsMap(String date) {
        Map<String, BookingInfo> map = new HashMap<>();
        if (bookingsCollection == null) return map;

        bookingsCollection.find(Filters.eq("booking_date", date))
                .forEach(doc -> {
                    String deskId = doc.getString("desk_id");
                    BookingInfo info = new BookingInfo(
                        deskId,
                        doc.getString("user_email"),
                        doc.getString("user_name"),
                        doc.getString("user_photo"),
                        doc.getString("booking_date"),
                        doc.getString("booking_interval")
                    );
                    map.put(deskId, info);
                });
        return map;
    }
    
    public String getUserRole(String email) {
        try {
            Document user = usersCollection.find(new Document("email", email)).first();
            if (user != null && user.containsKey("role")) {
                return user.getString("role");
            }
        } catch (Exception e) {
            System.out.println("Error reading user role: " + e.getMessage());
        }
        return "user";
    }
    
    public List<BookingInfo> getBookingsForReport(LocalDate startDate, LocalDate endDate) {
        List<BookingInfo> reportData = new ArrayList<>();
        if (bookingsCollection == null) return reportData;

        bookingsCollection.find(Filters.and(
                Filters.gte("booking_date", startDate.toString()),
                Filters.lte("booking_date", endDate.toString())
        )).sort(Sorts.ascending("booking_date"))
        .forEach(doc -> {
            reportData.add(new BookingInfo(
                doc.getString("desk_id"),
                doc.getString("user_email"),
                doc.getString("user_name"),
                doc.getString("user_photo"),
                doc.getString("booking_date"),
                doc.getString("booking_interval")
            ));
        });
        return reportData;
    }
}