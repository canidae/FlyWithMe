import exent.httpserver;
import std.array;
import std.bitmanip;
import std.conv;
import std.datetime;
import std.math: isNaN;
import std.net.curl;
import std.stdio;
import std.xml;

class WeatherHttpHandler : DynamicHttpHandler {
	this(string path) {
		super(regex(path));
	}

	override HttpResponse handle(HttpRequest request, Address remote) {
		/* x-fwm-forecast-request protocol:
		   1 byte: version of protocol (ubyte)
		   1 byte: amount of locations (ubyte)
		   <location loop>
		      4 bytes: latitude (float)
		      4 bytes: longitude (float)
		   <end location loop>
		 */
		if ("content-type" !in request.headers || request.headers["content-type"] != "application/x-fwm-forecast-request" || request.content.length < 2)
			return invalidRequest();
		ubyte[] input = request.content;
		ubyte reqVersion = input.read!ubyte();
		ubyte locationAmount = input.read!ubyte();
		if (reqVersion != 1 || input.length != locationAmount * 4 * 2)
			return invalidRequest();
		Location[] locations;
		for (int a = 0; a < locationAmount; ++a)
			locations ~= Location(input.read!float(), input.read!float());

		/* x-fwm-forecast-response protocol (note that forecasts are returned in the same order as they were requested, the location itself is not returned. TODO: we must return location, in the form of takeoff id, in case a takeoff "disappears" from server, but still exist on mobile devices):
		   1 byte: version of protocol (ubyte)
		   1 byte: amount of locations (ubyte)
		   <location loop>
		      2 bytes: forecast altitude (short)
		      1 byte: amount of forecasts for location (ubyte)
		      <forecast loop>
		         4 bytes: forecast timestamp, seconds since UTC epoch (int)
		         2 bytes: temperature (short, real value is multiplied with 10, -666 if not known)
		         2 bytes: wind direction (short, real value is multiplied with 10, -1 if not known)
		         2 bytes: wind speed (short, real value is multiplied with 10, -1 if not known)
		         2 bytes: humidity (short, real value is multiplied with 10, -1 if not known)
		         2 bytes: pressure (short, real value is multiplied with 10, -1 if not known)
		      <end forecast loop>
		   <end location loop>
		 */
		HttpResponse response;
		response.setHeader("content-type", "application/x-fwm-forecast-response");
		auto buffer = appender!(ubyte[])();
		buffer.append!ubyte(1);
		buffer.append!ubyte(locationAmount);
		foreach (location; locations) {
			Forecast[] forecasts = getForecasts(location);
			buffer.append!short(forecasts[0].altitude);
			buffer.append!ubyte(to!ubyte(forecasts.length));
			foreach (forecast; forecasts) {
				buffer.append!int(to!int(stdTimeToUnixTime(forecast.timestamp.stdTime)));
				buffer.append!short(to!short(isNaN(forecast.temperature) ? -666 : forecast.temperature * 10));
				buffer.append!short(to!short(isNaN(forecast.windDirection) ? -1 : forecast.windDirection * 10));
				buffer.append!short(to!short(isNaN(forecast.windSpeed) ? -1 : forecast.windSpeed * 10));
				buffer.append!short(to!short(isNaN(forecast.humidity) ? -1 : forecast.humidity * 10));
				buffer.append!short(to!short(isNaN(forecast.pressure) ? -1 : forecast.pressure * 10));
			}
		}
		response.content = buffer.data;
		return response;
	}

	HttpResponse invalidRequest() {
		HttpResponse response;
		response.status = 400;
		response.content = cast(ubyte[]) "This is not how you retrieve data from this service!";
		return response;
	}
}

struct Location {
	float latitude;
	float longitude;
}

struct Forecast {
	SysTime timestamp;
	short altitude; // TODO: move to future Takeoff struct (even though it's returned in forecast, but altitude doesn't change in forecasts and we want to know the exact altitude of the takeoff)
	float temperature;
	float windDirection;
	float windSpeed;
	float humidity;
	float pressure;
}

/* variables */
// TODO: this is flawed: an attacker can simply request data for loads of unique locations and fill up our memory. need to limit this (preferably by getting takeoffs from flightlog and taking takeoff id as a parameter instead of location)
Forecast[][Location] forecastLocations;

