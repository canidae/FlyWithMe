package net.exent.flywithme;

import com.google.appengine.api.datastore.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScheduleServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger(ScheduleServlet.class.getName());

    // TODO: store registration time, store unregistered, have client ask for a time frame (registrations/unregistrations for the last x seconds)
    private static final Key DATASTORE_SCHEDULES_KEY = KeyFactory.createKey("FlyWithMe", "Schedules");
    private static final Map<Integer, TakeoffSchedule> schedules = new HashMap<>();
    private static long lastScheduleClean = System.currentTimeMillis();

    public ScheduleServlet() {
        // read in schedule from datastore
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        try {
            Entity entity = datastore.get(DATASTORE_SCHEDULES_KEY);
            for (Map.Entry<String, Object> entry : entity.getProperties().entrySet()) {
                try {
                    Text text = (Text) entry.getValue();
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(text.getValue().getBytes());
                    XMLDecoder decoder = new XMLDecoder(inputStream);
                    schedules.put(Integer.valueOf(entry.getKey()), (TakeoffSchedule) decoder.readObject());
                } catch (Exception e) {
                    // failed reading in schedule? log error, and continue trying to read in remaining
                    log.log(Level.WARNING, "Failed reading in schedule for takeoff " + entry.getKey() + ": " + entry.getValue(), e);
                }
            }
        } catch (EntityNotFoundException e) {
            // seems like our datastore doesn't have any schedules
            log.log(Level.INFO, "No data found in datastore");
        }
    }

    /* Used for testing, this code should not be uploaded to google app engine
    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        // TODO: remove, just for testing
        // first test registering some flights
        System.out.println("=== TESTING REGISTERING FLIGHT ===");
        int registerTime = (int) (System.currentTimeMillis() / 1000);
        long pilotId = (new Random()).nextLong();
        HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8080/fwm").openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        DataOutputStream outputStream = new DataOutputStream(con.getOutputStream());
        outputStream.writeByte(1);
        outputStream.writeShort(4);
        outputStream.writeInt(registerTime);
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
        outputStream.writeInt(registerTime);
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
        outputStream.writeInt(registerTime);
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
        outputStream.writeShort(3);
        outputStream.writeShort(4);
        outputStream.writeShort(67);
        outputStream.writeShort(1337);
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
                System.out.println("Timestamp: " + inputStream.readInt());
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
        outputStream.writeInt(registerTime);
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
        outputStream.writeShort(3);
        outputStream.writeShort(4);
        outputStream.writeShort(67);
        outputStream.writeShort(1337);
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
                System.out.println("Timestamp: " + inputStream.readInt());
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
            int responseCode;
            switch (operation) {
                case 0:
                    // fetch schedule for supplied takeoffs
                    // input:
                    // ushort: takeoffIdCount
                    //   ushort: takeoffId
                    responseCode = getScheduleV1(inputStream, outputStream);
                    // output:
                    // <loop>
                    //   ushort: takeoffId (if this value is 0, then the end of the list is reached and no more data should be attempted read)
                    //   ushort: timestamps
                    //     int: timestamp
                    //     ushort: pilots
                    //       string: pilot name
                    //       string: pilot phone
                    break;

                case 1:
                    // register flight at takeoff
                    // input:
                    // ushort: takeoffId
                    // int: timestamp
                    // long: pilotId
                    // string: name
                    // string: phone
                    responseCode = registerScheduleEntryV1(inputStream);
                    // output:
                    // <none>
                    break;

                case 2:
                    // unregister flight at takeoff
                    // input:
                    // ushort: takeoffId
                    // int: timestamp
                    // long: pilotId
                    responseCode = unregisterScheduleEntryV1(inputStream);
                    // output:
                    // <none>
                    break;

                case 3:
                    // fetch meteogram/sounding for location
                    // input:
                    // float: latitude
                    // float: longitude
                    // boolean: fetchMeteogram
                    // ubyte: soundings
                    //   int: timestamp
                    // TODO: responseCode = getMeteogramAndSounding(inputStream, outputStream);
                    // output:
                    // ubyte: responsetype (0 = captcha, 1 = meteogram/sounding)
                    // int: meteogramSize/captchaSize
                    // <bytes>: meteogram/captcha
                    // ubyte: soundings
                    //   int: soundingSize
                    //   <bytes>: sounding

                /*
                case 4:
                    // input:
                    // <none>
                    responseCode = getScheduleV2(outputStream);
                    // output:
                    // <loop>
                    //   ushort: takeoffId (if this value is 0, then the end of the list is reached and no more data should be attempted read)
                    //   ushort: timestamps
                    //     int: timestamp
                    //     ushort: pilots
                    //       string: pilot name
                    //       string: pilot phone
                    break;
                 */

                default:
                    responseCode = HttpServletResponse.SC_NOT_FOUND;
                    break;
            }

            if (responseCode != HttpServletResponse.SC_OK)
                response.sendError(responseCode);

            /* clean up if it's been some time since we last cleaned */
            long currentTime = System.currentTimeMillis();
            if (currentTime < lastScheduleClean + 1000 * 60 * 60 * 6)
                return; // less than 6 hours since last cleanup, no rush
            int expireTime = (int) ((currentTime - 1000 * 60 * 60 * 24) / 1000); // remove all entries that are older than 24 hours
            Iterator<Map.Entry<Integer, TakeoffSchedule>> scheduleIterator = schedules.entrySet().iterator();
            while (scheduleIterator.hasNext()) {
                Map.Entry<Integer, TakeoffSchedule> scheduleIteratorEntry = scheduleIterator.next();
                TakeoffSchedule takeoffSchedule = scheduleIteratorEntry.getValue();
                takeoffSchedule.removeExpired(expireTime);
                if (takeoffSchedule.isEmpty())
                    scheduleIterator.remove();
            }
            storeSchedules();
            lastScheduleClean = currentTime;
        } catch (Exception e) {
            log.log(Level.WARNING, "Exception handling request", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /* schedule for favourited takeoffs */
    private static synchronized int getScheduleV1(final DataInputStream inputStream, final DataOutputStream outputStream) throws IOException {
        int takeoffCount = inputStream.readUnsignedShort();
        if (takeoffCount > 200)
            takeoffCount = 200; // don't allow asking for more than 200 takeoffs

        StringBuilder sb = new StringBuilder("Get schedule: ");
        for (int i = 0; i < takeoffCount; ++i) {
            int takeoffId = inputStream.readUnsignedShort();
            TakeoffSchedule takeoffSchedule = schedules.get(takeoffId);
            SortedMap<Integer, Set<Pilot>> schedule;
            if (takeoffSchedule == null || (schedule = takeoffSchedule.getEntries()).isEmpty()) {
                // no schedule for this takeoff, but in case we've removed a single entry we need to tell the client that it's empty (or the client won't remove that entry)
                outputStream.writeShort(takeoffId);
                outputStream.writeShort(0);
                continue;
            }
            outputStream.writeShort(takeoffId);
            outputStream.writeShort(schedule.size());
            sb.append(takeoffId);
            sb.append(',').append(schedule.size());
            int timestampCounter = 0;
            for (Map.Entry<Integer, Set<Pilot>> scheduleEntry : schedule.entrySet()) {
                if (++timestampCounter > 10)
                    break; // max 10 timestamps per takeoff returned
                outputStream.writeInt(scheduleEntry.getKey());
                sb.append(',').append(scheduleEntry.getKey());
                Set<Pilot> pilots = scheduleEntry.getValue();
                outputStream.writeShort(pilots.size());
                sb.append(',').append(pilots.size());
                int pilotCounter = 0;
                for (Pilot pilot : pilots) {
                    if (++pilotCounter > 10)
                        break; // max 10 pilots per timestamp
                    // make sure we don't send long strings by capping name to 40 and phone to 20 characters
                    outputStream.writeUTF(pilot.getName().length() > 40 ? pilot.getName().substring(0, 40) : pilot.getName());
                    outputStream.writeUTF(pilot.getPhone().length() > 20 ? pilot.getPhone().substring(0, 20) : pilot.getPhone());
                    sb.append(',').append(pilot.getName());
                    sb.append(',').append(pilot.getPhone());
                }
            }
            sb.append(" | ");
        }
        outputStream.writeShort(0);
        log.info(sb.toString());
        return HttpServletResponse.SC_OK;
    }

    /* fetch schedule for all takeoffs */
    private static synchronized int getScheduleV2(final DataOutputStream outputStream) throws IOException {
        StringBuilder sb = new StringBuilder("Get full schedule: ");
        for (Map.Entry<Integer, TakeoffSchedule> takeoffSchedule : schedules.entrySet()) {
            int takeoffId = takeoffSchedule.getKey();
            SortedMap<Integer, Set<Pilot>> schedule = takeoffSchedule.getValue().getEntries();
            outputStream.writeShort(takeoffId);
            outputStream.writeShort(schedule.size());
            sb.append(takeoffId);
            sb.append(',').append(schedule.size());
            for (Map.Entry<Integer, Set<Pilot>> scheduleEntry : schedule.entrySet()) {
                outputStream.writeInt(scheduleEntry.getKey());
                sb.append(',').append(scheduleEntry.getKey());
                Set<Pilot> pilots = scheduleEntry.getValue();
                outputStream.writeShort(pilots.size());
                sb.append(',').append(pilots.size());
                for (Pilot pilot : pilots) {
                    // make sure we don't send long strings by capping name to 40 and phone to 20 characters
                    outputStream.writeUTF(pilot.getName().length() > 40 ? pilot.getName().substring(0, 40) : pilot.getName());
                    outputStream.writeUTF(pilot.getPhone().length() > 20 ? pilot.getPhone().substring(0, 20) : pilot.getPhone());
                    sb.append(',').append(pilot.getName());
                    sb.append(',').append(pilot.getPhone());
                }
            }
            sb.append(" | ");
        }
        outputStream.writeShort(0);
        log.info(sb.toString());
        return HttpServletResponse.SC_OK;
    }

    /* register a flight at a takeoff */
    private static synchronized int registerScheduleEntryV1(final DataInputStream inputStream) throws IOException {
        int takeoffId = inputStream.readUnsignedShort();
        int timestamp = inputStream.readInt() / 60 * 60; // "/ 60 * 60" sets seconds to 0
        long pilotId = inputStream.readLong();
        if (pilotId == 0)
            return HttpServletResponse.SC_BAD_REQUEST;
        String pilotName = inputStream.readUTF();
        if ("".equals(pilotName.trim()))
            return HttpServletResponse.SC_BAD_REQUEST;
        String pilotPhone = inputStream.readUTF();
        log.info("Scheduling, takeoff ID: " + takeoffId + ", timestamp: " + timestamp + ", pilot ID: " + pilotId + ", name: " + pilotName + ", phone: " + pilotPhone);
        TakeoffSchedule takeoffSchedule = schedules.get(takeoffId);
        if (takeoffSchedule == null) {
            takeoffSchedule = new TakeoffSchedule();
            schedules.put(takeoffId, takeoffSchedule);
        }
        takeoffSchedule.addPilotToSchedule(timestamp, new Pilot(pilotId, pilotName, pilotPhone));
        storeSchedules();
        return HttpServletResponse.SC_OK;
    }

    /* unregister a flight at a takeoff */
    private static synchronized int unregisterScheduleEntryV1(final DataInputStream inputStream) throws IOException {
        int takeoffId = inputStream.readUnsignedShort();
        int timestamp = inputStream.readInt() / 60 * 60; // "/ 60 * 60" sets seconds to 0
        long pilotId = inputStream.readLong();
        log.info("Unscheduling, takeoff ID: " + takeoffId + ", timestamp: " + timestamp + ", pilot ID: " + pilotId);
        TakeoffSchedule takeoffSchedule = schedules.get(takeoffId);
        if (takeoffSchedule != null)
            takeoffSchedule.removePilotFromSchedule(timestamp, pilotId);
        storeSchedules();
        return HttpServletResponse.SC_OK;
    }

    private static synchronized int getMeteogramAndSounding(final DataInputStream inputStream, final DataOutputStream outputStream) throws IOException {
        // TODO
        //noaaUserId = getOne(fetchPageContent(NOAA_URL + "/ready2-bin/main.pl?Lat=" + loc.getLatitude() + "&Lon=" + loc.getLongitude()), NOAA_USERID_PATTERN);

        return HttpServletResponse.SC_OK;
    }

    private static void storeSchedules() {
        Entity entity = new Entity(DATASTORE_SCHEDULES_KEY);
        for (Map.Entry<Integer, TakeoffSchedule> schedule : schedules.entrySet()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            XMLEncoder xmlEncoder = new XMLEncoder(bos);
            xmlEncoder.writeObject(schedule.getValue());
            xmlEncoder.close();
            entity.setProperty(schedule.getKey().toString(), new Text(bos.toString()));
        }
        DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
        datastore.put(entity);
    }

    // NOTE! even though it may appear like the setters and getters are not in use, they are!
    // deserializing from datastore use the default constructor and the setters to rebuild the object!
    public static class Pilot {
        private long id;
        private String name;
        private String phone;

        public Pilot() {
            // required for deserialization
        }

        public Pilot(long id, String name, String phone) {
            this.id = id;
            this.name = name;
            this.phone = phone;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
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

    // NOTE! even though it may appear like the setters and getters are not in use, they are!
    // deserializing from datastore use the default constructor and the setters to rebuild the object!
    public static class TakeoffSchedule {
        private SortedMap<Integer, Set<Pilot>> entries = new TreeMap<>();

        public void addPilotToSchedule(int timestamp, Pilot pilot) {
            Set<Pilot> schedule = entries.get(timestamp);
            if (schedule == null) {
                schedule = new HashSet<>();
                entries.put(timestamp, schedule);
            }
            schedule.add(pilot);
        }

        public void removePilotFromSchedule(int timestamp, long pilotId) {
            Set<Pilot> schedule = entries.get(timestamp);
            if (schedule != null) {
                schedule.remove(new Pilot(pilotId, "doesn't matter", "neither does this"));
                if (schedule.isEmpty())
                    entries.remove(timestamp);
            }
        }

        public void removeExpired(int expireTime) {
            Iterator<SortedMap.Entry<Integer, Set<Pilot>>> timestampIterator = entries.entrySet().iterator();
            while (timestampIterator.hasNext()) {
                SortedMap.Entry<Integer, Set<Pilot>> timestampEntry = timestampIterator.next();
                if (timestampEntry.getKey() < expireTime)
                    timestampIterator.remove();
            }
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        public SortedMap<Integer, Set<Pilot>> getEntries() {
            return entries;
        }

        public void setEntries(SortedMap<Integer, Set<Pilot>> entries) {
            this.entries = entries;
        }
    }
}
