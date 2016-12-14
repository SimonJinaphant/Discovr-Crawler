import com.google.gson.GsonBuilder;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class DiscovrCrawler {

    private static final String TARGET_URL = "http://calendar.events.ubc.ca/cal/main/showMain.rdo";
    private static final String DISCOVR_URL = "http://discovrbackend.azurewebsites.net/api/Events";

    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json");
    private static final DateTimeFormatter FORMATTER_12_HOUR = DateTimeFormatter.ofPattern("h:mm a");


    public static void main(String[] args) {
        deleteEventInDatabase(94, 98, 99);
    }
    /**
     * Crawl the UBC events calendar and scrap the page for event data, submitting the results to our backend.
     */
    public static void crawlCalendar() {
        // Determine today's date and increment it when moving to a new event day list
        LocalDate baseDate = LocalDate.now();

        System.out.println("Starting web crawler");
        List<Event> events = new ArrayList<>();

        try {
            System.out.println("Connecting to " + TARGET_URL);
            Document document = Jsoup.connect(TARGET_URL).timeout(0).get();

            // Target the HTML table element containing all the events
            Elements weeklyEvents = document.getElementById("monthCalendarTable").child(0).child(1).children();

            // Find the index of the calendar column we're on
            int startIndex = -1;

            for (int i = 0; i < weeklyEvents.size(); i++) {
                Element day = weeklyEvents.get(i);

                if (day.hasClass("today")) {
                    startIndex = i;
                }

                if (startIndex == -1) {
                    // Don't bother parsing events that has already passed
                    continue;
                }
                if (i != startIndex) {
                    // Increment the day, we want to use a Date objects since it accounts for new year, leap year, etc...
                    baseDate = baseDate.plusDays(1);
                }

                for (Element element : day.getElementsByClass("eventTip")) {
                    // Parse the event name and description
                    String name = element.child(0).text();
                    String description = element.getElementsByClass("popDescription").text();

                    // Limit the description to 512 characters for the database
                    if (description.length() >= 512) {
                        description = description.substring(0, 512);
                    }

                    // The remaining info are normal text in the HTML
                    List<TextNode> text = element.textNodes();
                    assert text.size() == 3;

                    // Determine the host organizing this event
                    String host = text.get(2).text();

                    // Find the location, if not ignore this event
                    String location = text.get(1).text();
                    if (location.equals("See description")) {
                        System.out.println("Ignoring event that requires us to crawl more...");
                        continue;
                    }
                    if (location.contains("Okanagan")) {
                        System.out.println("Ignoring UBC Oakanagan locations...");
                        continue;
                    }
                    if (location.contains("Point Grey")) {
                        System.out.println("Ignoring campus wide events");
                        continue;
                    }

                    // Parse the start and end time
                    String time = text.get(0).text();
                    LocalDateTime startDateTime;
                    LocalDateTime endDateTime;

                    if (time.equals(" all day ")) {
                        // This event lasts all day
                        startDateTime = baseDate.atTime(6, 0);
                        endDateTime = baseDate.atTime(23, 0);

                    } else {
                        // This event has a specific time range
                        String[] startEndTimes = time.split(" - ");

                        if (startEndTimes.length != 2) {
                            continue;
                        }

                        String startTime = startEndTimes[0];
                        String endTime = startEndTimes[1];

                        try {
                            startDateTime = baseDate.atTime(LocalTime.parse(startTime, FORMATTER_12_HOUR));
                            endDateTime = baseDate.atTime(LocalTime.parse(endTime, FORMATTER_12_HOUR));
                        } catch (DateTimeParseException e) {
                            System.out.println("Unable to parse event, perhaps it contains some abnormal human input error");
                            continue;
                        }
                    }

                    events.add(new Event(name, host, location, description, startDateTime, endDateTime));
                }
            }


        } catch (IOException ioException) {
            System.err.println("Unable to connect to UBC event calendar page");
            ioException.printStackTrace();
        }

        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Event.class, new EventAdapter());

        for (Event e : events) {
            String jsonEvent = builder.create().toJson(e);
            System.out.println(jsonEvent);
            try {
                System.out.println(postEventToDatabase(jsonEvent));
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }

        }


    }

    /**
     * Submit a POST request to the remote database with the scraped event data
     *
     * @param jsonData - The event date in JSON format
     * @return - The response string of the request
     * @throws IOException - Input/Output exception
     */
    public static String postEventToDatabase(String jsonData) throws IOException {
        RequestBody body = RequestBody.create(MEDIA_TYPE_JSON, jsonData);
        Request request = new Request.Builder()
                .url(DISCOVR_URL)
                .post(body)
                .build();

        Response response = CLIENT.newCall(request).execute();
        return response.body().string();
    }

    /**
     * Remove an event from the database given its ID
     *
     * @param targets - List of Events ID to remove.
     */
    public static void deleteEventInDatabase(int... targets) {
        for (Integer i : targets) {
            Request request = new Request.Builder()
                    .url(DISCOVR_URL + "/" + i)
                    .delete()
                    .build();

            try {
                Response response = CLIENT.newCall(request).execute();
                System.out.println(response.isSuccessful());
                System.out.println(response.body().string());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}