/* main */
void main() {
	foreach (forecast; getForecasts(Location(60, 10)))
		writefln("%s: %s° C | Wind: %s°, %s m/s | Humidity: %s | Pressure: %s", forecast.timestamp.toISOExtString(), forecast.temperature, forecast.windDirection, forecast.windSpeed, forecast.humidity, forecast.pressure);

	addDynamicHandler(new WeatherHttpHandler("weather"));
	startServer(ServerSettings());
	core.thread.Thread.sleep(dur!("weeks")(100)); // sleep for a couple years
}

/* methods */
Forecast[] getForecasts(Location location) {
	if (location in forecastLocations && forecastLocations[location].length > 0 && Clock.currTime() < forecastLocations[location][0].timestamp) {
		/* cached data is recent enough */
		/* TODO: not good enough. 1) data is not updated every hour and 2) see TODO below about <model>-data */
		writefln("Cached data for location is recent enough, time left to next update: %s", forecastLocations[location][0].timestamp - Clock.currTime());
		return forecastLocations[location];
	}
	//auto document = to!string(get("http://api.met.no/weatherapi/locationforecast/1.8/?lat=" ~ to!string(location.latitude) ~ ";lon=" ~ to!string(location.longitude)));
	auto document = std.file.readText("weather.xml"); // TODO: remove, used while coding to not bother met.no too much (and make application faster)
	/* TODO: parse this information to know how long to cache data
	   <meta>
	      <model name="YR" termin="2013-03-28T12:00:00Z" runended="2013-03-28T18:56:53Z" nextrun="2013-03-29T06:00:00Z" from="2013-03-29T01:00:00Z" to="2013-03-31T06:00:00Z"/>
	      <model name="EPS" termin="2013-03-28T12:00:00Z" runended="2013-03-28T21:25:45Z" nextrun="2013-03-29T09:45:00Z" from="2013-03-31T12:00:00Z" to="2013-04-07T06:00:00Z"/>
	      <model name="EC.GEO.0.25" termin="2013-03-28T12:00:00Z" runended="2013-03-28T19:02:33Z" nextrun="2013-03-29T08:00:00Z" from="2013-03-29T03:00:00Z" to="2013-04-07T12:00:00Z"/>
	   </meta>
	 */
	Forecast[] forecasts;
	auto xml = new DocumentParser(document);
	xml.onStartTag["time"] = (ElementParser xml) {
		Forecast forecast;
		forecast.timestamp = SysTime.fromISOExtString(xml.tag.attr["to"]);
		xml.onStartTag["location"] = (ElementParser xml) {
			forecast.altitude = to!short(xml.tag.attr["altitude"]);
			bool found;
			xml.onStartTag["temperature"] = (ElementParser xml) { forecast.temperature = to!float(xml.tag.attr["value"]); };
			xml.onStartTag["windDirection"] = (ElementParser xml) { forecast.windDirection = to!float(xml.tag.attr["deg"]); };
			xml.onStartTag["windSpeed"] = (ElementParser xml) { forecast.windSpeed = to!float(xml.tag.attr["mps"]); };
			xml.onStartTag["humidity"] = (ElementParser xml) { forecast.humidity = to!float(xml.tag.attr["value"]); };
			xml.onStartTag["pressure"] = (ElementParser xml) { forecast.pressure = to!float(xml.tag.attr["value"]); found = true; };
			xml.parse();
			if (found) {
				/* data from met.no is a bit peculiar, hence the "found" variable. take a look to understand */
				forecasts ~= forecast;
			}
		};
		xml.parse();
	};
	xml.parse();
	forecastLocations[location] = forecasts;
	return forecasts;
	/*
	   <time datatype="forecast" from="2013-03-27T11:00:00Z" to="2013-03-27T11:00:00Z">
	   <location altitude="70" latitude="60.1000" longitude="9.5800">
	   <temperature id="TTT" unit="celcius" value="-4.2"/>
	   <windDirection id="dd" deg="165.1" name="S"/>
	   <windSpeed id="ff" mps="1.0" beaufort="1" name="Flau vind"/>
	   <humidity value="88.2" unit="percent"/>
	   <pressure id="pr" unit="hPa" value="1022.4"/>
	   <cloudiness id="NN" percent="0.0"/>
	   <fog id="FOG" percent="0.0"/>
	   <lowClouds id="LOW" percent="0.0"/>
	   <mediumClouds id="MEDIUM" percent="0.0"/>
	   <highClouds id="HIGH" percent="0.0"/>
	   </location>
	   </time>
	 */
}
