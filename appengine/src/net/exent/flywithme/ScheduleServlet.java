package net.exent.flywithme;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScheduleServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(ScheduleServlet.class.getName());

    // TODO: store registration time, store unregistered, have client ask for a time frame (registrations/unregistrations for the last x seconds)
    private static Map<Integer, Map<Long, List<String>>> schedule = new ConcurrentHashMap<>(); // <takeoffId, <timestamp, <pilots>>>
    private static long lastScheduleClean = System.currentTimeMillis();

    /*
    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        // TODO: remove, just for testing
        // first test registering some flights
        System.out.println("=== TESTING REGISTERING FLIGHT ===");
        long registerTime = System.currentTimeMillis();
        HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8080/fwm").openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        DataOutputStream outputStream = new DataOutputStream(con.getOutputStream());
        outputStream.writeByte(1);
        outputStream.writeShort(4);
        outputStream.writeUTF("95728262");
        outputStream.writeLong(registerTime);
        outputStream.close();
        int responseCode = con.getResponseCode();
        System.out.println("Response code: " + responseCode);
        DataInputStream inputStream = new DataInputStream(con.getInputStream());
        System.out.println("Result: " + inputStream.readBoolean());
        inputStream.close();
        // another one
        con = (HttpURLConnection) new URL("http://localhost:8080/fwm").openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        outputStream = new DataOutputStream(con.getOutputStream());
        outputStream.writeByte(1);
        outputStream.writeShort(4);
        outputStream.writeUTF("88888888");
        outputStream.writeLong(registerTime);
        outputStream.close();
        responseCode = con.getResponseCode();
        System.out.println("Response code: " + responseCode);
        inputStream = new DataInputStream(con.getInputStream());
        System.out.println("Result: " + inputStream.readBoolean());
        inputStream.close();
        // and one more
        con = (HttpURLConnection) new URL("http://localhost:8080/fwm").openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        outputStream = new DataOutputStream(con.getOutputStream());
        outputStream.writeByte(1);
        outputStream.writeShort(67);
        outputStream.writeUTF("95728262");
        outputStream.writeLong(registerTime);
        outputStream.close();
        responseCode = con.getResponseCode();
        System.out.println("Response code: " + responseCode);
        inputStream = new DataInputStream(con.getInputStream());
        System.out.println("Result: " + inputStream.readBoolean());
        inputStream.close();

        // then test fetching scheduled flights
        System.out.println("=== TESTING FETCHING SCHEDULE ===");
        con = (HttpURLConnection) new URL("http://localhost:8080/fwm").openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        outputStream = new DataOutputStream(con.getOutputStream());
        outputStream.writeByte(0);
        outputStream.writeShort(2);
        outputStream.writeShort(4);
        outputStream.writeShort(67);
        outputStream.close();
        responseCode = con.getResponseCode();
        System.out.println("Response code: " + responseCode);
        inputStream = new DataInputStream(con.getInputStream());
        int takeoffs = inputStream.readUnsignedShort();
        System.out.println("Takeoffs: " + takeoffs);
        for (int a = 0; a < takeoffs; ++a) {
            System.out.println("Takeoff ID: " + inputStream.readUnsignedShort());
            int timestamps = inputStream.readUnsignedShort();
            System.out.println("Timestamps: " + timestamps);
            for (int b = 0; b < timestamps; ++b) {
                System.out.println("Timestamp: " + inputStream.readLong());
                int pilots = inputStream.readUnsignedShort();
                System.out.println("Pilots: " + pilots);
                for (int c = 0; c < pilots; ++c) {
                    System.out.println("Pilot: " + inputStream.readUTF());
                }
            }
        }
        inputStream.close();

        // then test unregistering
        System.out.println("=== TESTING UNREGISTERING FLIGHT ===");
        con = (HttpURLConnection) new URL("http://localhost:8080/fwm").openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        outputStream = new DataOutputStream(con.getOutputStream());
        outputStream.writeByte(2);
        outputStream.writeShort(4);
        outputStream.writeUTF("95728262");
        outputStream.writeLong(registerTime);
        outputStream.close();
        responseCode = con.getResponseCode();
        System.out.println("Response code: " + responseCode);
        inputStream = new DataInputStream(con.getInputStream());
        System.out.println("Result: " + inputStream.readBoolean());
        inputStream.close();

        // then test fetching scheduled flights again
        System.out.println("=== TESTING FETCHING SCHEDULE ===");
        con = (HttpURLConnection) new URL("http://localhost:8080/fwm").openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        outputStream = new DataOutputStream(con.getOutputStream());
        outputStream.writeByte(0);
        outputStream.writeShort(2);
        outputStream.writeShort(4);
        outputStream.writeShort(67);
        outputStream.close();
        responseCode = con.getResponseCode();
        System.out.println("Response code: " + responseCode);
        inputStream = new DataInputStream(con.getInputStream());
        takeoffs = inputStream.readUnsignedShort();
        System.out.println("Takeoffs: " + takeoffs);
        for (int a = 0; a < takeoffs; ++a) {
            System.out.println("Takeoff ID: " + inputStream.readUnsignedShort());
            int timestamps = inputStream.readUnsignedShort();
            System.out.println("Timestamps: " + timestamps);
            for (int b = 0; b < timestamps; ++b) {
                System.out.println("Timestamp: " + inputStream.readLong());
                int pilots = inputStream.readUnsignedShort();
                System.out.println("Pilots: " + pilots);
                for (int c = 0; c < pilots; ++c) {
                    System.out.println("Pilot: " + inputStream.readUTF());
                }
            }
        }
        inputStream.close();
    }
    */

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        try (DataInputStream inputStream = new DataInputStream(request.getInputStream()); DataOutputStream outputStream = new DataOutputStream(response.getOutputStream())) {
            int operation = inputStream.readUnsignedByte();
            switch (operation) {
                case 0:
                    // input:
                    // ushort: takeoffIdCount
                    //   ushort: takeoffId
                    getScheduleV1(inputStream, outputStream);
                    // output:
                    // ushort: amount of takeoffs with scheduled flights
                    //   ushort: takeoffId
                    //   ushort: timestamps
                    //     long: timestamp
                    //     ushort: pilots
                    //       string: pilot name
                    break;

                case 1:
                    // input:
                    // ushort: takeoffId
                    // string: name
                    // long: timestamp
                    registerScheduleEntryV1(inputStream, outputStream);
                    // output:
                    // boolean: success
                    break;

                case 2:
                    // input:
                    // ushort: takeoffId
                    // string: name
                    // long: timestamp
                    unregisterScheduleEntryV1(inputStream, outputStream);
                    // output:
                    // boolean: success
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Exception handling request", e);
        }
    }

    /* schedule for favourited takeoffs */
    private static void getScheduleV1(final DataInputStream inputStream, final DataOutputStream outputStream) throws IOException {
        int favouriteCount = inputStream.readUnsignedShort();
        int[] takeoffIds = new int[favouriteCount];
        Map<Integer, Map<Long, List<String>>> scheduled = new HashMap<>();

        for (int i = 0; i < favouriteCount; ++i) {
            takeoffIds[i] = inputStream.readUnsignedShort();
            Map<Long, List<String>> entries = schedule.get(takeoffIds[i]);
            if (entries != null)
                scheduled.put(takeoffIds[i], entries);
        }
        outputStream.writeShort(scheduled.size());
        for (Map.Entry<Integer, Map<Long, List<String>>> takeoffEntry : scheduled.entrySet()) {
            outputStream.writeShort(takeoffEntry.getKey());
            outputStream.writeShort(takeoffEntry.getValue().size());
            for (Map.Entry<Long, List<String>> timestampEntry : takeoffEntry.getValue().entrySet()) {
                outputStream.writeLong(timestampEntry.getKey());
                outputStream.writeShort(timestampEntry.getValue().size());
                for (String pilot : timestampEntry.getValue()) {
                    outputStream.writeUTF(pilot);
                }
            }
        }

        // let's clean up the schedule list if it's been some time since we did it
        long currentTime = System.currentTimeMillis();
        if (currentTime < lastScheduleClean + 1000 * 60 * 60 * 24)
            return; // less than 24 hours since last cleanup, no rush

        // remove scheduled flights that were scheduled to occur for more than 24 hours ago
        Iterator<Map.Entry<Integer, Map<Long, List<String>>>> takeoffIterator = schedule.entrySet().iterator();
        while (takeoffIterator.hasNext()) {
            Map.Entry<Integer, Map<Long, List<String>>> takeoffEntry = takeoffIterator.next();
            Iterator<Map.Entry<Long, List<String>>> timestampIterator = takeoffEntry.getValue().entrySet().iterator();
            while (timestampIterator.hasNext()) {
                Map.Entry<Long, List<String>> timestampEntry = timestampIterator.next();
                // is it more than 24 hours since this flight happened?
                if (timestampEntry.getKey() < currentTime - 1000 * 60 * 60 * 24)
                    timestampIterator.remove(); // yes, remove timestamp from takeoff schedule
            }
            // are there any registered flights for this takeoff?
            if (takeoffEntry.getValue().isEmpty())
                takeoffIterator.remove(); // no, remove takeoff from schedule
        }
        lastScheduleClean = currentTime;
    }

    /* register a flight at a takeoff */
    private static void registerScheduleEntryV1(final DataInputStream inputStream, final DataOutputStream outputStream) throws IOException {
        // TODO: people can easily impersonate someone else to either register or unregister, it's costly to fix, and probably not a significant problem
        int takeoffId = inputStream.readUnsignedShort();
        String pilot = inputStream.readUTF(); // TODO: can we limit this somehow?
        long timestamp = inputStream.readLong();
        Map<Long, List<String>> timestamps = schedule.get(takeoffId);
        if (timestamps == null) {
            timestamps = new ConcurrentHashMap<>();
            schedule.put(takeoffId, timestamps);
        }
        List<String> pilots = timestamps.get(timestamp);
        if (pilots == null) {
            pilots = Collections.synchronizedList(new ArrayList<String>());
            timestamps.put(timestamp, pilots);
        }
        pilots.add(pilot);
        outputStream.writeBoolean(true);
    }

    /* unregister a flight at a takeoff */
    private static void unregisterScheduleEntryV1(final DataInputStream inputStream, final DataOutputStream outputStream) throws IOException {
        int takeoffId = inputStream.readUnsignedShort();
        String pilot = inputStream.readUTF(); // TODO: can we limit this somehow?
        long timestamp = inputStream.readLong();
        Map<Long, List<String>> timestamps = schedule.get(takeoffId);
        if (timestamps != null) {
            List<String> pilots = timestamps.get(timestamp);
            pilots.remove(pilot);
            // if there are no pilots left for this timestamp, we may as well clean up
            if (pilots.isEmpty())
                timestamps.remove(timestamp);
            // if there are no timestamps registered on this takeoff, we may as well clean up
            if (timestamps.isEmpty())
                schedule.remove(takeoffId);
        }
        outputStream.writeBoolean(true);
    }
}
