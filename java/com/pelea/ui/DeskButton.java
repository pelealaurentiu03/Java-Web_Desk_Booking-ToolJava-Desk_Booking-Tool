package com.pelea.ui;

import com.pelea.database.DatabaseManager;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.shared.Tooltip;
import java.time.LocalDate;
import java.time.LocalTime;

public class DeskButton extends Button {

    private static final long serialVersionUID = 1L;

    private final String deskId;
    private final String currentUserEmail;
    private final String currentUserName;
    private final LocalDate viewingDate;
    private Runnable onDataChanged;

    private boolean isBooked = false;
    private String bookedByEmail = null;
    private String bookedByName = null;
    private String bookedByPhotoUrl = null;
    private String bookingInterval = null;
    private String bookingDate = null;

    public DeskButton(String deskId, String currentUserEmail, String currentUserName, String currentUserPhotoUrl, LocalDate viewingDate, Runnable onDataChanged) {
        this.deskId = deskId;
        this.currentUserEmail = currentUserEmail;
        this.currentUserName = currentUserName;
        this.viewingDate = viewingDate;
        this.onDataChanged = onDataChanged;

        getStyle().set("cursor", "pointer");
        getStyle().set("padding", "0");

        if (viewingDate.isBefore(LocalDate.now())) {
            setTooltipText("You can't book in the past. Date: " + LocalDate.now());
        }

        //Click logic
        addClickListener(e -> {
            if (viewingDate.isBefore(LocalDate.now())) return;

            if (isBooked) {
                if (bookedByEmail != null && bookedByEmail.equals(currentUserEmail)) {
                    showCancelDialog();
                }
                return;
            }

            BookingDialog dialog = new BookingDialog(
                deskId, currentUserEmail, currentUserName, currentUserPhotoUrl, viewingDate,
                (date, interval) -> {
                    this.markAsBooked(currentUserEmail, currentUserName, currentUserPhotoUrl, date, interval);
                    MainView.sendLogToServer("ACTION: Add Booking | DESK: " + deskId + " | USER: " + currentUserName + " | DATE: " + date + " | HOURS: " + interval);
                    if (onDataChanged != null) onDataChanged.run();
                }
            );
            dialog.open();
        });
    }

    public void setAvailabilityFromData(BookingInfo info, String currentTimeFilter) {
        if (info != null && info.getInterval() != null) {
            try {
                String[] parts = info.getInterval().split(" - ");
                if (parts.length == 2) {
                    LocalTime start = LocalTime.parse(parts[0]);
                    LocalTime end = LocalTime.parse(parts[1]);
                    LocalTime filterTime = LocalTime.parse(currentTimeFilter);

                    if (!filterTime.isBefore(start) && filterTime.isBefore(end)) {
                        this.markAsBooked(info.getEmail(), info.getName(), info.getPhoto(), info.getDate(), info.getInterval());
                        return;
                    }
                }
            } catch (Exception e) {
                System.out.println("Error parsing time: " + e.getMessage());
            }
        }
        resetToFree();
    }

    public void markAsBooked(String email, String name, String photoUrl, String date, String interval) {
        this.isBooked = true;
        this.bookedByEmail = email;
        this.bookedByName = name;
        this.bookedByPhotoUrl = photoUrl;
        this.bookingDate = date;
        this.bookingInterval = interval;

        setText("");
        Avatar avatar = new Avatar(name);
        if (photoUrl != null && !photoUrl.isEmpty()) {
            avatar.setImage(photoUrl);
        }
        avatar.setWidth("80%");
        avatar.setHeight("80%");
        setIcon(avatar);

        getStyle().set("background-color", "white");
        getStyle().set("border", "2px solid #d93025");
        
        setTooltipText("ID: " + deskId + "\nReserved by: " + name + "\nHours: " + interval);
    }

    private void resetToFree() {
        this.isBooked = false;
        this.bookedByEmail = null;
        this.bookedByName = null;
        this.bookedByPhotoUrl = null;

        setIcon(null);
        setText(deskId);

        getStyle().set("background-color", "rgba(40, 167, 69, 0.15)");
        getStyle().set("border", "1px solid #28a745");
        getStyle().set("color", "#28a745");
        getStyle().set("font-weight", "bold");
        
        String widthStr = getStyle().get("width");
        String heightStr = getStyle().get("height");
        if (widthStr != null && heightStr != null) {
        	int w = (int) Double.parseDouble(widthStr.replace("px", "").trim());
        	int h = (int) Double.parseDouble(heightStr.replace("px", "").trim());
            if (h > w) {
                getStyle().set("display", "flex");
                getStyle().set("align-items", "center");
                getStyle().set("justify-content", "center");
                getElement().executeJs("this.innerHTML = '<div style=\"transform: rotate(-90deg);\">' + this.innerText + '</div>'");
            }
        }

        setTooltipText(viewingDate.isBefore(LocalDate.now()) ? "Locked (Past)" : deskId + " (Free)");
    }

    private void showCancelDialog() {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Cancel Booking");
        dialog.setText("Do you want to cancel the booking for desk " + deskId + " on " + bookingDate + "?");

        dialog.setCancelable(true);
        dialog.setCancelText("No");
        
        dialog.setConfirmText("Delete");
        dialog.setConfirmButtonTheme("error primary");

        dialog.addConfirmListener(e -> {
            DatabaseManager.getInstance().deleteBooking(deskId, bookingDate, currentUserEmail);
            MainView.sendLogToServer("ACTION: Cancel (Map) | DESK: " + deskId + " | USER: " + currentUserName);
            resetToFree();
            if (onDataChanged != null) onDataChanged.run();
        });

        dialog.open();
    }

    public void removeHighlight() {
        getStyle().remove("border");
        getStyle().remove("box-shadow");
        if (isBooked) {
            getStyle().set("border", "2px solid #d93025");
        } else {
            getStyle().set("border", "1px solid #28a745");
        }
    }

    public boolean isBooked() { return isBooked; }
    public String getBookedByName() { return bookedByName; }
    public String getDeskId() { return deskId; }
}