package com.pelea.ui;

import com.pelea.database.DatabaseManager;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinSession;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Route("")
@com.vaadin.flow.component.dependency.NpmPackage(value = "@polymer/polymer", version = "3.5.1")
public class MainView extends AppLayout implements BeforeEnterObserver {

    private static final long serialVersionUID = 1L;

    private String userEmail;
    private String userName;
    private String userPhotoUrl;
    private String userRole;

    private Div mapPane;
    private Div mapWrapper;
    private VerticalLayout bookingsList;
    private TextField searchField;
    private LocalDate selectedDate = LocalDate.now();
    private String selectedTime = "08:00";
    private int currentLevel = 1;
    private Map<String, BookingInfo> currentBookingsMap;
    private DeskButton currentHighlightedBtn = null;

    private final ScheduledExecutorService uiScheduler = Executors.newSingleThreadScheduledExecutor();

    public MainView() {
    }

    @Override
    protected void onAttach(com.vaadin.flow.component.AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        
        applyResponsiveScale();
        
        attachEvent.getUI().getPage().addBrowserWindowResizeListener(event -> {
            applyResponsiveScale();
        });
    }
    
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        userEmail = (String) VaadinSession.getCurrent().getAttribute("email");
        if (userEmail == null) {
            event.rerouteTo(LoginView.class);
            return;
        }
        userName = (String) VaadinSession.getCurrent().getAttribute("name");
        userPhotoUrl = (String) VaadinSession.getCurrent().getAttribute("photo");
        userRole = DatabaseManager.getInstance().getUserRole(userEmail);

        sendLogToServer("ACTION: User Login | ROLE: " + userRole + " | USER: " + userName + " | EMAIL: " + userEmail);
        
