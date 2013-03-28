import exent.httpserver;
import std.conv;
import std.datetime;
import std.file; // TODO: used for testing, remove
import std.net.curl;
import std.stdio;
import std.xml;

class WeatherHttpHandler : DynamicHttpHandler {
	this(string path) {
		super(regex(path));
	}

	override HttpResponse handle(HttpRequest request, Address remote) {
		return HttpResponse();
	}
}

struct Takeoff {
	int id;
	string name;
	string description;
	short altitude; // altitude above mean sea level
	short height; // height from start to landing
	float latitude;
	float longitude;
	byte startDirections; // N, NE, E, SE, S, SW, W and NW (hooray for bit juggling)
	int updated; // seconds since this takeoff was last updated (relative to epoch)
	WeatherData[] weatherData;
}

struct WeatherData {
	int timestamp; // what time this forecast is valid for in seconds since epoch
	float temperature;
	float windDirection;
	float windSpeed;
	float humidity;
	float pressure;
}

void main() {
}

void fetchWeatherData(ref Takeoff takeoff) {
	//auto document = cast(string) get("http://api.met.no/weatherapi/locationforecast/1.8/?lat=60.10;lon=9.58");
	auto document = readText("weather.xml");
	auto xml = new DocumentParser(document);
	xml.onStartTag["time"] = (ElementParser xml) {
		WeatherData weatherData;
		weatherData.timestamp = cast(int) (SysTime.fromISOExtString(xml.tag.attr["to"]).stdTime() / 10000000);
		xml.onStartTag["location"] = (ElementParser xml) {
			bool found;
			xml.onStartTag["temperature"] = (ElementParser xml) { weatherData.temperature = to!float(xml.tag.attr["value"]); };
			xml.onStartTag["windDirection"] = (ElementParser xml) { weatherData.windDirection = to!float(xml.tag.attr["deg"]); };
			xml.onStartTag["windSpeed"] = (ElementParser xml) { weatherData.windSpeed = to!float(xml.tag.attr["mps"]); };
			xml.onStartTag["humidity"] = (ElementParser xml) { weatherData.humidity = to!float(xml.tag.attr["value"]); };
			xml.onStartTag["pressure"] = (ElementParser xml) { weatherData.pressure = to!float(xml.tag.attr["value"]); found = true; };
			xml.parse();
			if (found) {
				/* data from met.no is a bit peculiar, hence the "found" variable. take a look to understand */
				takeoff.altitude = to!short(xml.tag.attr["altitude"]);
				takeoff.weatherData ~= weatherData;
			}
		};
		xml.parse();
	};
	xml.parse();
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
