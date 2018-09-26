package net.exent.flywithme.util;

import net.exent.flywithme.bean.Takeoff;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Tool for fetching data from flightlog.org.
 */
public class FlightlogProxy {
    private static final Log log = new Log();

    private static final String UPDATED_URL = "http://flightlog.org/?returntype=xml&rqtid=12&d=";
    private static final Pattern UNICODE_PATTERN = Pattern.compile("&#(\\d+);", Pattern.DOTALL);

    public static List<Takeoff> fetchUpdatedTakeoffs(long days) {
        try {
            TakeoffDataHandler takeoffDataHandler = new TakeoffDataHandler();
            SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
            if (days > 365) {
                // not updated in a year, most likely an empty database
                saxParser.parse(new InputSource(new FileReader(new File("resources", "flightlog.org.xml"))), takeoffDataHandler);
            } else {
                // less than a year since last update, takeoff database likely exist
                URL url = new URL(UPDATED_URL + days);
                saxParser.parse(new InputSource(url.openStream()), takeoffDataHandler);
            }
            return takeoffDataHandler.getTakeoffs();
        } catch (Exception e) {
            log.w(e, "Unable to fetch list of updated takeoffs within the last ", days, " days");
        }
        return null;
    }

    private static class TakeoffDataHandler extends DefaultHandler {
        private List<Takeoff> takeoffs = new ArrayList<>();
        private Takeoff currentTakeoff;
        private StringBuilder currentData = new StringBuilder();

        public List<Takeoff> getTakeoffs() {
            return takeoffs;
        }

        @Override
        public void startElement(String namespaceURI, String localName, String qName, Attributes atts) {
            if ("start".equals(qName)) {
                currentTakeoff = new Takeoff();
                long now = System.currentTimeMillis();
                currentTakeoff.setLastChecked(now);
                currentTakeoff.setLastUpdated(now);
            } else {
                currentData.setLength(0);
            }
        }

        @Override
        public void characters (char ch[], int start, int length) {
            currentData.append(ch, start, length);
        }

        @Override
        public void endElement (String uri, String localName, String qName) {
            switch (qName) {
                case "start":
                    takeoffs.add(currentTakeoff);
                    break;

                case "id":
                    currentTakeoff.setId(Long.parseLong(currentData.toString()));
                    break;

                case "name":
                    currentTakeoff.setName(convertHtmlUnicodeCodeToActualUnicode(currentData.toString()).trim());
                    break;

                case "description":
                    currentTakeoff.setDescription(convertHtmlUnicodeCodeToActualUnicode(currentData.toString()).trim());
                    break;

                case "lat":
                    currentTakeoff.setLatitude(Float.parseFloat(currentData.toString()));
                    break;

                case "lon":
                    currentTakeoff.setLongitude(Float.parseFloat(currentData.toString()));
                    break;

                case "wind":
                    currentTakeoff.setExits(Integer.parseInt(currentData.toString()));
                    break;

                case "country_id":
                    // ignored
                    break;

                case "region_id":
                    // ignored
                    break;

                case "subregion_id":
                    // ignored
                    break;

                case "altitude":
                    currentTakeoff.setAsl(Integer.parseInt(currentData.toString()));
                    break;

                case "altitudediff":
                    currentTakeoff.setHeight(Integer.parseInt(currentData.toString()));
                    break;

                case "createdtime":
                    // ignored
                    break;

                case "updatedtime":
                    // ignored
                    break;
            }
        }

        private String convertHtmlUnicodeCodeToActualUnicode(String text) {
            Matcher unicodeMatcher = UNICODE_PATTERN.matcher(text);
            while (unicodeMatcher.find()) {
                String replace = new String(Character.toChars(Integer.parseInt(unicodeMatcher.group(1))));
                text = text.replace(unicodeMatcher.group(), replace);
                unicodeMatcher = UNICODE_PATTERN.matcher(text);
            }
            return text;
        }
    }

    /* TODO: handle building up entire database from scratch: should probably just supply a prefetched takeoffs.xml
    public static void main(String... args) throws Exception {
        List<Takeoff> takeoffs = fetchUpdatedTakeoffs(999999); // 999999 days ago should fetch all takeoffs from flightlog
        if (takeoffs != null) {
            DataOutputStream outputStream = new DataOutputStream(new FileOutputStream("flywithme.dat"));
            outputStream.writeLong(System.currentTimeMillis());
            for (Takeoff takeoff : takeoffs) {
                outputStream.writeShort((short) takeoff.getId());
                outputStream.writeUTF(takeoff.getName());
                outputStream.writeUTF(takeoff.getDescription());
                outputStream.writeShort(takeoff.getAsl());
                outputStream.writeShort(takeoff.getHeight());
                outputStream.writeFloat(takeoff.getLatitude());
                outputStream.writeFloat(takeoff.getLongitude());
                outputStream.writeShort(takeoff.getExits());
            }
            outputStream.close();

            // test flywithme.dat by reading it (had some unexplainable issues where the file somehow got corrupted)
            DataInputStream inputStream = new DataInputStream(new FileInputStream("flywithme.dat"));
            try {
                long imported = inputStream.readLong();
                while (true) {
                    // loop breaks once we get an EOFException
                    int takeoffId = inputStream.readShort();
                    String name = inputStream.readUTF();
                    String description = inputStream.readUTF();
                    int asl = inputStream.readShort();
                    int height = inputStream.readShort();
                    float latitude = inputStream.readFloat();
                    float longitude = inputStream.readFloat();
                    int exits = inputStream.readShort();
                    System.out.println("[" + takeoffId + "] " + name + " (ASL: " + asl + ", Height: " + height + ") [" + latitude + "," + longitude + "] <" + exits + ">");
                }
            } catch (EOFException e) {
                // expected
                System.out.println("Database file tested OK, uploading to GAE...");
                ObjectifyService.register(Takeoff.class);
                PrivateKey privateKey = null;
                try {
                    privateKey = SecurityUtils.loadPrivateKeyFromKeyStore(SecurityUtils.getPkcs12KeyStore(), new FileInputStream("resources/FlyWithMe-24e9e26a499c.p12"), "notasecret", "privatekey", "notasecret");
                } catch (GeneralSecurityException | IOException e2) {
                    e.printStackTrace();
                }

                RemoteApiInstaller installer = new RemoteApiInstaller();
                installer.install(new RemoteApiOptions().server("5-dot-flywithme-160421.appspot.com", 443).useServiceAccountCredential("535669012847-compute@developer.gserviceaccount.com", privateKey));
                try {
                    DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
                    for (Takeoff takeoff : takeoffs) {
                        Entity entity = ofy().save().toEntity(takeoff);
                        System.out.println("Added takeoff to Datastore: " + ds.put(entity));
                    }
                } finally {
                    installer.uninstall();
                }
            }
        } else {
            System.out.println("Unable to fetch takeoffs from server...?");
        }
    }
    */
}