        buildMainLayout();
        startMidnightRefreshTimer();
    }

    private void buildMainLayout() {
    	searchField = new TextField(); 
        searchField.setPlaceholder("Find colleague...");
        
        // HEADER
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setPadding(true);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        searchField.getStyle().set("flex-grow", "1");
        searchField.getStyle().set("max-width", "400px");
        header.getStyle().set("background-color", "#fff");
        header.getStyle().set("box-shadow", "0 2px 5px rgba(0,0,0,0.1)");

        // Avatar & Welcome
        HorizontalLayout userInfo = new HorizontalLayout();
        userInfo.setAlignItems(FlexComponent.Alignment.CENTER);
        if (userPhotoUrl != null) {
            Image avatar = new Image(userPhotoUrl, "Avatar");
            avatar.setWidth("40px");
            avatar.setHeight("40px");
            avatar.getStyle().set("border-radius", "50%");
            userInfo.add(avatar);
        }
        userInfo.add(new Span("Hello, " + userName + "!"));

        // Date & Time
        DatePicker datePicker = new DatePicker("Select Date", selectedDate);
        
        if (!"admin".equals(userRole)) {
            LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
            datePicker.setMin(thirtyDaysAgo);}
        
        datePicker.addValueChangeListener(e -> {
            selectedDate = e.getValue();
            sendLogToServer("ACTION: Date Changed | NEW DATE: " + selectedDate + " | USER: " + userName);
            loadMap();
        });

        List<String> hours = new ArrayList<>();
        for (int i = 7; i <= 20; i++) hours.add(String.format("%02d:00", i));
        ComboBox<String> timeSelector = new ComboBox<>("Time", hours);
        timeSelector.setValue(selectedTime);
        timeSelector.addValueChangeListener(e -> {
            selectedTime = e.getValue();
            loadMap();
        });

        // Levels
        Button btnL1 = new Button("LEVEL 1", e -> { currentLevel = 1; loadMap(); });
        Button btnL2 = new Button("LEVEL 2", e -> { currentLevel = 2; loadMap(); });

        // Search
        Button btnSearch = new Button(VaadinIcon.SEARCH.create(), e -> performSearch(searchField.getValue()));

        header.add(userInfo, new Span("|"), datePicker, timeSelector, new Span("|"), btnL1, btnL2, new Span("|"), searchField, btnSearch);

        if ("admin".equals(userRole)) {
            Button btnExport = new Button("Export Report", VaadinIcon.FILE_TEXT.create());
            btnExport.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            btnExport.addClickListener(e -> showExportDialog());
            header.add(new Span("|"), btnExport);
        }

        addToNavbar(header);

        VerticalLayout drawer = new VerticalLayout();
        drawer.add(new H3("My Bookings"));
        bookingsList = new VerticalLayout();
        bookingsList.setPadding(false);
        drawer.add(bookingsList);
        addToDrawer(drawer);

        // MAP AREA
        mapPane = new Div();
        mapPane.setSizeFull();
        mapPane.getStyle().set("position", "relative");
        mapPane.getStyle().set("background-color", "#f0f0f0");
        mapPane.setSizeFull(); 
        mapPane.getStyle().set("overflow", "hidden");
        
        mapWrapper = new Div();
        mapWrapper.getStyle().set("width", "1600px");
        mapWrapper.getStyle().set("height", "850px"); 
        mapWrapper.getStyle().set("position", "absolute");
        
        mapPane.add(mapWrapper);

        setContent(mapPane);
        refreshMyBookings();
        loadMap();
        enableMapPanAndZoom();
    }
    
    private void enableMapPanAndZoom() {
        mapPane.getElement().executeJs(
            "const pane = this;" +
            "const wrapper = pane.firstChild;" +
            "if (!wrapper) return;" +
            "" +
            "pane._scale = 1;" +
            "pane._isDragging = false;" +
            "pane._startX = 0;" +
            "pane._startY = 0;" +
            "pane._translateX = 0;" +
            "pane._translateY = 0;" +
            "" +
            "pane.style.cursor = 'grab';" +
            "wrapper.style.transformOrigin = 'center center';" +
            "wrapper.style.transition = 'transform 0.1s ease-out';" +
            "" +
            "pane.addEventListener('dragstart', function(e) {" +
            "    e.preventDefault();" +
            "});" +
            "pane.addEventListener('wheel', function(e) {" +
            "    e.preventDefault();" +
            "    const zoomIntensity = 0.001;" +
            "    pane._scale -= (e.deltaY * zoomIntensity);" +
            "    pane._scale = Math.min(Math.max(0.4, pane._scale), 3.5);" +
            "    updateTransform();" +
            "}, { passive: false });" +
            "pane.addEventListener('mousedown', function(e) {" +
            "    if (e.target.tagName && e.target.tagName.toLowerCase().includes('button')) return;" +
            "    e.preventDefault();" +
            "    pane._isDragging = true;" +
            "    pane._startX = e.clientX - pane._translateX;" +
            "    pane._startY = e.clientY - pane._translateY;" +
            "    pane.style.cursor = 'grabbing';" +
            "    wrapper.style.transition = 'none';" +
            "});" +
            "pane.addEventListener('mousemove', function(e) {" +
            "    if (!pane._isDragging) return;" +
            "    e.preventDefault();" +
            "    let rawX = e.clientX - pane._startX;" +
            "    let rawY = e.clientY - pane._startY;" +
            "" +
            "    const limitX = 1600 * pane._scale * 0.6;" +
            "    const limitY = 850 * pane._scale * 0.6;" +
            "" +
            "    pane._translateX = Math.max(-limitX, Math.min(limitX, rawX));" +
            "    pane._translateY = Math.max(-limitY, Math.min(limitY, rawY));" +
            "" +
            "    updateTransform();" +
            "});" +
            "" +
            "pane.addEventListener('mouseup', function() {" +
            "    pane._isDragging = false;" +
            "    pane.style.cursor = 'grab';" +
            "});" +
            "" +
            "pane.addEventListener('mouseleave', function() {" +
            "    pane._isDragging = false;" +
            "    pane.style.cursor = 'grab';" +
            "});" +
            "" +
            "function updateTransform() {" +
            "    wrapper.style.transform = 'translate(' + pane._translateX + 'px, ' + pane._translateY + 'px) scale(' + pane._scale + ')';" +
            "}"
        );
    }

    private void loadMap() {
    	mapWrapper.removeAll();
        Image mapImage = new Image("maps/level" + currentLevel + ".png", "Map");
        mapImage.getStyle().set("display", "block");
        mapImage.getStyle().set("pointer-events", "none"); 
        mapImage.getStyle().set("user-select", "none");
        mapImage.getElement().setAttribute("draggable", "false");
        mapWrapper.add(mapImage);

        currentBookingsMap = DatabaseManager.getInstance().getAllBookingsForDateAsMap(selectedDate.toString());

        if (currentLevel == 1) setupLevel1Desks();
        else setupLevel2Desks();
    }

    private void setupLevel1Desks() {
        addDeskToMap("IT-1", 677, 167, 30, 50);
        addDeskToMap("IT-2", 677, 118, 30, 50);
        addDeskToMap("IT-3", 677, 66, 30, 50);
        addDeskToMap("IT-4", 708, 167, 30, 50);
        addDeskToMap("IT-5", 708, 118, 30, 50);
        addDeskToMap("IT-6", 708, 66, 30, 50);
        addDeskToMap("IT-9", 815, 167, 30, 50);
        addDeskToMap("IT-8", 815, 118, 30, 50);
        addDeskToMap("IT-7", 815, 66, 30, 50);
        addDeskToMap("IT-10", 848, 167, 30, 50);
        addDeskToMap("IT-11", 848, 118, 30, 50);
        addDeskToMap("IT-12", 848, 66, 30, 50);
        addDeskToMap("IT-13", 954, 167, 30, 50);
        addDeskToMap("IT-14", 954, 118, 30, 50);
        addDeskToMap("IT-15", 954, 66, 30, 50);
        addDeskToMap("IT-16", 986, 167, 30, 50);
        addDeskToMap("IT-17", 986, 118, 30, 50);
        addDeskToMap("IT-18", 986, 66, 30, 50);
        addDeskToMap("IT-19", 1095, 95, 55, 30);
        addDeskToMap("IT-20", 1173, 95, 55, 30);
        addDeskToMap("IT-21", 1173, 123, 30, 50);
        addDeskToMap("GM-1", 514, 59, 30, 50);
        addDeskToMap("GM-2", 514, 166, 30, 50);
        addDeskToMap("PM-1", 818, 433, 30, 50);
        addDeskToMap("PM-2", 818, 484, 30, 50);
        addDeskToMap("PM-3", 818, 534, 30, 50);
        addDeskToMap("PM-4", 848, 534, 30, 50);
        addDeskToMap("PM-5", 848, 484, 30, 50);
        addDeskToMap("PM-6", 848, 433, 30, 50);
        addDeskToMap("PM-7", 682, 423, 50, 50);
        addDeskToMap("PM-8", 683, 520, 50, 50);
        addDeskToMap("PM-9", 513, 543, 30, 65);
        addDeskToMap("Sales-1", 994, 433, 30, 50);
        addDeskToMap("Sales-2", 994, 484, 30, 50);
        addDeskToMap("Sales-3", 994, 534, 30, 50);
        addDeskToMap("Sales-4", 1025, 433, 30, 50);
        addDeskToMap("Sales-5", 1025, 484, 30, 50);
        addDeskToMap("Sales-6", 1025, 534, 30, 50);
        addDeskToMap("Sales-7", 1144, 433, 30, 50);
        addDeskToMap("Sales-8", 1144, 484, 30, 50);
        addDeskToMap("Sales-9", 1144, 534, 30, 50);
        addDeskToMap("Sales-10", 1175, 433, 30, 50);
        addDeskToMap("Sales-11", 1175, 484, 30, 50);
        addDeskToMap("Sales-12", 1175, 534, 30, 50);
        addDeskToMap("Q-1", 1272, 74, 30, 50);
        addDeskToMap("Q-2", 1272, 124, 30, 50);
        addDeskToMap("Q-3", 1303, 124, 30, 50);
        addDeskToMap("Q-4", 1303, 74, 30, 50);
        addDeskToMap("Q-5", 1406, 103, 30, 50);
        addDeskToMap("Q-6", 1406, 153, 30, 50);
        addDeskToMap("Q-7", 1406, 205, 30, 50);
        addDeskToMap("Q-8", 1437, 205, 30, 50);
        addDeskToMap("Q-9", 1437, 153, 30, 50);
        addDeskToMap("Q-10", 1437, 103, 30, 50);
        addDeskToMap("Q-11", 1272, 536, 30, 50);
        addDeskToMap("Q-12", 1272, 485, 30, 50);
        addDeskToMap("Q-13", 1303, 485, 30, 50);
        addDeskToMap("Q-14", 1303, 536, 30, 50);
        addDeskToMap("Q-15", 1416, 546, 30, 50);
        addDeskToMap("Q-16", 1416, 495, 30, 50);
        addDeskToMap("Q-17", 1416, 445, 30, 50);
        addDeskToMap("Q-18", 1446, 445, 30, 50);
        addDeskToMap("Q-19", 1446, 495, 30, 50);
        addDeskToMap("Q-20", 1446, 546, 30, 50);
        addMeetingRoomToMap("Felix", 371, 90, 55, 150);
        addMeetingRoomToMap("Mt. Mic", 1148, 224, 45, 35);
        addMeetingRoomToMap("Semenic", 1098, 262, 45, 35);
        addMeetingRoomToMap("Herculane", 513, 423, 50, 50);
    }

    private void setupLevel2Desks() {
        addDeskToMap("PUR", 337, 53, 30, 50);
        addDeskToMap("PUR-1", 483, 169, 25, 50);
        addDeskToMap("PUR-2", 483, 120, 25, 50);
        addDeskToMap("PUR-3", 483, 74, 25, 50);
        addDeskToMap("PUR-4", 512, 74, 25, 50);
        addDeskToMap("PUR-5", 512, 120, 25, 50);
        addDeskToMap("PUR-6", 512, 169, 25, 50);
        addDeskToMap("PUR-7", 625, 74, 25, 50);
        addDeskToMap("PUR-8", 625, 120, 25, 50);
        addDeskToMap("PUR-9", 625, 169, 25, 50);
        addDeskToMap("PUR-10", 653, 169, 25, 50);
        addDeskToMap("PUR-11", 653, 120, 25, 50);
        addDeskToMap("PUR-12", 653, 74, 25, 50);
        addDeskToMap("PUR-13", 741, 74, 25, 50);
        addDeskToMap("PUR-14", 741, 120, 25, 50);
        addDeskToMap("PUR-15", 741, 169, 25, 50);
        addDeskToMap("PUR-16", 770, 169, 25, 50);
        addDeskToMap("PUR-17", 770, 120, 25, 50);
        addDeskToMap("PUR-18", 770, 74, 25, 50);
        addDeskToMap("PUR-19", 868, 62, 25, 50);
        addDeskToMap("PUR-20", 868, 109, 25, 50);
        addDeskToMap("PUR-21", 868, 159, 25, 50);
        addDeskToMap("PUR-22", 896, 159, 25, 50);
        addDeskToMap("PUR-23", 896, 109, 25, 50);
        addDeskToMap("PUR-24", 896, 62, 25, 50);
        addDeskToMap("HR", 1029, 60, 30, 50);
        addDeskToMap("HR-1", 1157, 63, 25, 50);
        addDeskToMap("HR-2", 1185, 63, 25, 50);
        addDeskToMap("HR-3", 1275, 63, 25, 50);
        addDeskToMap("HR-4", 1304, 63, 25, 50);
        addDeskToMap("HR-5", 1194, 166, 50, 25);
        addDeskToMap("HR-6", 1242, 166, 50, 25);
        addDeskToMap("HR-7", 1242, 193, 50, 25);
        addDeskToMap("HR-8", 1194, 193, 50, 25);
        addDeskToMap("FI/CO-A", 451, 401, 30, 50);
        addDeskToMap("FI/CO-B", 451, 523, 30, 50);
        addDeskToMap("GBS-1", 604, 408, 25, 50);
        addDeskToMap("GBS-2", 604, 457, 25, 50);
        addDeskToMap("GBS-3", 604, 504, 25, 50);
        addDeskToMap("GBS-4", 631, 408, 25, 50);
        addDeskToMap("GBS-5", 631, 457, 25, 50);
        addDeskToMap("GBS-6", 631, 504, 25, 50);
        addDeskToMap("GBS-7", 729, 408, 25, 50);
        addDeskToMap("GBS-8", 729, 457, 25, 50);
        addDeskToMap("GBS-9", 729, 504, 25, 50);
        addDeskToMap("GBS-10", 758, 408, 25, 50);
        addDeskToMap("GBS-11", 758, 457, 25, 50);
        addDeskToMap("GBS-12", 758, 504, 25, 50);
        addDeskToMap("GBS-13", 855, 408, 25, 50);
        addDeskToMap("GBS-14", 855, 457, 25, 50);
        addDeskToMap("GBS-15", 855, 504, 25, 50);
        addDeskToMap("GBS-16", 883, 408, 25, 50);
        addDeskToMap("GBS-17", 883, 457, 25, 50);
        addDeskToMap("GBS-18", 883, 504, 25, 50);
        addDeskToMap("GBS-19", 1001, 408, 25, 50);
        addDeskToMap("GBS-20", 1001, 457, 25, 50);
        addDeskToMap("GBS-21", 1001, 504, 25, 50);
        addDeskToMap("GBS-22", 1028, 408, 25, 50);
        addDeskToMap("GBS-23", 1028, 457, 25, 50);
        addDeskToMap("GBS-24", 1028, 504, 25, 50);
        addDeskToMap("GBS-25", 1139, 408, 25, 50);
        addDeskToMap("GBS-26", 1139, 457, 25, 50);
        addDeskToMap("GBS-27", 1139, 504, 25, 50);
        addDeskToMap("GBS-28", 1165, 408, 25, 50);
        addDeskToMap("GBS-29", 1165, 457, 25, 50);
        addDeskToMap("GBS-30", 1165, 504, 25, 50);
        addDeskToMap("GBS-31", 1266, 408, 25, 50);
        addDeskToMap("GBS-32", 1266, 457, 25, 50);
        addDeskToMap("GBS-33", 1266, 504, 25, 50);
        addDeskToMap("GBS-34", 1293, 408, 25, 50);
        addDeskToMap("GBS-35", 1293, 457, 25, 50);
        addDeskToMap("GBS-36", 1293, 504, 25, 50);
        addMeetingRoomToMap("Sinaia", 325, 187, 50, 50);
        addMeetingRoomToMap("Predeal", 998, 185, 50, 50);
        addDeskToMap("Straja", 843, 244, 77, 32);
    }

    private void addDeskToMap(String id, double x, double y, double w, double h) {
        DeskButton btn = new DeskButton(id, userEmail, userName, userPhotoUrl, selectedDate, this::refreshMyBookings);
        btn.getStyle().set("position", "absolute");
        btn.getStyle().set("left", x + "px");
        btn.getStyle().set("top", y + "px");
        btn.getStyle().set("width", w + "px");
        btn.getStyle().set("height", h + "px");
        btn.getStyle().set("border-radius", "10px");

        BookingInfo info = (currentBookingsMap != null) ? currentBookingsMap.get(id) : null;
        btn.setAvailabilityFromData(info, selectedTime);

        mapWrapper.add(btn);
    }

    private void addMeetingRoomToMap(String id, double x, double y, double w, double h) {
        addDeskToMap(id, x, y, w, h);
    }

    private void applyResponsiveScale() {
        getElement().executeJs(
            "setTimeout(function() {" +
            "  document.documentElement.style.zoom = '1';\n" +
            "  \n" +
            "  var availableWidth = window.innerWidth;\n" +
            "  var baseWidth = 1600;\n" +
            "  var ratio = (availableWidth / baseWidth) * 0.85; \n" +
            "  \n" +
            "  if (ratio < 0.99) {" +
            "    document.documentElement.style.zoom = ratio;\n" +
            "  }\n" +
            "}, 100);"
        );
    }
    
    private void refreshMyBookings() {
        bookingsList.removeAll();
        var myBookings = DatabaseManager.getInstance().getBookingsForUser(userEmail);

        if (myBookings.isEmpty()) {
            bookingsList.add(new Span("No active bookings found."));
            return;
        }

        for (var b : myBookings) {
            LocalDate bookingDate = LocalDate.parse(b.getDate());
            if (bookingDate.isBefore(LocalDate.now())) continue;

            VerticalLayout card = new VerticalLayout();
            card.setSpacing(false);
            card.setPadding(true);
            card.getStyle().set("background-color", "white");
            card.getStyle().set("border", "1px solid #ddd");
            card.getStyle().set("border-radius", "8px");

            Span deskInfo = new Span("Desk " + b.getDeskId());
            deskInfo.getStyle().set("font-weight", "bold");
            deskInfo.getStyle().set("color", "#1a73e8");

            Button btnDel = new Button("Delete", VaadinIcon.TRASH.create(), e -> {
                DatabaseManager.getInstance().deleteBooking(b.getDeskId(), b.getDate(), userEmail);
                sendLogToServer("ACTION: Delete (List) | DESK: " + b.getDeskId() + " | USER: " + userName);
                refreshMyBookings();
                loadMap();
            });
            btnDel.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

            card.add(deskInfo, new Span("Date: " + b.getDate()), new Span("Hours: " + b.getInterval()), btnDel);
            bookingsList.add(card);
        }
    }

    private void performSearch(String targetName) {
        if (targetName == null || targetName.isEmpty()) return;
        sendLogToServer("ACTION: Search | QUERY: " + targetName + " | USER: " + userName);

        if (currentHighlightedBtn != null) {
            currentHighlightedBtn.removeHighlight();
            currentHighlightedBtn = null;
        }

        boolean found = false;
        for (var component : mapWrapper.getChildren().toArray()) {
            if (component instanceof DeskButton) {
                DeskButton btn = (DeskButton) component;
                if (btn.isBooked() && btn.getBookedByName() != null && btn.getBookedByName().equalsIgnoreCase(targetName)) {
                    found = true;
                    currentHighlightedBtn = btn;
                    btn.getStyle().set("border", "4px solid gold");
                    btn.getStyle().set("box-shadow", "0 0 15px gold");
                    break;
                }
            }
        }

        if (!found) {
            Notification.show("User '" + targetName + "' not found on this level today.", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        }
    }

    private void showExportDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Export Data");

        DatePicker start = new DatePicker("Start Date", LocalDate.now().minusDays(30));
        DatePicker end = new DatePicker("End Date", LocalDate.now());

        Button btnDoExport = new Button("Export CSV", e -> {
            if (start.getValue().isAfter(end.getValue())) {
                Notification.show("Invalid interval!", 2000, Notification.Position.MIDDLE);
                return;
            }
            exportToCSV(start.getValue(), end.getValue());
            dialog.close();
        });

        dialog.add(new VerticalLayout(start, end));
        dialog.getFooter().add(new Button("Cancel", e -> dialog.close()), btnDoExport);
        dialog.open();
    }

    private void exportToCSV(LocalDate s, LocalDate e) {
        var data = DatabaseManager.getInstance().getBookingsForReport(s, e);
        StringBuilder csv = new StringBuilder("Date,Desk ID,Interval,Employee Name,Email\n");
        for (var b : data) {
            csv.append(String.format("%s,%s,%s,%s,%s\n", b.getDate(), b.getDeskId(), b.getInterval(), b.getName(), b.getEmail()));
        }

        StreamResource res = new StreamResource("Report.csv", () -> new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8)));
        var registration = VaadinSession.getCurrent().getResourceRegistry().registerResource(res);
        UI.getCurrent().getPage().executeJs(
                "const a = document.createElement('a'); a.href = $0; a.setAttribute('download', 'Report.csv'); a.click();", 
                registration.getResourceUri().toString()
            );
        sendLogToServer("ACTION: Export Report | USER: " + userName);
    }

    private void startMidnightRefreshTimer() {
        LocalDateTime now = LocalDateTime.now();
        long delay = Duration.between(now, now.truncatedTo(ChronoUnit.DAYS).plusDays(1)).getSeconds();

        uiScheduler.scheduleAtFixedRate(() -> {
            getUI().ifPresent(ui -> ui.access(() -> {
                selectedDate = LocalDate.now();
                loadMap();
                refreshMyBookings();
                System.out.println("UI Refreshed at midnight.");
            }));
        }, delay, 24 * 60 * 60, TimeUnit.SECONDS);
    }

    public static void sendLogToServer(String message) {
        new Thread(() -> {
            try (Socket socket = new Socket("localhost", 5000);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                out.println(message);
            } catch (Exception ignored) {}
        }).start();
    }
}