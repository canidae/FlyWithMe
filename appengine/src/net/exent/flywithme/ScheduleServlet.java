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
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScheduleServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(ScheduleServlet.class.getName());

    // TODO: store registration time, store unregistered, have client ask for a time frame (registrations/unregistrations for the last x seconds)
    private static Map<Integer, TakeoffSchedule> schedules = new HashMap<>();
    private static long lastScheduleClean = System.currentTimeMillis();

    /*
    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        // TODO: remove, just for testing
        // first test registering some flights
        System.out.println("=== TESTING REGISTERING FLIGHT ===");
        long registerTime = System.currentTimeMillis();
        long pilotId = (new Random()).nextLong();
        HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8080/fwm").openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        DataOutputStream outputStream = new DataOutputStream(con.getOutputStream());
        outputStream.writeByte(1);
        outputStream.writeShort(4);
        outputStream.writeLong(registerTime);
        outputStream.writeLong(pilotId);
        outputStream.writeUTF("Vidar Wahlberg");
        outputStream.writeUTF("+4795728262");
        outputStream.close();
        int responseCode = con.getResponseCode();
        System.out.println("Response code: " + responseCode);
        DataInputStream inputStream = new DataInputStream(con.getInputStream());
        inputStream.close();
        // another one
        con = (HttpURLConnection) new URL("http://localhost:8080/fwm").openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        outputStream = new DataOutputStream(con.getOutputStream());
        outputStream.writeByte(1);
        outputStream.writeShort(4);
        outputStream.writeLong(registerTime);
        outputStream.writeLong((new Random()).nextLong());
        outputStream.writeUTF("Unknown Pilot");
        outputStream.writeUTF("88888888");
        outputStream.close();
        responseCode = con.getResponseCode();
        System.out.println("Response code: " + responseCode);
        inputStream = new DataInputStream(con.getInputStream());
        inputStream.close();
        // and one more
        con = (HttpURLConnection) new URL("http://localhost:8080/fwm").openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        outputStream = new DataOutputStream(con.getOutputStream());
        outputStream.writeByte(1);
        outputStream.writeShort(67);
        outputStream.writeLong(registerTime);
        outputStream.writeLong(pilotId);
        outputStream.writeUTF("Vidar Wahlberg");
        outputStream.writeUTF("95728262");
        outputStream.close();
        responseCode = con.getResponseCode();
        System.out.println("Response code: " + responseCode);
        inputStream = new DataInputStream(con.getInputStream());
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
        while (true) {
            int takeoffId = inputStream.readUnsignedShort();
            if (takeoffId == 0)
                break; // no more takeoffs
            System.out.println("Takeoff ID: " + takeoffId);
            int timestamps = inputStream.readUnsignedShort();
            System.out.println("Timestamps: " + timestamps);
            for (int b = 0; b < timestamps; ++b) {
                System.out.println("Timestamp: " + inputStream.readLong());
                int pilots = inputStream.readUnsignedShort();
                System.out.println("Pilots: " + pilots);
                for (int c = 0; c < pilots; ++c) {
                    System.out.println("Pilot: " + inputStream.readUTF());
                    System.out.println("Phone: " + inputStream.readUTF());
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
        outputStream.writeLong(registerTime);
        outputStream.writeLong(pilotId);
        outputStream.close();
        responseCode = con.getResponseCode();
        System.out.println("Response code: " + responseCode);
        inputStream = new DataInputStream(con.getInputStream());
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
        while (true) {
            int takeoffId = inputStream.readUnsignedShort();
            if (takeoffId == 0)
                break; // no more takeoffs
            System.out.println("Takeoff ID: " + takeoffId);
            int timestamps = inputStream.readUnsignedShort();
            System.out.println("Timestamps: " + timestamps);
            for (int b = 0; b < timestamps; ++b) {
                System.out.println("Timestamp: " + inputStream.readLong());
                int pilots = inputStream.readUnsignedShort();
                System.out.println("Pilots: " + pilots);
                for (int c = 0; c < pilots; ++c) {
                    System.out.println("Pilot: " + inputStream.readUTF());
                    System.out.println("Phone: " + inputStream.readUTF());
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
                    // <loop>
                    //   ushort: takeoffId (if this value is 0, then the end of the list is reached and no more data should be attempted read)
                    //   ushort: timestamps
                    //     long: timestamp
                    //     ushort: pilots
                    //       string: pilot name
                    //       string: pilot phone
                    break;

                case 1:
                    // input:
                    // ushort: takeoffId
                    // long: timestamp
                    // long: pilotId
                    // string: name
                    // string: phone
                    registerScheduleEntryV1(inputStream, outputStream);
                    // output:
                    // <none>
                    break;

                case 2:
                    // input:
                    // ushort: takeoffId
                    // long: pilotId
                    unregisterScheduleEntryV1(inputStream, outputStream);
                    // output:
                    // <none>
                    break;

                default:
                    break;
            }

            /* clean up if it's been some time since we last cleaned */
            long currentTime = System.currentTimeMillis();
            if (currentTime < lastScheduleClean + 1000 * 60 * 60 * 6)
                return; // less than 6 hours since last cleanup, no rush
            long expireTime = currentTime - 1000 * 60 * 60 * 24; // remove all entries that are older than 24 hours
            Iterator<Map.Entry<Integer, TakeoffSchedule>> scheduleIterator = schedules.entrySet().iterator();
            while (scheduleIterator.hasNext()) {
                Map.Entry<Integer, TakeoffSchedule> scheduleIteratorEntry = scheduleIterator.next();
                TakeoffSchedule takeoffSchedule = scheduleIteratorEntry.getValue();
                takeoffSchedule.removeExpired(expireTime);
                if (takeoffSchedule.isEmpty())
                    scheduleIterator.remove();
            }
            lastScheduleClean = currentTime;
        } catch (Exception e) {
            log.log(Level.WARNING, "Exception handling request", e);
        }
    }

    /* schedule for favourited takeoffs */
    private static synchronized void getScheduleV1(final DataInputStream inputStream, final DataOutputStream outputStream) throws IOException {
        int takeoffCount = inputStream.readUnsignedShort();
        if (takeoffCount > 200)
            takeoffCount = 200; // don't allow asking for more than 200 takeoffs

        for (int i = 0; i < takeoffCount; ++i) {
            int takeoffId = inputStream.readUnsignedShort();
            outputStream.writeShort(takeoffId);
            TakeoffSchedule takeoffSchedule = schedules.get(takeoffId);
            Map<Long, Set<Pilot>> schedule = takeoffSchedule.getSchedule();
            outputStream.writeShort(schedule.size());
            for (Map.Entry<Long, Set<Pilot>> scheduleEntry : schedule.entrySet()) {
                outputStream.writeLong(scheduleEntry.getKey());
                Set<Pilot> pilots = scheduleEntry.getValue();
                outputStream.writeShort(pilots.size());
                for (Pilot pilot : pilots) {
                    outputStream.writeUTF(pilot.name);
                    outputStream.writeUTF(pilot.phone);
                }
            }
        }
        outputStream.writeShort(0);
    }

    /* register a flight at a takeoff */
    private static synchronized void registerScheduleEntryV1(final DataInputStream inputStream, final DataOutputStream outputStream) throws IOException {
        int takeoffId = inputStream.readUnsignedShort();
        long timestamp = inputStream.readLong();
        long pilotId = inputStream.readLong();
        String pilotName = inputStream.readUTF();
        String pilotPhone = inputStream.readUTF();
        TakeoffSchedule takeoffSchedule = schedules.get(takeoffId);
        if (takeoffSchedule == null) {
            takeoffSchedule = new TakeoffSchedule();
            schedules.put(takeoffId, takeoffSchedule);
        }
        takeoffSchedule.addPilotToSchedule(timestamp, new Pilot(pilotId, pilotName, pilotPhone));
    }

    /* unregister a flight at a takeoff */
    private static synchronized void unregisterScheduleEntryV1(final DataInputStream inputStream, final DataOutputStream outputStream) throws IOException {
        int takeoffId = inputStream.readUnsignedShort();
        long timestamp = inputStream.readLong();
        long pilotId = inputStream.readLong();
        TakeoffSchedule takeoffSchedule = schedules.get(takeoffId);
        if (takeoffSchedule != null)
            takeoffSchedule.removePilotFromSchedule(timestamp, pilotId);
    }

    private static class Pilot {
        public long id;
        public String name;
        public String phone;

        public Pilot(long id, String name, String phone) {
            this.id = id;
            this.name = name;
            this.phone = phone;
        }

        @Override
        public int hashCode() {
            return Long.valueOf(id).hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return (other instanceof Pilot) && other.hashCode() == hashCode();
        }
    }

    private static class TakeoffSchedule {
        private Map<Long, Set<Pilot>> entries = new HashMap<>();

        public void addPilotToSchedule(long timestamp, Pilot pilot) {
            Set<Pilot> schedule = entries.get(timestamp);
            if (schedule == null) {
                schedule = new HashSet<>();
                entries.put(timestamp, schedule);
            }
            schedule.add(pilot);
        }

        public void removePilotFromSchedule(long timestamp, long pilotId) {
            Set<Pilot> schedule = entries.get(timestamp);
            if (schedule != null) {
                schedule.remove(new Pilot(pilotId, "doesn't matter", "neither does this"));
                if (schedule.isEmpty())
                    entries.remove(timestamp);
            }
        }

        public void removeExpired(long expireTime) {
            Iterator<Map.Entry<Long, Set<Pilot>>> timestampIterator = entries.entrySet().iterator();
            while (timestampIterator.hasNext()) {
                Map.Entry<Long, Set<Pilot>> timestampEntry = timestampIterator.next();
                if (timestampEntry.getKey() < expireTime)
                    timestampIterator.remove(); // yes, remove timestamp from takeoff schedules
            }
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        public Map<Long, Set<Pilot>> getSchedule() {
            return entries;
        }
    }
}
