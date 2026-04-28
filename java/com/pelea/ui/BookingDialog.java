package com.pelea.ui;

import com.pelea.database.DatabaseManager;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;

public class BookingDialog extends Dialog {

    private final String deskId;
    private final String userEmail;
    private final String userName;
    private final String userPhotoUrl;
    private final BiConsumer<String, String> onBookingSuccess;

    public BookingDialog(String deskId, String userEmail, String userName, String userPhotoUrl, 
                         LocalDate initialDate, BiConsumer<String, String> onBookingSuccess) {
        this.deskId = deskId;
        this.userEmail = userEmail;
        this.userName = userName;
        this.userPhotoUrl = userPhotoUrl;
        this.onBookingSuccess = onBookingSuccess;

        setHeaderTitle("Desk booking: " + deskId);
        setModal(true);
        setDraggable(true);

        H3 title = new H3("Book the desk no. " + deskId);

        //Radio buttons
        RadioButtonGroup<String> bookingType = new RadioButtonGroup<>();
        bookingType.setLabel("Booking type");
        bookingType.setItems("Single day", "More days");
        bookingType.setValue("Single day");

        //Date selectors
        DatePicker datePickerStart = new DatePicker("Date", initialDate);
        datePickerStart.setMin(LocalDate.now().minusDays(30)); //30 days limit

        DatePicker datePickerEnd = new DatePicker("Until");
        datePickerEnd.setVisible(false);

        //Show/hide logic for multiple days
        bookingType.addValueChangeListener(event -> {
            boolean isMultiple = event.getValue().equals("More days");
            datePickerEnd.setVisible(isMultiple);
            datePickerStart.setLabel(isMultiple ? "From" : "Date");
            if (isMultiple && datePickerStart.getValue() != null) {
                datePickerEnd.setValue(datePickerStart.getValue().plusDays(1));
            }
        });

        //Hour selectors
        List<String> hours = new ArrayList<>();
        for (int i = 7; i <= 20; i++) {
            hours.add(String.format("%02d:00", i));
        }

        ComboBox<String> startHour = new ComboBox<>("Start hour", hours);
        startHour.setValue("08:00");
        ComboBox<String> endHour = new ComboBox<>("End hour", hours);
        endHour.setValue("16:00");

        HorizontalLayout timeBox = new HorizontalLayout(startHour, new Span("->"), endHour);
        timeBox.setAlignItems(Alignment.BASELINE);

        //Buttons
        Button btnSave = new Button("Confirm the booking");
        btnSave.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        
        Button btnCancel = new Button("Cancel", e -> close());
        btnCancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

        //Save logic
        btnSave.addClickListener(e -> {
            LocalDate startDate = datePickerStart.getValue();
            String sHour = startHour.getValue();
            String eHour = endHour.getValue();

            //Validations
            if (startDate == null) {
                showWebAlert("Please select the start date!");
                return;
            }
            if (sHour.compareTo(eHour) >= 0) {
                showWebAlert("The start hour can't be after the end hour!");
                return;
            }

            String intervalText = sHour + " - " + eHour;
            boolean allSaved = true;

            if (bookingType.getValue().equals("More days")) {
                LocalDate endDate = datePickerEnd.getValue();
                if (endDate == null || endDate.isBefore(startDate)) {
                    showWebAlert("Invalid end date!");
                    return;
                }

                for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                    boolean success = DatabaseManager.getInstance().saveBooking(
                            this.deskId, this.userEmail, this.userName, this.userPhotoUrl,
                            date.toString(), intervalText
                    );
                    if (!success) allSaved = false;
                }
            } else {
                allSaved = DatabaseManager.getInstance().saveBooking(
                        this.deskId, this.userEmail, this.userName, this.userPhotoUrl,
                        startDate.toString(), intervalText
                );
            }

            if (allSaved) {
                if (onBookingSuccess != null) {
                    onBookingSuccess.accept(startDate.toString(), intervalText);
                }
                close();
            } else {
                showWebAlert("Some bookings could not be saved to MongoDB.");
            }
        });

        //Final layout
        VerticalLayout layout = new VerticalLayout(title, bookingType, datePickerStart, datePickerEnd, timeBox);
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setAlignItems(Alignment.CENTER);

        add(layout);
        getFooter().add(btnCancel, btnSave);
    }

    private void showWebAlert(String message) {
        Notification notification = Notification.show(message, 3000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}